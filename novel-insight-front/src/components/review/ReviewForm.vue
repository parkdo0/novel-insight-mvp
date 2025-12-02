<script setup lang="ts">
import { ref } from 'vue'
import { createReview } from '../../api/reviewApi'
import { useAuthStore } from '../../stores/auth'

const emit = defineEmits<{
  (e: 'log', message: string): void
}>()

const authStore = useAuthStore()
const novelId = ref(1)
const content = ref('')

const handleWrite = async () => {
  try {
    const data = await createReview(novelId.value, content.value)
    emit('log', `✅ 리뷰 등록 성공! (ID: ${data.id})`)
    content.value = ''
  } catch (error: any) {
    emit('log', `❌ 작성 실패: ${error.response?.status || error.message}`)
  }
}
</script>

<template>
  <div class="card">
    <h2>📝 리뷰 작성</h2>
    <p>작성자: {{ authStore.userEmail }}</p>

    <div class="input-group">
      <label>소설 ID:</label>
      <input v-model="novelId" type="number" />
    </div>
    <textarea v-model="content" placeholder="내용 입력"></textarea>

    <div class="btn-group">
      <button @click="handleWrite">등록</button>
      <button class="secondary" @click="authStore.logout()">로그아웃</button>
    </div>
  </div>
</template>

<style scoped>
.card { border: 1px solid #ddd; padding: 20px; border-radius: 8px; background: #fff; color: #333; }
input, textarea { width: 100%; margin-bottom: 10px; padding: 8px; display: block; box-sizing: border-box; }
button { width: 100%; padding: 10px; background: #42b883; color: white; border: none; font-weight: bold; border-radius: 4px; cursor: pointer; }
button.secondary { background: #666; margin-top: 5px; }
</style>