<!--
  ========================================
  MyPageView - 마이페이지 (Tailwind CSS 디자인 개선)
  ========================================

  [디자인 개선 사항]
  - 프로필 헤더 섹션 (아바타, 사용자 정보)
  - 포인트 카드 (그라데이션, 큰 숫자 표시)
  - 빠른 충전 버튼 그리드
  - 현대적인 테이블 디자인
  - 트랜잭션 타입별 색상 구분
-->
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { chargePoint, getMyHistory, type PointHistory } from '../api/paymentApi'

const authStore = useAuthStore()
const histories = ref<PointHistory[]>([])
const chargeAmount = ref(10000)

const fetchHistory = async () => {
  try {
    histories.value = await getMyHistory()
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  authStore.fetchUserInfo()
  fetchHistory()
})

const handleCharge = async () => {
  if (confirm(`${chargeAmount.value.toLocaleString()}원을 충전하시겠습니까?`)) {
    try {
      await chargePoint(chargeAmount.value)
      alert("충전이 완료되었습니다.")
      await authStore.fetchUserInfo()
      await fetchHistory()
    } catch (e) {
      alert("충전 실패")
    }
  }
}

// Quick charge options
const quickChargeOptions = [
  { value: 5000, label: '5,000원' },
  { value: 10000, label: '10,000원' },
  { value: 30000, label: '30,000원' },
  { value: 50000, label: '50,000원' },
]

const formatDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// Get user initials for avatar
const userInitials = computed(() => {
  const email = authStore.userEmail
  if (!email) return '?'
  return email.substring(0, 2).toUpperCase()
})

// Calculate statistics
const totalCharged = computed(() => {
  return histories.value
    .filter(h => h.type === 'CHARGE')
    .reduce((sum, h) => sum + h.amount, 0)
})

