<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
import {
  getAuthorDashboard,
  getTrend,
  getBestEpisodes,
  getWorstEpisodes,
  getDropOffDetail,
  type AuthorDashboardDto,
  type EpisodeStatsDto,
  type DropOffDetailDto
} from '../api/dashboardApi'

//
// # 작가 대시보드 Vue 컴포넌트
//
// ## 기능
//
// 1. 작가 통계 조회 (총 조회수, 독자 수, 완독률, 읽기 시간)
// 2. 감성 분석 (리뷰 긍정/부정 비율)
// 3. 종합 품질 점수
// 4. 최근 30일 트렌드 차트
// 5. 베스트/워스트 에피소드
// 6. 에피소드 이탈 지점 분석
//
// ## 차트 라이브러리
//
// - Chart.js 사용 (CDN 로드)
// - index.html에 <script> 추가 필요
//

const router = useRouter()
const authStore = useAuthStore()

// State
const loading = ref(false)
const dashboard = ref<AuthorDashboardDto | null>(null)
const trendData = ref<AuthorDashboardDto[]>([])
const bestEpisodes = ref<EpisodeStatsDto[]>([])
const worstEpisodes = ref<EpisodeStatsDto[]>([])
const dropOffData = ref<DropOffDetailDto | null>(null)

const selectedEpisodeId = ref<number>(1)

// Computed
const authorId = computed(() => authStore.userId)

// 차트 인스턴스
let trendChart: any = null
let scoreChart: any = null
let dropOffChart: any = null

//
// 대시보드 로드
//
const loadDashboard = async () => {
  if (!authorId.value) {
    alert('로그인이 필요합니다.')
    router.push('/login')
    return
  }

  loading.value = true

  try {
    // 1. 메인 대시보드 데이터
    dashboard.value = await getAuthorDashboard(authorId.value)

    // 2. 트렌드 데이터 (최근 30일)
    trendData.value = await getTrend(authorId.value, 30)

    // 3. 베스트/워스트 에피소드 (novelId가 필요하므로 가정)
    // 실제로는 사용자의 소설 목록을 먼저 가져와야 함
    const assumedNovelId = 1  // TODO: 실제 소설 ID로 교체
    bestEpisodes.value = await getBestEpisodes(assumedNovelId, 5)
    worstEpisodes.value = await getWorstEpisodes(assumedNovelId, 5)

    // 차트 그리기
    renderCharts()
  } catch (error: any) {
    console.error('대시보드 로드 실패:', error)
    alert('대시보드 데이터를 불러올 수 없습니다.')
  } finally {
    loading.value = false
  }
}

//
// 이탈 지점 분석 로드
//
const loadDropOffAnalysis = async () => {
  if (!selectedEpisodeId.value) {
    alert('에피소드 ID를 입력하세요.')
    return
  }

  try {
    dropOffData.value = await getDropOffDetail(selectedEpisodeId.value)
    renderDropOffChart()
  } catch (error: any) {
    console.error('이탈 분석 실패:', error)
    alert('이탈 분석 데이터를 불러올 수 없습니다.')
  }
}

//
// 차트 렌더링
//
const renderCharts = () => {
  if (!dashboard.value || !trendData.value.length) return

  // Chart.js가 로드되었는지 확인
  if (typeof (window as any).Chart === 'undefined') {
    console.error('Chart.js가 로드되지 않았습니다.')
    return
  }

  const Chart = (window as any).Chart

  // 1. 트렌드 차트
  const trendCtx = document.getElementById('trendChart') as HTMLCanvasElement
  if (trendCtx) {
    if (trendChart) trendChart.destroy()

    trendChart = new Chart(trendCtx, {
      type: 'line',
      data: {
        labels: trendData.value.map(d => d.aggregatedDate),
        datasets: [
          {
            label: '조회수',
            data: trendData.value.map(d => d.totalViews),
            borderColor: '#667eea',
            backgroundColor: 'rgba(102, 126, 234, 0.1)',
            yAxisID: 'y'
          },
          {
            label: '완독률 (%)',
            data: trendData.value.map(d => d.completionRate),
            borderColor: '#f59e0b',
            backgroundColor: 'rgba(245, 158, 11, 0.1)',
            yAxisID: 'y1'
          }
        ]
      },
      options: {
        responsive: true,
        interaction: {
          mode: 'index',
          intersect: false
        },
        scales: {
          y: {
            type: 'linear',
            display: true,
            position: 'left'
          },
          y1: {
            type: 'linear',
            display: true,
            position: 'right',
            grid: {
              drawOnChartArea: false
            }
          }
        }
      }
    })
  }

  // 2. 종합 점수 차트
  const scoreCtx = document.getElementById('insightScoreChart') as HTMLCanvasElement
  if (scoreCtx) {
    if (scoreChart) scoreChart.destroy()

    scoreChart = new Chart(scoreCtx, {
      type: 'doughnut',
      data: {
        datasets: [{
          data: [dashboard.value.insightScore, 100 - dashboard.value.insightScore],
          backgroundColor: ['#667eea', '#e5e7eb'],
          borderWidth: 0
        }]
      },
      options: {
        cutout: '80%',
        plugins: {
          legend: {
            display: false
          }
        }
      }
    })
  }
}

