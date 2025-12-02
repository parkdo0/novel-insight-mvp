<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import LoginForm from '../components/auth/LoginForm.vue'
import SignupForm from '../components/auth/SignupForm.vue'
import { ref } from 'vue'

const router = useRouter()
const route = useRoute()
const mode = ref('login')

// ★★★ [수정 핵심] 로그인 성공 핸들러
const onLoginSuccess = () => {
  // 1. 쿼리 파라미터에서 redirect 주소를 꺼낸다.
  const redirectPath = route.query.redirect as string

  console.log("로그인 성공! 이동할 경로:", redirectPath || '/') // 디버깅용 로그

  // 2. 있으면 거기로, 없으면 메인('/')으로 이동
  if (redirectPath) {
    router.push(redirectPath)
  } else {
    router.push('/')
  }
}
</script>

<template>
  <div class="auth-page">
    <div v-if="mode === 'login'">
      <div class="logo" @click="router.push('/')">🦅 Novel Insight</div>
      <LoginForm @login-success="onLoginSuccess" @log="console.log" />
      <p class="switch">계정이 없나요? <a @click="mode='signup'">회원가입</a></p>
    </div>
    <div v-else>
      <div class="logo" @click="router.push('/')">🦅 Novel Insight</div>
      <SignupForm @switch-mode="mode='login'" @log="console.log" />
    </div>
  </div>
</template>

<style scoped>
.auth-page { max-width: 400px; margin: 50px auto; text-align: center; }
.logo { font-size: 1.5rem; font-weight: bold; color: #42b883; margin-bottom: 20px; cursor: pointer; }
.switch { margin-top: 20px; color: #666; }
.switch a { color: #42b883; cursor: pointer; font-weight: bold; text-decoration: underline; }
</style>