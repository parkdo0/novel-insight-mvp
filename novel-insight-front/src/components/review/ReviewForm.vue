<!--
  ========================================
  ReviewForm - 리뷰 작성 폼
  ========================================

  [인증된 사용자 전용 기능]
  - authStore.userEmail로 현재 로그인 사용자 정보 표시
  - JWT 토큰이 HTTP 요청 헤더에 자동 포함 (Axios Interceptor)

  [폼 초기화 패턴]
  - 성공 시 content.value = '' 로 폼 초기화
  - 사용자 경험(UX) 향상
-->
<script setup lang="ts">
import { ref } from 'vue'
import { createReview } from '../../api/reviewApi'
import { useAuthStore } from '../../stores/auth'

/**
 * [이벤트 정의]
 * - 부모 컴포넌트에게 성공/실패 로그 전달
 */
const emit = defineEmits<{
  (e: 'log', message: string): void
}>()

/**
 * [인증 상태 접근]
 * - authStore.userEmail: 현재 로그인된 사용자 이메일
 * - authStore.token: JWT 토큰 (HTTP 요청 시 자동 포함)
 */
const authStore = useAuthStore()

/**
 * [폼 상태]
 * - novelId: 기본값 1 (실제로는 라우터 파라미터로 받아야 함)
 * - content: 리뷰 내용 (textarea와 양방향 바인딩)
 */
const novelId = ref(1)
const content = ref('')

/**
 * [리뷰 등록 처리]
 * - POST /api/novels/{novelId}/reviews
 * - 성공 시: content 초기화 (폼 리셋)
 * - 실패 시: 에러 메시지 표시
 */
const handleWrite = async () => {
  try {
    const data = await createReview(novelId.value, content.value)
    emit('log', `리뷰 등록 성공! (ID: ${data.id})`)
    content.value = ''  // 폼 초기화
  } catch (error: any) {
    emit('log', `작성 실패: ${error.response?.status || error.message}`)
  }
}
</script>

<template>
  <div class="card">
    <h2>리뷰 작성</h2>
    <!-- Pinia Store에서 가져온 사용자 정보 표시 -->
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