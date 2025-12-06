<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import {
  readEpisode,
  addComment,
  getComments,
  getEpisodeNav,
  toggleLike,
  recordSectionReplay,
  type Episode,
  type Comment,
  type EpisodeWithNav
} from '../api/novelApi'
import { startReading, updateScroll } from '../api/behaviorApi'

//
// # 뷰어 페이지 (V2 - 확장 버전)
//
// ## 새로 추가된 기능
//
// 1. 📖 **스크롤/페이지 모드 선택**
//    - 스크롤 모드: 일반 스크롤로 읽기
//    - 페이지 모드: 페이지 넘김으로 읽기
//
// 2. 📊 **명장면 분석 로그**
//    - 사용자가 다시 본 구간 추적
//    - 재조회 횟수 집계
//
// 3. ⏮️⏭️ **이전화/다음화 네비게이션**
//    - 하단에 이전화/다음화 버튼
//    - 제목 미리보기
//
// 4. 👍👎 **댓글 좋아요/싫어요**
//    - 댓글에 좋아요/싫어요 버튼 추가
//

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// State
const episode = ref<EpisodeWithNav | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')
const loading = ref(true)

// 읽기 추적 State
const readingStartTime = ref<Date | null>(null)
const currentScrollDepth = ref(0)
const maxScrollDepth = ref(0)
const readingTimeSeconds = ref(0)
const previousScrollDepth = ref(0) // 이전 스크롤 위치 (명장면 분석용)

// 뷰어 모드 State
const viewerMode = ref<'SCROLL' | 'PAGE'>('SCROLL')
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = ref(1500) // 페이지당 글자 수
const pagesContent = ref<string[]>([])

// 세션 ID (명장면 분석용)
const sessionId = ref<string>('')

// 타이머 ID
let scrollUpdateInterval: number | null = null
let readingTimeInterval: number | null = null
let replayCheckInterval: number | null = null

// Computed
const readingTimeFormatted = computed(() => {
  const minutes = Math.floor(readingTimeSeconds.value / 60)
  const seconds = readingTimeSeconds.value % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
})

const progressPercentage = computed(() => {
  if (viewerMode.value === 'PAGE') {
    return (currentPage.value / totalPages.value) * 100
  }
  return Math.min(100, Math.max(0, currentScrollDepth.value))
})

// 디바이스 타입 감지
const deviceType = computed<'MOBILE' | 'DESKTOP' | 'TABLET'>(() => {
  const width = window.innerWidth
  if (width < 768) return 'MOBILE'
  if (width < 1024) return 'TABLET'
  return 'DESKTOP'
})

// 현재 페이지 컨텐츠
const currentPageContent = computed(() => {
  if (viewerMode.value === 'PAGE' && pagesContent.value.length > 0) {
    return pagesContent.value[currentPage.value - 1] || ''
  }
  return ''
})

//
// 세션 ID 생성
//
const generateSessionId = (): string => {
  return 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9)
}

//
// 스크롤 깊이 계산
//
const calculateScrollDepth = (): number => {
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  const scrollTop = window.scrollY || document.documentElement.scrollTop

  if (documentHeight <= windowHeight) {
    return 100
  }

  const scrollable = documentHeight - windowHeight
  const scrolled = (scrollTop / scrollable) * 100

  return Math.min(100, Math.max(0, scrolled))
}

//
// 스크롤 이벤트 핸들러 (명장면 분석 포함)
//
const handleScroll = () => {
  if (viewerMode.value !== 'SCROLL') return

  const depth = calculateScrollDepth()
  currentScrollDepth.value = depth

  // 명장면 분석: 이전 위치보다 10% 이상 위로 스크롤한 경우 = 재조회
  if (previousScrollDepth.value - depth > 10 && episode.value) {
    recordReplay(depth)
  }

  if (depth > maxScrollDepth.value) {
    maxScrollDepth.value = depth
  }

  previousScrollDepth.value = depth
}

