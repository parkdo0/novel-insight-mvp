<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { readEpisode, addComment, getComments, type Episode, type Comment } from '../api/novelApi'
import { startReading, updateScroll } from '../api/behaviorApi'

//
// # 뷰어 페이지 (개선 버전)
//
// ## 새로 추가된 기능
//
// 1. 📊 **읽기 로그 자동 기록**
//    - 읽기 시작 시 로그 생성
//    - 5초마다 스크롤 깊이 업데이트
//    - 페이지 이탈 시 최종 로그 전송
//
// 2. 📈 **진도율 표시**
//    - 상단에 읽기 진행 바 표시
//    - 실시간 업데이트
//
// 3. ⏱️ **읽기 시간 추적**
//    - 읽은 시간 표시
//    - 예상 남은 시간 계산
//
// 4. 🎨 **UI 개선**
//    - Tailwind CSS로 현대적인 디자인
//    - 반응형 레이아웃
//    - 더 나은 가독성
//

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// State
const episode = ref<Episode | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')

// 읽기 추적 State
const readingStartTime = ref<Date | null>(null)
const currentScrollDepth = ref(0)
const maxScrollDepth = ref(0)
const readingTimeSeconds = ref(0)

// 타이머 ID
let scrollUpdateInterval: number | null = null
let readingTimeInterval: number | null = null

// Computed
const readingTimeFormatted = computed(() => {
  const minutes = Math.floor(readingTimeSeconds.value / 60)
  const seconds = readingTimeSeconds.value % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
})

const progressPercentage = computed(() => {
  return Math.min(100, Math.max(0, currentScrollDepth.value))
})

//
// 스크롤 깊이 계산
//
// 페이지 전체 높이 대비 현재 스크롤 위치를 백분율로 계산합니다.
//
const calculateScrollDepth = (): number => {
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  const scrollTop = window.scrollY || document.documentElement.scrollTop

  if (documentHeight <= windowHeight) {
    return 100 // 스크롤이 필요 없는 경우 100%
  }

  const scrollable = documentHeight - windowHeight
  const scrolled = (scrollTop / scrollable) * 100

  return Math.min(100, Math.max(0, scrolled))
}

//
// 스크롤 이벤트 핸들러
//
// 실시간으로 스크롤 깊이를 계산하고 업데이트합니다.
//
const handleScroll = () => {
  const depth = calculateScrollDepth()
  currentScrollDepth.value = depth

  if (depth > maxScrollDepth.value) {
    maxScrollDepth.value = depth
  }
}

//
// 뷰어 로드
//
const loadViewer = async () => {
  try {
    const epId = Number(route.params.episodeId)

    // 에피소드 데이터 로드
    episode.value = await readEpisode(epId)
    comments.value = await getComments(epId)

    // 읽기 로그 시작
    await startReadingLog()

  } catch (e) {
    alert("볼 수 없는 회차입니다.")
    router.back()
  }
}

//
// 읽기 로그 시작
//
// ## 동작
//
// 1. 백엔드에 읽기 시작 로그 전송
// 2. 읽기 시작 시간 기록
// 3. 스크롤 추적 시작 (5초마다 업데이트)
// 4. 읽기 시간 카운터 시작 (1초마다)
//
const startReadingLog = async () => {
  if (!episode.value) return

  try {
    // 1. 백엔드에 읽기 시작 로그 전송
    await startReading(episode.value.id, episode.value.novelId)

    // 2. 읽기 시작 시간 기록
    readingStartTime.value = new Date()

    // 3. 스크롤 추적 시작 (5초마다)
    scrollUpdateInterval = window.setInterval(async () => {
      try {
        await updateScroll(maxScrollDepth.value)
      } catch (error) {
        console.error('스크롤 업데이트 실패:', error)
      }
    }, 5000)

    // 4. 읽기 시간 카운터 시작 (1초마다)
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
// ## 호출 시점
//
// - 페이지 이탈 시 (beforeunload)
// - 컴포넌트 언마운트 시
//
// ## Beacon API 사용
//
// - 일반 fetch/XHR은 페이지 unload 중 취소될 수 있음
// - Beacon API는 브라우저가 전송을 보장
//
const endReadingLog = () => {
  if (maxScrollDepth.value === 0) return

  try {
    // Beacon API로 전송 (페이지 unload 중에도 전송 보장)
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
// 뒤로 가기
//
const goBack = () => {
  const novelId = route.query.novelId
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

  // 스크롤 이벤트 리스너 등록
  window.addEventListener('scroll', handleScroll)

  // 페이지 이탈 시 로그 전송
  window.addEventListener('beforeunload', endReadingLog)
})

onBeforeUnmount(() => {
  // 타이머 정리
  if (scrollUpdateInterval) {
    clearInterval(scrollUpdateInterval)
  }
  if (readingTimeInterval) {
    clearInterval(readingTimeInterval)
  }

  // 이벤트 리스너 제거
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('beforeunload', endReadingLog)

  // 최종 로그 전송
  endReadingLog()
})
</script>

<template>
  <div v-if="episode" class="min-h-screen bg-gray-50">
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
          <div class="flex items-center gap-2">
            <span>📖</span>
            <span>{{ progressPercentage.toFixed(0) }}%</span>
          </div>
          <div class="flex items-center gap-2">
            <span>⏱️</span>
            <span>{{ readingTimeFormatted }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 에피소드 제목 (상단 여백) -->
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

    <!-- 에피소드 본문 -->
    <div class="max-w-4xl mx-auto px-6 py-12 bg-white min-h-screen">
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

    <!-- 댓글 섹션 -->
    <div class="bg-gray-100 border-t">
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
            <div class="flex items-center gap-2 mb-2">
              <span class="font-semibold text-gray-900">{{ c.writerName }}</span>
              <span class="text-xs text-gray-400">방금 전</span>
            </div>
            <p class="text-gray-700">{{ c.content }}</p>
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
/* Tailwind CSS를 사용하므로 별도 스타일 최소화 */

/* 본문 가독성 개선 */
.prose p {
  font-family: 'Noto Sans KR', 'KoPub Batang', serif;
  font-size: 1.125rem;
  line-height: 1.8;
}
</style>
