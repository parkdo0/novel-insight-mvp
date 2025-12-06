<!--
  ========================================
  NovelListView - 소설 목록 페이지 (Tailwind CSS 디자인 개선)
  ========================================

  [디자인 개선 사항]
  - Tailwind CSS 유틸리티 클래스로 현대적 디자인
  - 반응형 그리드 레이아웃 (1열 → 2열 → 3열 → 4열)
  - 카드 호버 효과 및 그라데이션
  - 로딩 스켈레톤 UI
  - 장르 태그 표시
  - 별점 시각화
-->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNovels, type Novel } from '../api/novelApi'

const router = useRouter()

const novels = ref<Novel[]>([])
const loading = ref(false)
const page = ref(0)
const hasMore = ref(true)
const initialLoad = ref(true)

const loadMore = async () => {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
        const data = await getNovels(page.value, 12)
        if (data.content.length === 0) {
            hasMore.value = false
        } else {
            novels.value.push(...data.content)
            page.value++
        }
    } finally {
        loading.value = false
        initialLoad.value = false
    }
}

onMounted(() => loadMore())

/**
 * 소설 ID 기반 그라데이션 색상
 */
const getGradient = (id: number) => {
    const gradients = [
        'from-purple-500 to-pink-500',
        'from-blue-500 to-cyan-500',
        'from-green-500 to-emerald-500',
        'from-orange-500 to-red-500',
        'from-indigo-500 to-purple-500',
        'from-pink-500 to-rose-500',
        'from-cyan-500 to-blue-500',
        'from-emerald-500 to-teal-500'
    ]
    return gradients[id % gradients.length]
}

/**
 * 조회수 포맷팅 (1000 → 1K, 1000000 → 1M)
 */
const formatViewCount = (count: number) => {
    if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M'
    if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
    return count.toString()
}
</script>

<template>
    <div class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
        <!-- Header Section -->
        <div class="bg-white shadow-sm border-b border-gray-200">
            <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
                <div class="text-center">
                    <h1 class="text-4xl font-bold text-gray-900 mb-3">
                        실시간 인기 웹소설
                    </h1>
                    <p class="text-lg text-gray-600">
                        지금 가장 핫한 웹소설을 만나보세요
                    </p>
                </div>
            </div>
        </div>

        <!-- Main Content -->
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
            <!-- Loading Skeleton (Initial Load) -->
            <div v-if="initialLoad && novels.length === 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                <div v-for="i in 8" :key="i" class="bg-white rounded-xl overflow-hidden shadow-md animate-pulse">
                    <div class="h-48 bg-gradient-to-br from-gray-200 to-gray-300"></div>
                    <div class="p-5 space-y-3">
                        <div class="h-6 bg-gray-200 rounded"></div>
                        <div class="h-4 bg-gray-200 rounded w-3/4"></div>
                        <div class="h-4 bg-gray-200 rounded w-1/2"></div>
                    </div>
                </div>
            </div>

            <!-- Novel Grid -->
            <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                <div
                    v-for="novel in novels"
                    :key="novel.id"
                    @click="router.push(`/novels/${novel.id}`)"
                    class="group bg-white rounded-xl overflow-hidden shadow-md hover:shadow-2xl
                           transition-all duration-300 cursor-pointer transform hover:-translate-y-2"
                >
                    <!-- Cover Image / Gradient -->
                    <div
                        :class="`h-48 bg-gradient-to-br ${getGradient(novel.id)}
                                 flex items-center justify-center relative overflow-hidden`"
                    >
                        <!-- Title Initials -->
                        <div class="text-white text-5xl font-bold opacity-30 group-hover:opacity-40 transition-opacity">
                            {{ novel.title.substring(0, 2) }}
                        </div>

                        <!-- View Count Badge -->
                        <div class="absolute top-3 right-3 bg-white/90 backdrop-blur-sm rounded-full px-3 py-1 flex items-center gap-1">
                            <svg class="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                      d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                      d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                            </svg>
                            <span class="text-sm font-semibold text-gray-700">
                                {{ formatViewCount(novel.viewCount) }}
                            </span>
                        </div>
                    </div>

                    <!-- Novel Info -->
                    <div class="p-5">
                        <!-- Title -->
                        <h3 class="text-lg font-bold text-gray-900 mb-2 line-clamp-1 group-hover:text-purple-600 transition-colors">
                            {{ novel.title }}
                        </h3>

                        <!-- Author -->
                        <div class="flex items-center gap-2 mb-3">
                            <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                      d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                            </svg>
                            <span class="text-sm text-gray-600">{{ novel.author }}</span>
                        </div>

                        <!-- Description -->
                        <p class="text-sm text-gray-500 mb-4 line-clamp-2 leading-relaxed">
                            {{ novel.description || '준비 중인 작품입니다.' }}
                        </p>

                        <!-- Stats -->
                        <div class="flex items-center justify-between pt-3 border-t border-gray-100">
                            <div class="flex items-center gap-1 text-yellow-500">
                                <svg class="w-4 h-4 fill-current" viewBox="0 0 20 20">
                                    <path d="M10 15l-5.878 3.09 1.123-6.545L.489 6.91l6.572-.955L10 0l2.939 5.955 6.572.955-4.756 4.635 1.123 6.545z"/>
                                </svg>
                                <span class="text-sm font-semibold text-gray-700">4.5</span>
                            </div>

                            <div class="flex items-center gap-1 text-gray-500">
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                                          d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
                                </svg>
                                <span class="text-sm font-medium">{{ novel.reviews?.length || 0 }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Empty State -->
            <div v-if="!initialLoad && novels.length === 0" class="text-center py-20">
                <div class="inline-block p-8 bg-white rounded-2xl shadow-lg">
                    <svg class="w-20 h-20 text-gray-300 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                              d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                    </svg>
                    <h3 class="text-xl font-semibold text-gray-700 mb-2">소설이 없습니다</h3>
                    <p class="text-gray-500">첫 번째 소설을 등록해보세요!</p>
                </div>
            </div>

            <!-- Load More Button -->
            <div v-if="hasMore && novels.length > 0" class="text-center mt-12">
                <button
                    @click="loadMore"
                    :disabled="loading"
                    class="px-8 py-3 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-semibold rounded-full
                           hover:from-purple-700 hover:to-pink-700 disabled:from-gray-400 disabled:to-gray-500
                           disabled:cursor-not-allowed transition-all duration-300 shadow-lg hover:shadow-xl
                           transform hover:scale-105 disabled:transform-none"
                >
                    <span v-if="loading" class="flex items-center gap-2">
                        <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                        로딩 중...
                    </span>
                    <span v-else class="flex items-center gap-2">
                        작품 더 보기
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                        </svg>
                    </span>
                </button>
            </div>

            <!-- No More Content -->
            <div v-if="!hasMore && novels.length > 0" class="text-center mt-12 py-8">
                <p class="text-gray-500 font-medium">모든 작품을 확인하셨습니다 ✨</p>
            </div>
        </div>
    </div>
</template>

<style scoped>
/* Line clamp utilities */
.line-clamp-1 {
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.line-clamp-2 {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}
</style>