//
// 재조회 로그 기록 (명장면 분석)
//
const recordReplay = async (sectionPercentage: number) => {
  if (!episode.value || !sessionId.value) return

  try {
    await recordSectionReplay({
      userId: authStore.userId || undefined,
      episodeId: episode.value.id,
      sectionPercentage: sectionPercentage,
      sectionEndPercentage: Math.min(sectionPercentage + 10, 100),
      sessionId: sessionId.value,
      viewerMode: viewerMode.value,
      deviceType: deviceType.value
    })
    console.log('✅ 재조회 로그 기록:', sectionPercentage.toFixed(1) + '%')
  } catch (error) {
    console.error('재조회 로그 기록 실패:', error)
  }
}

//
// 페이지 모드: 페이지 분할
//
const splitIntoPages = (content: string) => {
  const lines = content.split('\n')
  const pages: string[] = []
  let currentPageContent = ''

  for (const line of lines) {
    if ((currentPageContent + line).length > pageSize.value) {
      pages.push(currentPageContent.trim())
      currentPageContent = line + '\n'
    } else {
      currentPageContent += line + '\n'
    }
  }

  if (currentPageContent.trim()) {
    pages.push(currentPageContent.trim())
  }

  pagesContent.value = pages
  totalPages.value = pages.length
}