const totalSpent = computed(() => {
  return histories.value
    .filter(h => h.type === 'SPEND')
    .reduce((sum, h) => sum + h.amount, 0)
})
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <!-- Header -->
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900">마이페이지</h1>
        <p class="text-gray-600 mt-1">포인트 관리 및 사용 내역을 확인하세요</p>
      </div>

      <!-- Profile Section -->
      <div class="bg-white rounded-2xl shadow-lg p-8 mb-6">
        <div class="flex items-center gap-6">
          <!-- Avatar -->
          <div class="flex-shrink-0">
            <div class="w-20 h-20 bg-gradient-to-br from-purple-500 to-pink-500 rounded-full
                        flex items-center justify-center text-white text-2xl font-bold shadow-lg">
              {{ userInitials }}
            </div>
          </div>

          <!-- User Info -->
          <div class="flex-1">
            <h2 class="text-2xl font-bold text-gray-900 mb-1">
              {{ authStore.userNickname || '사용자' }}
            </h2>
            <div class="flex items-center gap-2 text-gray-600">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M16 12a4 4 0 10-8 0 4 4 0 008 0zm0 0v1.5a2.5 2.5 0 005 0V12a9 9 0 10-9 9m4.5-1.206a8.959 8.959 0 01-4.5 1.207" />
              </svg>
              <span>{{ authStore.userEmail }}</span>
            </div>
          </div>

          <!-- Edit Profile Button -->
          <button class="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
            <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </button>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <!-- Point Balance Card -->
        <div class="lg:col-span-2 bg-gradient-to-br from-purple-600 to-pink-600 rounded-2xl shadow-xl p-8 text-white">
          <div class="flex items-center justify-between mb-4">
            <div>
              <p class="text-purple-100 mb-2">보유 포인트</p>
              <h3 class="text-5xl font-bold">
                {{ authStore.userPoint.toLocaleString() }}
                <span class="text-2xl font-normal ml-1">P</span>
              </h3>
            </div>
            <div class="bg-white/20 backdrop-blur-sm rounded-xl p-4">
              <svg class="w-12 h-12" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4 pt-4 border-t border-white/30">
            <div>
              <p class="text-purple-100 text-sm mb-1">총 충전</p>
              <p class="text-xl font-semibold">+{{ totalCharged.toLocaleString() }}</p>
            </div>
            <div>
              <p class="text-purple-100 text-sm mb-1">총 사용</p>
              <p class="text-xl font-semibold">-{{ totalSpent.toLocaleString() }}</p>
            </div>
          </div>
        </div>

        <!-- Quick Stats -->
        <div class="space-y-4">
          <div class="bg-white rounded-xl shadow-lg p-6">
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm text-gray-600">거래 횟수</span>
              <svg class="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
            </div>
            <p class="text-3xl font-bold text-gray-900">{{ histories.length }}</p>
          </div>

          <div class="bg-white rounded-xl shadow-lg p-6">
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm text-gray-600">최근 활동</span>
              <svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <p class="text-sm text-gray-700">
              {{ histories.length > 0 ? formatDate(histories[0].createdAt) : '없음' }}
            </p>
          </div>
        </div>
      </div>

      <!-- Charge Section -->
      <div class="bg-white rounded-2xl shadow-lg p-8 mb-6">
        <div class="flex items-center gap-3 mb-6">
          <div class="bg-purple-100 rounded-lg p-2">
            <svg class="w-6 h-6 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                    d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <h3 class="text-xl font-bold text-gray-900">빠른 충전</h3>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <button
            v-for="option in quickChargeOptions"
            :key="option.value"
            @click="chargeAmount = option.value"
            :class="[
              'p-4 border-2 rounded-xl font-semibold transition-all',
              chargeAmount === option.value
                ? 'border-purple-600 bg-purple-50 text-purple-700'
                : 'border-gray-200 hover:border-purple-300 text-gray-700'
            ]"
          >
            {{ option.label }}
          </button>
        </div>

        <button
          @click="handleCharge"
          class="w-full mt-6 py-4 bg-gradient-to-r from-purple-600 to-pink-600
                 text-white font-bold rounded-xl shadow-lg
                 hover:from-purple-700 hover:to-pink-700
                 transform hover:scale-[1.02] transition-all"
        >
          {{ chargeAmount.toLocaleString() }}원 충전하기
        </button>
      </div>

      <!-- Transaction History -->
      <div class="bg-white rounded-2xl shadow-lg overflow-hidden">
        <div class="p-6 border-b border-gray-200">
          <div class="flex items-center gap-3">
            <div class="bg-blue-100 rounded-lg p-2">
              <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <h3 class="text-xl font-bold text-gray-900">거래 내역</h3>
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50 border-b border-gray-200">
              <tr>
                <th class="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  날짜
                </th>
                <th class="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  내용
                </th>
                <th class="px-6 py-4 text-right text-xs font-semibold text-gray-600 uppercase tracking-wider">
                  금액
                </th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr
                v-for="item in histories"
                :key="item.id"
                class="hover:bg-gray-50 transition-colors"
              >
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {{ formatDate(item.createdAt) }}
                </td>
                <td class="px-6 py-4 text-sm text-gray-900">
                  <div class="flex items-center gap-2">
                    <span
                      :class="[
                        'inline-block w-2 h-2 rounded-full',
                        item.type === 'CHARGE' ? 'bg-blue-500' : 'bg-red-500'
                      ]"
                    ></span>
                    {{ item.description }}
                  </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-right">
                  <span
                    :class="[
                      'text-sm font-bold',
                      item.type === 'CHARGE' ? 'text-blue-600' : 'text-red-600'
                    ]"
                  >
                    {{ item.type === 'CHARGE' ? '+' : '-' }}{{ item.amount.toLocaleString() }}
                  </span>
                </td>
              </tr>

              <tr v-if="histories.length === 0">
                <td colspan="3" class="px-6 py-16 text-center">
                  <div class="flex flex-col items-center">
                    <svg class="w-16 h-16 text-gray-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                    <p class="text-gray-500 font-medium">거래 내역이 없습니다</p>
                    <p class="text-gray-400 text-sm mt-1">포인트를 충전하거나 사용하면 내역이 표시됩니다</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* No additional styles needed - all using Tailwind CSS */
</style>
