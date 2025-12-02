<script setup lang="ts">
import { useAuthStore } from './stores/auth'
import { useRouter } from 'vue-router'
import { onMounted } from 'vue'

const authStore = useAuthStore()
const router = useRouter()

// 새로고침 시 포인트 정보 최신화
onMounted(() => {
  if (authStore.isAuthenticated) {
    authStore.fetchUserInfo()
  }
})

const handleLogout = () => {
  authStore.logout()
  router.push('/')
}
</script>

<template>
  <header class="gnb">
    <div class="logo" @click="router.push('/')">🦅 Novel Insight</div>

    <div class="actions">
      <template v-if="authStore.isAuthenticated">
        <div class="user-profile" @click="router.push('/mypage')">
          <span class="welcome">반가워요, <b>{{ authStore.userEmail.split('@')[0] }}</b>님!</span>
          <span class="point-badge">{{ authStore.userPoint.toLocaleString() }} P</span>
        </div>
        <button @click="handleLogout" class="btn-logout">로그아웃</button>
      </template>

      <template v-else>
        <button @click="router.push('/login')" class="btn-login">로그인</button>
      </template>
    </div>
  </header>

  <RouterView />
</template>

<style>
body { margin: 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }

.gnb {
  background: white; padding: 0 40px; height: 70px; display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05); position: sticky; top: 0; z-index: 100;
}
.logo { font-weight: 900; font-size: 1.4rem; color: #42b883; cursor: pointer; letter-spacing: -1px; }

.actions { display: flex; align-items: center; gap: 15px; }

/* ★★★ [수정] 커서 포인터 추가 */
.user-profile { display: flex; align-items: center; gap: 10px; font-size: 0.95rem; color: #333; cursor: pointer; }
.user-profile:hover .welcome { text-decoration: underline; }

.welcome b { color: #42b883; }
.point-badge { background: #fff8e1; color: #fbc02d; padding: 4px 8px; border-radius: 12px; font-weight: bold; font-size: 0.85rem; border: 1px solid #fbc02d; }

button { cursor: pointer; border: none; border-radius: 6px; padding: 8px 16px; font-weight: bold; transition: all 0.2s; font-size: 0.9rem; }
.btn-login { background: white; border: 1px solid #ddd; color: #555; }
.btn-login:hover { background: #f9f9f9; border-color: #ccc; }
.btn-logout { background: #eee; color: #666; font-size: 0.85rem; padding: 6px 12px; }
.btn-logout:hover { background: #ddd; }
</style>