//
// 페이지 이동
//
const goToPage = (page: number) => {
  if (page < 1 || page > totalPages.value) return

  const prevPage = currentPage.value

  // 명장면 분석: 이전 페이지로 돌아가는 경우 = 재조회
  if (page < prevPage && episode.value) {
    const sectionPercentage = ((page - 1) / totalPages.value) * 100
    recordReplay(sectionPercentage)
  }

  currentPage.value = page

  // 최대 진도율 업데이트
  const pageProgress = (page / totalPages.value) * 100
  if (pageProgress > maxScrollDepth.value) {
    maxScrollDepth.value = pageProgress
  }

  // 페이지 상단으로 스크롤
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

//
// 뷰어 모드 변경
//
const toggleViewerMode = () => {
  viewerMode.value = viewerMode.value === 'SCROLL' ? 'PAGE' : 'SCROLL'

  if (viewerMode.value === 'PAGE' && episode.value) {
    splitIntoPages(episode.value.content)
    // 현재 스크롤 위치 기반으로 페이지 계산
    const estimatedPage = Math.ceil((currentScrollDepth.value / 100) * totalPages.value) || 1
    currentPage.value = estimatedPage
  }

  // 로컬 스토리지에 모드 저장
  localStorage.setItem('viewerMode', viewerMode.value)
}

//
// 뷰어 로드
//
const loadViewer = async () => {
  loading.value = true
  sessionId.value = generateSessionId()

  // 저장된 뷰어 모드 복원
  const savedMode = localStorage.getItem('viewerMode')
  if (savedMode === 'SCROLL' || savedMode === 'PAGE') {
    viewerMode.value = savedMode
  }

  try {
    const epId = Number(route.params.episodeId)
    const novelId = Number(route.query.novelId)

    // 에피소드 데이터 로드
    const [episodeData, navInfo, commentsData] = await Promise.all([
      readEpisode(epId),
      novelId ? getEpisodeNav(novelId, epId) : Promise.resolve(null),
      getComments(epId)
    ])

    episode.value = {
      ...episodeData,
      ...(navInfo || {})
    }
    comments.value = commentsData

    // 페이지 모드일 경우 페이지 분할
    if (viewerMode.value === 'PAGE' && episode.value) {
      splitIntoPages(episode.value.content)
    }

    // 읽기 로그 시작
    await startReadingLog()

  } catch (e: any) {
    if (e.response?.status === 403) {
      if (!authStore.isAuthenticated) {
        if (confirm("로그인이 필요합니다. 로그인 페이지로 이동하시겠습니까?")) {
          router.push({ path: '/login', query: { redirect: route.fullPath } })
        } else {
          router.back()
        }
      } else if (confirm("포인트가 부족합니다. 포인트를 구매하시겠습니까?")) {
        router.push('/mypage')
      } else {
        router.back()
      }
    } else {
      alert("볼 수 없는 회차입니다.")
      router.back()
    }
  } finally {
    loading.value = false
  }
}

//
// 읽기 로그 시작
//
const startReadingLog = async () => {
  if (!episode.value) return

  try {
    await startReading(episode.value.id, episode.value.novelId)
    readingStartTime.value = new Date()

    // 스크롤 추적 (5초마다)
    scrollUpdateInterval = window.setInterval(async () => {
      try {
        await updateScroll(maxScrollDepth.value)
      } catch (error) {
        console.error('스크롤 업데이트 실패:', error)
      }
    }, 5000)

    // 읽기 시간 카운터 (1초마다)
    readingTimeInterval = window.setInterval(() => {
      readingTimeSeconds.value++
    }, 1000)

    console.log('✅ 읽기 로그 추적 시작')
  } catch (error) {
    console.error('읽기 로그 시작 실패:', error)
  }
}

//
// 읽기 로그 종료
//
const endReadingLog = () => {
  if (maxScrollDepth.value === 0) return

  try {
    const data = JSON.stringify({ scrollDepth: maxScrollDepth.value })
    const blob = new Blob([data], { type: 'application/json' })
    navigator.sendBeacon('/api/behavior/end', blob)
    console.log('✅ 읽기 로그 전송:', maxScrollDepth.value)
  } catch (error) {
    console.error('읽기 로그 종료 실패:', error)
  }
}

//
// 댓글 추가
//
const handleAddComment = async () => {
  if (!authStore.isAuthenticated) return alert("로그인 필요")
  if (!newComment.value || !episode.value) return

  try {
    await addComment(episode.value.id, newComment.value)
    newComment.value = ''
    comments.value = await getComments(episode.value.id)
  } catch (e) {
    alert("댓글 등록 실패")
  }
}

//
// 댓글 좋아요/싫어요
//
const handleToggleLike = async (commentId: number, type: 'LIKE' | 'DISLIKE') => {
  if (!authStore.isAuthenticated) return alert("로그인이 필요합니다")

  try {
    await toggleLike(commentId, type)
    // 댓글 목록 새로고침
    if (episode.value) {
      comments.value = await getComments(episode.value.id)
    }
  } catch (e: any) {
    if (e.response?.status === 400) {
      alert("이미 반응한 댓글입니다")
    } else {
      alert("오류가 발생했습니다")
    }
  }
}

//
// 이전화/다음화 이동
//
const goToEpisode = (episodeId: number) => {
  const novelId = episode.value?.novelId || route.query.novelId
  router.push(`/viewer/${episodeId}?novelId=${novelId}`)
}

//
// 뒤로 가기
//
const goBack = () => {
  const novelId = route.query.novelId || episode.value?.novelId
  if (novelId) {
    router.push(`/novels/${novelId}/episodes`)
  } else {
    router.back()
  }
}

//
// 라이프사이클 훅
//
onMounted(() => {
  loadViewer()
  window.addEventListener('scroll', handleScroll)
  window.addEventListener('beforeunload', endReadingLog)
})

onBeforeUnmount(() => {
  if (scrollUpdateInterval) clearInterval(scrollUpdateInterval)
  if (readingTimeInterval) clearInterval(readingTimeInterval)
  if (replayCheckInterval) clearInterval(replayCheckInterval)

  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('beforeunload', endReadingLog)

  endReadingLog()
})

// 에피소드 변경 감지 (이전화/다음화 이동 시)
watch(() => route.params.episodeId, () => {
  if (route.params.episodeId) {
    // 상태 초기화
    currentScrollDepth.value = 0
    maxScrollDepth.value = 0
    previousScrollDepth.value = 0
    readingTimeSeconds.value = 0
    currentPage.value = 1

    loadViewer()
  }
})
</script>

<template>
  <div v-if="!loading && episode" class="min-h-screen bg-gray-50">
    <!-- 진도율 표시 바 (고정) -->
    <div class="fixed top-0 left-0 right-0 z-50 bg-white shadow-sm">
      <div
        class="h-1 bg-gradient-to-r from-purple-500 to-indigo-600 transition-all duration-300"
        :style="{ width: `${progressPercentage}%` }"
      ></div>
      <div class="px-4 py-3 flex justify-between items-center">
        <button
          @click="goBack"
          class="px-4 py-2 text-gray-600 hover:text-gray-900 font-medium rounded-lg hover:bg-gray-100 transition-colors"
        >
          ← 나가기
        </button>
        <div class="flex items-center gap-4 text-sm text-gray-600">
          <!-- 뷰어 모드 토글 -->
          <button
            @click="toggleViewerMode"
            class="px-3 py-1 bg-gray-100 rounded-full text-xs font-medium hover:bg-gray-200 transition-colors"
          >
            {{ viewerMode === 'SCROLL' ? '📜 스크롤' : '📄 페이지' }}
          </button>
          <div class="flex items-center gap-2">
            <span>📖</span>
            <span v-if="viewerMode === 'PAGE'">{{ currentPage }}/{{ totalPages }}페이지</span>
            <span v-else>{{ progressPercentage.toFixed(0) }}%</span>
          </div>
          <div class="flex items-center gap-2">
            <span>⏱️</span>
            <span>{{ readingTimeFormatted }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 에피소드 제목 -->
    <div class="pt-20 pb-8 bg-white border-b">
      <div class="max-w-4xl mx-auto px-6">
        <h1 class="text-3xl font-bold text-gray-900 mb-2">
          {{ episode.title }}
        </h1>
        <div class="flex items-center gap-4 text-sm text-gray-500">
          <span>👁️ {{ episode.viewCount?.toLocaleString() || 0 }} 조회</span>
          <span>💬 {{ comments.length }} 댓글</span>
        </div>
      </div>
    </div>

    <!-- 에피소드 본문 - 스크롤 모드 -->
    <div v-if="viewerMode === 'SCROLL'" class="max-w-4xl mx-auto px-6 py-12 bg-white min-h-screen">
      <div class="prose prose-lg max-w-none">
        <p
          v-for="(line, idx) in episode.content.split('\n')"
          :key="idx"
          class="mb-4 text-gray-800 leading-relaxed"
          :class="{ 'text-center font-semibold': line.startsWith('##') }"
        >
          {{ line }}
        </p>
      </div>
    </div>

    <!-- 에피소드 본문 - 페이지 모드 -->
    <div v-else class="max-w-4xl mx-auto px-6 py-12 bg-white min-h-[70vh]">
      <div class="prose prose-lg max-w-none">
        <p
          v-for="(line, idx) in currentPageContent.split('\n')"
          :key="idx"
          class="mb-4 text-gray-800 leading-relaxed"
          :class="{ 'text-center font-semibold': line.startsWith('##') }"
        >
          {{ line }}
        </p>
      </div>

      <!-- 페이지 네비게이션 -->
      <div class="flex justify-center items-center gap-4 mt-8 pb-8">
        <button
          @click="goToPage(currentPage - 1)"
          :disabled="currentPage <= 1"
          class="px-6 py-3 bg-gray-200 text-gray-700 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-300 transition-colors font-medium"
        >
          ← 이전 페이지
        </button>
        <span class="text-gray-600 font-medium">{{ currentPage }} / {{ totalPages }}</span>
        <button
          @click="goToPage(currentPage + 1)"
          :disabled="currentPage >= totalPages"
          class="px-6 py-3 bg-purple-600 text-white rounded-lg disabled:opacity-50 disabled:cursor-not-allowed hover:bg-purple-700 transition-colors font-medium"
        >
          다음 페이지 →
        </button>
      </div>
    </div>

    <!-- 이전화/다음화 네비게이션 -->
    <div class="bg-gray-100 border-t border-b">
      <div class="max-w-4xl mx-auto px-6 py-6">
        <div class="flex justify-between items-stretch gap-4">
          <!-- 이전화 -->
          <div class="flex-1">
            <button
              v-if="episode.prevEpisode"
              @click="goToEpisode(episode.prevEpisode.id)"
              class="w-full h-full p-4 bg-white rounded-lg shadow-sm hover:shadow-md transition-shadow text-left"
            >
              <div class="text-xs text-gray-400 mb-1">← 이전화</div>
              <div class="text-sm font-medium text-gray-700 line-clamp-2">
                {{ episode.prevEpisode.title }}
              </div>
            </button>
            <div v-else class="w-full h-full p-4 bg-gray-50 rounded-lg text-center text-gray-400 text-sm">
              첫 번째 회차입니다
            </div>
          </div>

          <!-- 다음화 -->
          <div class="flex-1">
            <button
              v-if="episode.nextEpisode"
              @click="goToEpisode(episode.nextEpisode.id)"
              class="w-full h-full p-4 bg-purple-600 text-white rounded-lg shadow-sm hover:bg-purple-700 transition-colors text-right"
            >
              <div class="text-xs text-purple-200 mb-1">다음화 →</div>
              <div class="text-sm font-medium line-clamp-2">
                {{ episode.nextEpisode.title }}
              </div>
            </button>
            <div v-else class="w-full h-full p-4 bg-gray-50 rounded-lg text-center text-gray-400 text-sm">
              마지막 회차입니다
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 댓글 섹션 -->
    <div class="bg-gray-100">
      <div class="max-w-4xl mx-auto px-6 py-8">
        <h2 class="text-2xl font-bold mb-6 text-gray-900">
          💬 댓글 ({{ comments.length }})
        </h2>

        <!-- 댓글 입력 -->
        <div class="mb-6 flex gap-3">
          <input
            v-model="newComment"
            placeholder="댓글을 남겨주세요"
            class="flex-1 px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            @keyup.enter="handleAddComment"
          />
          <button
            @click="handleAddComment"
            class="px-6 py-3 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors font-medium"
          >
            등록
          </button>
        </div>

        <!-- 댓글 목록 -->
        <div class="space-y-4">
          <div
            v-for="c in comments"
            :key="c.id"
            class="bg-white p-4 rounded-lg shadow-sm"
          >
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <span class="font-semibold text-gray-900">{{ c.writerName }}</span>
                <span class="text-xs text-gray-400">방금 전</span>
              </div>
            </div>
            <p class="text-gray-700 mb-3">{{ c.content }}</p>

            <!-- 좋아요/싫어요 버튼 -->
            <div class="flex items-center gap-4 text-sm">
              <button
                @click="handleToggleLike(c.id, 'LIKE')"
                class="flex items-center gap-1 text-gray-500 hover:text-blue-600 transition-colors"
              >
                <span>👍</span>
                <span>{{ c.likeCount || 0 }}</span>
              </button>
              <button
                @click="handleToggleLike(c.id, 'DISLIKE')"
                class="flex items-center gap-1 text-gray-500 hover:text-red-600 transition-colors"
              >
                <span>👎</span>
                <span>{{ c.dislikeCount || 0 }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 로딩 -->
  <div v-else class="min-h-screen flex items-center justify-center">
    <div class="text-center">
      <div class="animate-spin rounded-full h-16 w-16 border-t-4 border-purple-600 mx-auto mb-4"></div>
      <p class="text-gray-600">로딩 중...</p>
    </div>
  </div>
</template>

<style scoped>
.prose p {
  font-family: 'Noto Sans KR', 'KoPub Batang', serif;
  font-size: 1.125rem;
  line-height: 1.8;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
