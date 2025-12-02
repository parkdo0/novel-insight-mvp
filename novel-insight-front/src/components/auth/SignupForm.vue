<script setup lang="ts">
import { ref } from 'vue'
import { signupUser } from '../../api/authApi'

const emit = defineEmits<{
  (e: 'signup-success'): void
  (e: 'log', message: string): void
  (e: 'switch-mode'): void // 로그인 화면으로 전환
}>()

const email = ref('')
const password = ref('')
const nickname = ref('')
const isLoading = ref(false)

const handleSignup = async () => {
  if (!email.value || !password.value || !nickname.value) {
    alert("모든 항목을 입력해주세요.")
    return
  }

  isLoading.value = true
  try {
    await signupUser(email.value, password.value, nickname.value)
    emit('log', `✅ 회원가입 성공! (Email: ${email.value})`)
    alert("가입 성공! 로그인 해주세요.")
    emit('switch-mode') // 로그인 화면으로 이동
  } catch (error: any) {
    emit('log', `❌ 가입 실패: ${error.response?.data || error.message}`)
    alert("가입 실패: " + (error.response?.data || error.message))
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="auth-card">
    <div class="header">
      <h2>🆕 회원가입</h2>
      <p>Novel Insight의 멤버가 되어보세요</p>
    </div>

    <div class="form-group">
      <label>이메일</label>
      <input v-model="email" placeholder="example@sparta.com" />
    </div>

    <div class="form-group">
      <label>비밀번호</label>
      <input v-model="password" type="password" placeholder="비밀번호" />
    </div>

    <div class="form-group">
      <label>닉네임</label>
      <input v-model="nickname" placeholder="활동명" />
    </div>

    <button @click="handleSignup" :disabled="isLoading" class="primary-btn">
      {{ isLoading ? '가입 중...' : '회원가입' }}
    </button>

    <p class="switch-link">
      이미 계정이 있으신가요?
      <a href="#" @click.prevent="$emit('switch-mode')">로그인하기</a>
    </p>
  </div>
</template>

<style scoped>
/* 공통 스타일은 아래 HomeView에서 전역으로 잡거나 여기서 scoped로 줍니다 */
.auth-card {
  background: white;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  max-width: 400px;
  margin: 0 auto;
}
.header { text-align: center; margin-bottom: 25px; }
.header h2 { margin: 0; color: #333; }
.header p { color: #666; font-size: 0.9em; margin-top: 5px; }

.form-group { margin-bottom: 15px; }
.form-group label { display: block; margin-bottom: 5px; font-weight: 500; color: #555; font-size: 0.9em; }
.form-group input {
  width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 6px;
  font-size: 1em; transition: border-color 0.2s;
  box-sizing: border-box; /* input 튀어나감 방지 */
}
.form-group input:focus { border-color: #42b883; outline: none; }

.primary-btn {
  width: 100%; padding: 12px; background: #42b883; color: white;
  border: none; border-radius: 6px; font-weight: bold; cursor: pointer;
  font-size: 1em; margin-top: 10px; transition: background 0.2s;
}
.primary-btn:hover { background: #3aa876; }
.primary-btn:disabled { background: #ccc; cursor: not-allowed; }

.switch-link { text-align: center; margin-top: 15px; font-size: 0.9em; color: #666; }
.switch-link a { color: #42b883; text-decoration: none; font-weight: bold; }
</style>