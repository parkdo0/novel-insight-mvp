<!--
  ========================================
  SignupForm - 회원가입 폼 컴포넌트 (Tailwind CSS 디자인 개선)
  ========================================

  [디자인 개선 사항]
  - 현대적인 input 디자인 (아이콘 포함)
  - 실시간 유효성 검증 UI
  - 비밀번호 강도 표시기
  - Tailwind CSS 유틸리티 클래스 활용
-->
<script setup lang="ts">
import { ref, computed } from 'vue'
import { signupUser } from '../../api/authApi'

const emit = defineEmits<{
  (e: 'signup-success'): void
  (e: 'log', message: string): void
  (e: 'switch-mode'): void
}>()

const email = ref('')
const password = ref('')
const nickname = ref('')
const isLoading = ref(false)

/**
 * 비밀번호 강도 계산
 */
const passwordStrength = computed(() => {
  const pwd = password.value
  if (pwd.length === 0) return { level: 0, text: '', color: '' }
  if (pwd.length < 4) return { level: 1, text: '약함', color: 'bg-red-500' }
  if (pwd.length < 8) return { level: 2, text: '보통', color: 'bg-yellow-500' }
  return { level: 3, text: '강함', color: 'bg-green-500' }
})

const handleSignup = async () => {
  if (!email.value || !password.value || !nickname.value) {
    alert("모든 항목을 입력해주세요.")
    return
  }

  if (password.value.length < 4) {
    alert("비밀번호는 최소 4자 이상이어야 합니다.")
    return
  }

  isLoading.value = true
  try {
    await signupUser(email.value, password.value, nickname.value)
    emit('log', `회원가입 성공! (Email: ${email.value})`)
    alert("가입 성공! 로그인 해주세요.")
    emit('signup-success')
  } catch (error: any) {
    emit('log', `가입 실패: ${error.response?.data || error.message}`)
    alert("가입 실패: " + (error.response?.data || error.message))
  } finally {
    isLoading.value = false
  }
}

// Enter 키로 회원가입
const handleKeyPress = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !isLoading.value) {
    handleSignup()
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="text-2xl font-bold text-gray-900 mb-2">
        계정 만들기 🚀
      </h2>
      <p class="text-gray-600">
        무료로 가입하여 시작하세요
      </p>
    </div>

    <!-- Email Input -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-2">
        이메일 <span class="text-red-500">*</span>
      </label>
      <div class="relative">
        <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M16 12a4 4 0 10-8 0 4 4 0 008 0zm0 0v1.5a2.5 2.5 0 005 0V12a9 9 0 10-9 9m4.5-1.206a8.959 8.959 0 01-4.5 1.207" />
          </svg>
        </div>
        <input
          v-model="email"
          type="email"
          @keypress="handleKeyPress"
          placeholder="example@sparta.com"
          class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg
                 focus:ring-2 focus:ring-purple-500 focus:border-transparent
                 transition-all duration-200 text-gray-900 placeholder-gray-400"
        />
      </div>
    </div>

    <!-- Password Input -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-2">
        비밀번호 <span class="text-red-500">*</span>
      </label>
      <div class="relative">
        <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </div>
        <input
          v-model="password"
          type="password"
          @keypress="handleKeyPress"
          placeholder="8자 이상 권장"
          class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg
                 focus:ring-2 focus:ring-purple-500 focus:border-transparent
                 transition-all duration-200 text-gray-900 placeholder-gray-400"
        />
      </div>

      <!-- Password Strength Indicator -->
      <div v-if="password.length > 0" class="mt-2">
        <div class="flex items-center gap-2 mb-1">
          <div class="flex-1 bg-gray-200 rounded-full h-2 overflow-hidden">
            <div
              :class="[passwordStrength.color, 'h-full transition-all duration-300']"
              :style="{ width: `${(passwordStrength.level / 3) * 100}%` }"
            ></div>
          </div>
          <span
            :class="[
              'text-xs font-medium',
              passwordStrength.level === 1 ? 'text-red-600' : '',
              passwordStrength.level === 2 ? 'text-yellow-600' : '',
              passwordStrength.level === 3 ? 'text-green-600' : ''
            ]"
          >
            {{ passwordStrength.text }}
          </span>
        </div>
        <p class="text-xs text-gray-500">
          {{ password.length }}/8 자
        </p>
      </div>
    </div>

    <!-- Nickname Input -->
    <div>
      <label class="block text-sm font-medium text-gray-700 mb-2">
        닉네임 <span class="text-red-500">*</span>
      </label>
      <div class="relative">
        <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
          <svg class="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        </div>
        <input
          v-model="nickname"
          type="text"
          @keypress="handleKeyPress"
          placeholder="활동 시 사용할 이름"
          class="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg
                 focus:ring-2 focus:ring-purple-500 focus:border-transparent
                 transition-all duration-200 text-gray-900 placeholder-gray-400"
        />
      </div>
    </div>

    <!-- Terms -->
    <div class="space-y-3">
      <label class="flex items-start">
        <input type="checkbox" class="mt-1 rounded border-gray-300 text-purple-600 focus:ring-purple-500">
        <span class="ml-2 text-sm text-gray-600">
          <span class="font-medium text-gray-900">이용약관</span>에 동의합니다
        </span>
      </label>

      <label class="flex items-start">
        <input type="checkbox" class="mt-1 rounded border-gray-300 text-purple-600 focus:ring-purple-500">
        <span class="ml-2 text-sm text-gray-600">
          <span class="font-medium text-gray-900">개인정보 처리방침</span>에 동의합니다
        </span>
      </label>
    </div>

    <!-- Signup Button -->
    <button
      @click="handleSignup"
      :disabled="isLoading"
      class="w-full py-3 px-4 bg-gradient-to-r from-purple-600 to-pink-600
             text-white font-semibold rounded-lg shadow-lg
             hover:from-purple-700 hover:to-pink-700
             focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2
             disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed
             transition-all duration-200 transform hover:scale-[1.02] active:scale-[0.98]
             disabled:transform-none"
    >
      <span v-if="isLoading" class="flex items-center justify-center gap-2">
        <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        가입 중...
      </span>
      <span v-else>회원가입</span>
    </button>
  </div>
</template>

<style scoped>
/* No additional styles needed - all using Tailwind CSS */
</style>
