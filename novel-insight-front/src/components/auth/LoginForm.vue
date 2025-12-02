<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '../../stores/auth'

// ★ 이벤트 정의: 부모(LoginView)에게 알릴 신호
const emit = defineEmits<{
  (e: 'log', message: string): void
  (e: 'login-success'): void // 이거 중요!
}>()

const authStore = useAuthStore()
const email = ref('new_user@sparta.com')
const password = ref('1234')
const isLoading = ref(false)

const handleLogin = async () => {
  isLoading.value = true
  try {
    await authStore.login(email.value, password.value)
    emit('log', `✅ 로그인 성공!`)

    // ★ 로그인 성공 신호를 부모에게 보냄 -> 부모가 리다이렉트 처리함
    emit('login-success')

  } catch (error: any) {
    emit('log', `❌ 로그인 실패: ${error.response?.data || error.message}`)
    alert("로그인 정보가 틀렸습니다.")
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="card">
    <h2>🔐 로그인</h2>
    <div class="form-group">
      <input v-model="email" placeholder="이메일" />
    </div>
    <div class="form-group">
      <input v-model="password" type="password" placeholder="비밀번호" />
    </div>
    <button @click="handleLogin" :disabled="isLoading">
      {{ isLoading ? '로그인 중...' : '로그인' }}
    </button>
  </div>
</template>

<style scoped>
.card { border: 1px solid #ddd; padding: 30px; border-radius: 12px; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.05); }
.form-group { margin-bottom: 15px; }
input { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 6px; box-sizing: border-box; font-size: 1rem; }
input:focus { border-color: #42b883; outline: none; }
button { width: 100%; padding: 12px; background: #42b883; color: white; border: none; font-weight: bold; border-radius: 6px; cursor: pointer; font-size: 1rem; }
button:disabled { background: #ccc; }
h2 { margin-top: 0; color: #333; }
</style>