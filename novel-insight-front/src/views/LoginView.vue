<!--
  ========================================
  LoginView - 로그인/회원가입 페이지 (Tailwind CSS 디자인 개선)
  ========================================

  [디자인 개선 사항]
  - 현대적인 그라데이션 배경
  - 탭 전환 애니메이션
  - 반응형 레이아웃
  - 세련된 로고 및 타이포그래피
-->
<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import LoginForm from '../components/auth/LoginForm.vue'
import SignupForm from '../components/auth/SignupForm.vue'
import { ref } from 'vue'

const router = useRouter()
const route = useRoute()
const mode = ref('login')

const onLoginSuccess = () => {
  const redirectPath = route.query.redirect as string
  console.log("로그인 성공! 이동할 경로:", redirectPath || '/')

  if (redirectPath) {
    router.push(redirectPath)
  } else {
    router.push('/')
  }
}

const onSignupSuccess = () => {
  mode.value = 'login'
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-purple-500 via-pink-500 to-red-500 flex items-center justify-center p-4">
    <!-- Background Pattern -->
    <div class="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGRlZnM+PHBhdHRlcm4gaWQ9ImdyaWQiIHdpZHRoPSI2MCIgaGVpZ2h0PSI2MCIgcGF0dGVyblVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+PHBhdGggZD0iTSAxMCAwIEwgMCAwIDAgMTAiIGZpbGw9Im5vbmUiIHN0cm9rZT0icmdiYSgyNTUsMjU1LDI1NSwwLjEpIiBzdHJva2Utd2lkdGg9IjEiLz48L3BhdHRlcm4+PC9kZWZzPjxyZWN0IHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiIGZpbGw9InVybCgjZ3JpZCkiLz48L3N2Zz4=')] opacity-30"></div>

    <!-- Main Card -->
    <div class="relative w-full max-w-md">
      <!-- Logo -->
      <div
        class="text-center mb-8 cursor-pointer transform hover:scale-105 transition-transform"
        @click="router.push('/')"
      >
        <div class="inline-block bg-white rounded-2xl px-6 py-4 shadow-2xl">
          <h1 class="text-3xl font-bold bg-gradient-to-r from-purple-600 to-pink-600 bg-clip-text text-transparent">
            📚 Novel Insight
          </h1>
          <p class="text-sm text-gray-500 mt-1">웹소설의 새로운 인사이트</p>
        </div>
      </div>

      <!-- Auth Card -->
      <div class="bg-white rounded-2xl shadow-2xl overflow-hidden">
        <!-- Tab Header -->
        <div class="flex border-b border-gray-200">
          <button
            @click="mode = 'login'"
            :class="[
              'flex-1 py-4 text-center font-semibold transition-all relative',
              mode === 'login'
                ? 'text-purple-600 bg-purple-50'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-50'
            ]"
          >
            로그인
            <div
              v-if="mode === 'login'"
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-purple-500 to-pink-500"
            ></div>
          </button>

          <button
            @click="mode = 'signup'"
            :class="[
              'flex-1 py-4 text-center font-semibold transition-all relative',
              mode === 'signup'
                ? 'text-purple-600 bg-purple-50'
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-50'
            ]"
          >
            회원가입
            <div
              v-if="mode === 'signup'"
              class="absolute bottom-0 left-0 right-0 h-0.5 bg-gradient-to-r from-purple-500 to-pink-500"
            ></div>
          </button>
        </div>

        <!-- Form Content -->
        <div class="p-8">
          <transition
            name="fade"
            mode="out-in"
          >
            <LoginForm
              v-if="mode === 'login'"
              @login-success="onLoginSuccess"
              @log="console.log"
            />
            <SignupForm
              v-else
              @signup-success="onSignupSuccess"
              @switch-mode="mode = 'login'"
              @log="console.log"
            />
          </transition>
        </div>
      </div>

      <!-- Footer Links -->
      <div class="mt-6 text-center">
        <p class="text-white text-sm drop-shadow-lg">
          © 2024 Novel Insight. All rights reserved.
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Fade transition for tab switching */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
