<script setup lang="ts">
import { ref, onMounted } from 'vue'
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
  if (confirm(`${chargeAmount.value}원을 충전하시겠습니까?`)) {
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

const formatDate = (dateStr: string) => new Date(dateStr).toLocaleString()
</script>

<template>
  <div class="container">
    <h2>👤 마이페이지</h2>

    <div class="card mb-20">
      <div class="flex-between mb-20">
        <label>이메일</label>
        <span>{{ authStore.userEmail }}</span>
      </div>
      <div class="flex-between">
        <label>보유 포인트</label>
        <strong style="color:var(--primary-color); font-size:1.2rem">{{ authStore.userPoint.toLocaleString() }} P</strong>
      </div>
    </div>

    <div class="card mb-20">
      <h3>⚡ 포인트 충전</h3>
      <div class="flex-between">
        <select v-model="chargeAmount" style="flex:1; margin-right:10px;">
          <option :value="5000">5,000원</option>
          <option :value="10000">10,000원</option>
          <option :value="30000">30,000원</option>
          <option :value="50000">50,000원</option>
        </select>
        <button @click="handleCharge" class="btn btn-primary">충전하기</button>
      </div>
    </div>

    <div class="card">
      <h3>📜 사용/충전 내역</h3>
      <table style="width:100%; border-collapse: collapse;">
        <thead>
        <tr style="background:#f9f9f9;">
          <th style="padding:10px; text-align:left;">날짜</th>
          <th style="padding:10px; text-align:left;">내용</th>
          <th style="padding:10px; text-align:right;">금액</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="item in histories" :key="item.id" style="border-bottom:1px solid #eee;">
          <td style="padding:10px; font-size:0.9rem; color:#888;">{{ formatDate(item.createdAt) }}</td>
          <td style="padding:10px;">{{ item.description }}</td>
          <td style="padding:10px; text-align:right; font-weight:bold;"
              :style="{ color: item.type === 'CHARGE' ? 'blue' : 'red' }">
            {{ item.type === 'CHARGE' ? '+' : '-' }}{{ item.amount.toLocaleString() }}
          </td>
        </tr>
        <tr v-if="histories.length === 0">
          <td colspan="3" class="text-center" style="padding:20px; color:#999;">내역이 없습니다.</td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>