//
// 이탈 지점 차트 렌더링
//
const renderDropOffChart = () => {
  if (!dropOffData.value) return

  if (typeof (window as any).Chart === 'undefined') {
    console.error('Chart.js가 로드되지 않았습니다.')
    return
  }

  const Chart = (window as any).Chart

  const dropOffCtx = document.getElementById('dropOffChart') as HTMLCanvasElement
  if (dropOffCtx) {
    if (dropOffChart) dropOffChart.destroy()

    const labels = Object.keys(dropOffData.value.dropOffMap)
    const values = Object.values(dropOffData.value.dropOffMap)

    dropOffChart = new Chart(dropOffCtx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: '이탈자 수',
          data: values,
          backgroundColor: 'rgba(239, 68, 68, 0.5)',
          borderColor: 'rgba(239, 68, 68, 1)',
          borderWidth: 1
        }]
      },
      options: {
        responsive: true,
        plugins: {
          legend: {
            display: false
          }
        },
        scales: {
          y: {
            beginAtZero: true
          }
        }
      }
    })
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-purple-600 to-indigo-700 p-6">
    <div class="max-w-7xl mx-auto">
      <!-- 헤더 -->
      <div class="mb-8 text-white">
        <h1 class="text-4xl font-bold mb-2">📚 Novel Insight</h1>
        <p class="text-lg opacity-90">작가 대시보드 - 실시간 독자 분석</p>
      </div>

      <!-- 로딩 인디케이터 -->
      <div v-if="loading" class="flex justify-center items-center py-20">
        <div class="animate-spin rounded-full h-16 w-16 border-t-4 border-white"></div>
      </div>

      <!-- 대시보드 콘텐츠 -->
      <div v-else-if="dashboard" class="space-y-6">
        <!-- 주요 지표 카드 -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6">
          <div class="bg-gradient-to-br from-purple-500 to-indigo-600 p-6 rounded-lg text-white shadow-lg">
            <div class="text-sm opacity-80 mb-1">총 조회수</div>
            <div class="text-3xl font-bold">{{ dashboard.totalViews.toLocaleString() }}</div>
            <div class="text-xs opacity-60 mt-2">최근 30일</div>
          </div>
          <div class="bg-gradient-to-br from-purple-500 to-indigo-600 p-6 rounded-lg text-white shadow-lg">
            <div class="text-sm opacity-80 mb-1">고유 독자</div>
            <div class="text-3xl font-bold">{{ dashboard.uniqueReaders.toLocaleString() }}</div>
            <div class="text-xs opacity-60 mt-2">세션 기준</div>
          </div>
          <div class="bg-gradient-to-br from-purple-500 to-indigo-600 p-6 rounded-lg text-white shadow-lg">
            <div class="text-sm opacity-80 mb-1">완독률</div>
            <div class="text-3xl font-bold">{{ dashboard.completionRate }}%</div>
            <div class="text-xs opacity-60 mt-2">90% 이상 기준</div>
          </div>
          <div class="bg-gradient-to-br from-purple-500 to-indigo-600 p-6 rounded-lg text-white shadow-lg">
            <div class="text-sm opacity-80 mb-1">평균 읽기 시간</div>
            <div class="text-3xl font-bold">{{ dashboard.avgReadingTimeMinutes }}분</div>
            <div class="text-xs opacity-60 mt-2">에피소드당</div>
          </div>
        </div>

        <!-- 감성 분석 & 종합 점수 -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div class="bg-white p-6 rounded-lg shadow-lg">
            <h3 class="text-xl font-bold mb-4 text-gray-800">📊 감성 분석</h3>
            <div class="space-y-3">
              <div class="flex justify-between items-center">
                <span class="text-gray-600">총 리뷰</span>
                <span class="font-bold">{{ dashboard.totalReviews.toLocaleString() }}개</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-gray-600">긍정 비율</span>
                <div class="flex items-center gap-2">
                  <div class="w-24 bg-gray-200 rounded-full h-2">
                    <div class="bg-green-500 h-2 rounded-full" :style="`width: ${dashboard.positiveRate}%`"></div>
                  </div>
                  <span class="font-bold text-green-600">{{ dashboard.positiveRate }}%</span>
                </div>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-gray-600">부정 비율</span>
                <div class="flex items-center gap-2">
                  <div class="w-24 bg-gray-200 rounded-full h-2">
                    <div class="bg-red-500 h-2 rounded-full" :style="`width: ${dashboard.negativeRate}%`"></div>
                  </div>
                  <span class="font-bold text-red-600">{{ dashboard.negativeRate }}%</span>
                </div>
              </div>
            </div>
          </div>

          <div class="bg-white p-6 rounded-lg shadow-lg">
            <h3 class="text-xl font-bold mb-4 text-gray-800">🏆 종합 품질 점수</h3>
            <div class="flex items-center justify-center">
              <div class="relative w-48 h-48">
                <canvas id="insightScoreChart"></canvas>
                <div class="absolute inset-0 flex items-center justify-center">
                  <div class="text-center">
                    <div class="text-4xl font-bold text-purple-600">{{ Math.round(dashboard.insightScore) }}</div>
                    <div class="text-sm text-gray-500">/ 100</div>
                  </div>
                </div>
              </div>
            </div>
            <p class="text-xs text-gray-500 text-center mt-4">
              완독률(40%) + 긍정률(30%) + 기쁨(20%) - 분노(10%)
            </p>
          </div>
        </div>

        <!-- 트렌드 차트 -->
        <div class="bg-white p-6 rounded-lg shadow-lg">
          <h3 class="text-xl font-bold mb-4 text-gray-800">📈 최근 30일 트렌드</h3>
          <canvas id="trendChart" height="100"></canvas>
        </div>

        <!-- 베스트/워스트 에피소드 -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div class="bg-white p-6 rounded-lg shadow-lg">
            <h3 class="text-xl font-bold mb-4 text-gray-800">🌟 베스트 에피소드 TOP 5</h3>
            <div class="space-y-2">
              <div
                v-for="(ep, index) in bestEpisodes"
                :key="ep.episodeId"
                class="flex items-center justify-between p-3 bg-green-50 rounded-lg"
              >
                <div class="flex items-center gap-3">
                  <span class="w-8 h-8 bg-green-500 text-white rounded-full flex items-center justify-center font-bold">
                    {{ index + 1 }}
                  </span>
                  <span class="font-medium">에피소드 {{ ep.episodeId }}</span>
                </div>
                <span class="text-green-600 font-bold">{{ ep.completionRate }}%</span>
              </div>
            </div>
          </div>

          <div class="bg-white p-6 rounded-lg shadow-lg">
            <h3 class="text-xl font-bold mb-4 text-gray-800">⚠️ 개선 필요 에피소드</h3>
            <div class="space-y-2">
              <div
                v-for="(ep, index) in worstEpisodes"
                :key="ep.episodeId"
                class="flex items-center justify-between p-3 bg-red-50 rounded-lg"
              >
                <div class="flex items-center gap-3">
                  <span class="w-8 h-8 bg-red-500 text-white rounded-full flex items-center justify-center font-bold">
                    {{ index + 1 }}
                  </span>
                  <span class="font-medium">에피소드 {{ ep.episodeId }}</span>
                </div>
                <span class="text-red-600 font-bold">{{ ep.completionRate }}%</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 이탈 지점 분석 -->
        <div class="bg-white p-6 rounded-lg shadow-lg">
          <h3 class="text-xl font-bold mb-4 text-gray-800">🎯 에피소드 이탈 지점 분석</h3>
          <div class="mb-4 flex gap-2">
            <input
              v-model.number="selectedEpisodeId"
              type="number"
              placeholder="에피소드 ID 입력"
              class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent"
            />
            <button
              @click="loadDropOffAnalysis"
              class="px-6 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors font-medium"
            >
              분석하기
            </button>
          </div>
          <canvas id="dropOffChart" height="80"></canvas>
          <div v-if="dropOffData" class="mt-4 p-4 bg-yellow-50 border-l-4 border-yellow-400 rounded">
            <p class="text-sm text-yellow-800">{{ dropOffData.recommendation }}</p>
          </div>
        </div>
      </div>

      <!-- 데이터 없음 -->
      <div v-else class="text-center text-white py-20">
        <p class="text-xl">대시보드 데이터를 불러올 수 없습니다.</p>
        <p class="text-sm opacity-75 mt-2">배치 작업이 아직 실행되지 않았을 수 있습니다.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Tailwind CSS를 사용하므로 별도 스타일 불필요 */
</style>
