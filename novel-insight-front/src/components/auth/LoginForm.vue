<!--
  ========================================
  LoginForm - 로그인 폼 컴포넌트
  ========================================

  [Vue 컴포넌트 통신]
  - Props Down, Events Up 패턴
  - defineEmits: 자식 → 부모로 이벤트 발생
  - TypeScript 타입 안전성 확보

  [Pinia Store 사용]
  - useAuthStore(): Pinia 스토어 접근 (전역 상태 관리)
  - login() 메소드: JWT 인증 처리 후 localStorage 저장

  [폼 처리 패턴]
  - v-model: 양방향 바인딩 (Two-way Binding)
  - 비동기 요청 중 버튼 비활성화 (:disabled="isLoading")
-->
<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '../../stores/auth'

/**
 * [이벤트 정의 - 부모 컴포넌트와 통신]
 * - 'log': 로그 메시지 전달 (부모가 화면에 표시)
 * - 'login-success': 로그인 성공 시 부모에게 알림 (리다이렉트 처리)
 *
 * TypeScript 제네릭으로 타입 안전성 확보
 */
const emit = defineEmits<{
  (e: 'log', message: string): void
  (e: 'login-success'): void
}>()

/**
 * [Pinia Store - 전역 상태 관리]
 * - Vuex의 후속 라이브러리 (더 간결한 API)
 * - authStore.login(): JWT 토큰 받아서 localStorage 저장
 * - authStore.isLoggedIn: 로그인 상태 확인 (computed)
 */
const authStore = useAuthStore()

/**
 * [반응형 폼 상태]
 * - email, password: v-model로 input과 양방향 바인딩
 * - isLoading: 버튼 비활성화 및 텍스트 변경에 사용
 */
const email = ref('new_user@sparta.com')
const password = ref('1234')
const isLoading = ref(false)

/**
 * [로그인 처리 함수]
 * - async/await: 비동기 HTTP 요청 처리
 * - try-catch-finally: 에러 핸들링 표준 패턴
 * - emit: 부모 컴포넌트에게 성공/실패 알림
 */
const handleLogin = async () => {
  isLoading.value = true
  try {
    await authStore.login(email.value, password.value)
    emit('log', `로그인 성공!`)

    // 부모 컴포넌트가 리다이렉트 처리하도록 이벤트 발생
    emit('login-success')

  } catch (error: any) {
    emit('log', `로그인 실패: ${error.response?.data || error.message}`)
    alert("로그인 정보가 틀렸습니다.")
  } finally {
    // 성공/실패 관계없이 로딩 상태 해제
    isLoading.value = false
  }
}
</script>

<template>
  <div class="card">
    <h2>로그인</h2>
    <div class="form-group">
      <!-- v-model: input value와 email ref 양방향 바인딩 -->
      <input v-model="email" placeholder="이메일" />
    </div>
    <div class="form-group">
      <input v-model="password" type="password" placeholder="비밀번호" />
    </div>
    <!--
      동적 속성 바인딩:
      - :disabled: isLoading이 true면 버튼 비활성화
      - 삼항 연산자: 로딩 중일 때 버튼 텍스트 변경
    -->
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