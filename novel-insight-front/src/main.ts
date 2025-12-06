import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

//
// # Vue 앱 초기화
//
// ## 중요: 앱 시작 시 로그인 상태 복원
//
// Cookie 기반 인증이므로:
// 1. 새로고침 시 Cookie는 유지됨
// 2. 하지만 Pinia Store는 초기화됨
// 3. 따라서 앱 시작 시 `checkAuth()` 호출하여 로그인 상태 복원
//

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

//
// 앱 마운트 전 로그인 상태 확인
//
// ## 동작 원리
//
// 1. Pinia 초기화 완료 후 authStore 생성
// 2. `checkAuth()` 호출 (Cookie로 `/users/me` 요청)
// 3. 성공: Store에 사용자 정보 저장
// 4. 실패: 로그인 안 됨 (정상)
// 5. Router 준비 후 앱 마운트
//
router.isReady().then(async () => {
    const authStore = useAuthStore()

    // Cookie가 유효하면 로그인 상태 복원
    await authStore.checkAuth()

    // 앱 마운트
    app.mount('#app')
})
