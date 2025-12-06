<!--
  ========================================
  NovelDetailLayout - 소설 상세 페이지 레이아웃 (Tailwind CSS 디자인 개선)
  ========================================

  [디자인 개선 사항]
  - 현대적인 헤더 섹션 (그라데이션 배경, 메타 정보 카드)
  - 깔끔한 탭 네비게이션 (언더라인 효과)
  - 로딩 스켈레톤 UI
  - 반응형 레이아웃
  - 아이콘 및 통계 시각화
-->
<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getNovelDetail, type Novel } from '../api/novelApi'

const route = useRoute()
const router = useRouter()
const novel = ref<Novel | null>(null)
const loading = ref(true)

const loadData = async () => {
  loading.value = true
  try {
    const novelId = Number(route.params.id)
    novel.value = await getNovelDetail(novelId)
  } catch (e) {
    alert("소설 정보 로딩 실패")
    router.push('/')
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())

// 라우트 변경 시 데이터 갱신
watch(() => route.params.id, () => {
  loadData()
})

/**
 * 소설 ID 기반 그라데이션 색상
 */
const getGradient = computed(() => {
  if (!novel.value) return 'from-purple-500 to-pink-500'
  const gradients = [
    'from-purple-500 to-pink-500',
    'from-blue-500 to-cyan-500',
    'from-green-500 to-emerald-500',
    'from-orange-500 to-red-500',
    'from-indigo-500 to-purple-500',
  ]
  return gradients[novel.value.id % gradients.length]
})

/**
 * 조회수 포맷팅
 */
const formatViewCount = (count: number) => {
  if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
  if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
  return count.toString()
}
</script>

<template>
  <!-- Loading State -->
  <div v-if="loading" class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Back button skeleton -->
      <div class="mb-6">
        <div class="h-10 w-32 bg-gray-200 rounded-lg animate-pulse"></div>
      </div>

      <!-- Header skeleton -->
      <div class="bg-white rounded-2xl shadow-lg overflow-hidden animate-pulse">
        <div class="h-48 bg-gradient-to-br from-gray-200 to-gray-300"></div>
        <div class="p-8 space-y-4">
          <div class="h-8 bg-gray-200 rounded w-2/3"></div>
          <div class="h-5 bg-gray-200 rounded w-1/3"></div>
          <div class="flex gap-4">
            <div class="h-20 flex-1 bg-gray-200 rounded-lg"></div>
            <div class="h-20 flex-1 bg-gray-200 rounded-lg"></div>
            <div class="h-20 flex-1 bg-gray-200 rounded-lg"></div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- Main Content -->
  <div v-else-if="novel" class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Back Button -->
      <div class="mb-6">
        <button
          @click="router.push('/')"
          class="flex items-center gap-2 px-4 py-2 bg-white text-gray-700 rounded-lg
                 hover:bg-gray-50 border border-gray-200 transition-colors shadow-sm"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          목록으로
        </button>
      </div>

      <!-- Novel Header Card -->
      <div class="bg-white rounded-2xl shadow-xl overflow-hidden mb-6">
        <!-- Cover Banner -->
        <div :class="`h-48 bg-gradient-to-br ${getGradient} relative`">
          <div class="absolute inset-0 flex items-center justify-center">
            <div class="text-white text-7xl font-bold opacity-20">
              {{ novel.title.substring(0, 2) }}
            </div>
          </div>

          <!-- Floating Stats Badge -->
          <div class="absolute bottom-4 right-4 bg-white/95 backdrop-blur-sm rounded-xl px-4 py-2 shadow-lg">
            <div class="flex items-center gap-2">
              <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              </svg>
              <span class="text-xl font-bold text-gray-800">
                {{ formatViewCount(novel.viewCount) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Novel Info -->
        <div class="p-8">
          <!-- Title & Author -->
          <h1 class="text-3xl font-bold text-gray-900 mb-3">
            {{ novel.title }}
          </h1>

          <div class="flex items-center gap-2 mb-6">
            <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
            <span class="text-lg text-gray-600 font-medium">{{ novel.author }}</span>
          </div>

          <!-- Stats Cards -->
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <!-- Total Views -->
            <div class="bg-gradient-to-br from-purple-50 to-pink-50 rounded-xl p-5 border border-purple-100">
              <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-medium text-purple-700">총 조회수</span>
                <svg class="w-5 h-5 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              </div>
              <div class="text-2xl font-bold text-purple-900">
                {{ formatViewCount(novel.viewCount) }}
              </div>
            </div>

            <!-- Total Reviews -->
            <div class="bg-gradient-to-br from-blue-50 to-cyan-50 rounded-xl p-5 border border-blue-100">
              <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-medium text-blue-700">리뷰 수</span>
                <svg class="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
                </svg>
              </div>
              <div class="text-2xl font-bold text-blue-900">
                {{ novel.reviews?.length || 0 }}
              </div>
            </div>

            <!-- Rating -->
            <div class="bg-gradient-to-br from-yellow-50 to-orange-50 rounded-xl p-5 border border-yellow-100">
              <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-medium text-yellow-700">평점</span>
                <svg class="w-5 h-5 text-yellow-500 fill-current" viewBox="0 0 20 20">
                  <path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/>
                </svg>
              </div>
              <div class="text-2xl font-bold text-yellow-900">
                4.5 / 5.0
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab Navigation -->
      <div class="bg-white rounded-2xl shadow-lg overflow-hidden">
        <div class="border-b border-gray-200">
          <nav class="flex -mb-px">
            <router-link
              :to="{ name: 'novel-info' }"
              class="group flex-1 px-6 py-4 text-center text-sm font-medium border-b-2 border-transparent
                     hover:border-purple-500 hover:text-purple-600 transition-all"
              active-class="!border-purple-600 !text-purple-600 bg-purple-50/30"
            >
              <div class="flex items-center justify-center gap-2">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>작품 정보</span>
              </div>
            </router-link>

            <router-link
              :to="{ name: 'novel-episodes' }"
              class="group flex-1 px-6 py-4 text-center text-sm font-medium border-b-2 border-transparent
                     hover:border-purple-500 hover:text-purple-600 transition-all"
              active-class="!border-purple-600 !text-purple-600 bg-purple-50/30"
            >
              <div class="flex items-center justify-center gap-2">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M4 6h16M4 10h16M4 14h16M4 18h16" />
                </svg>
                <span>회차 목록</span>
              </div>
            </router-link>

            <router-link
              :to="{ name: 'novel-reviews' }"
              class="group flex-1 px-6 py-4 text-center text-sm font-medium border-b-2 border-transparent
                     hover:border-purple-500 hover:text-purple-600 transition-all"
              active-class="!border-purple-600 !text-purple-600 bg-purple-50/30"
            >
              <div class="flex items-center justify-center gap-2">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                        d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                </svg>
                <span>리뷰</span>
              </div>
            </router-link>
          </nav>
        </div>

        <!-- Tab Content -->
        <div class="p-6">
          <router-view :novel="novel" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* No additional styles needed - all using Tailwind CSS */
</style>
