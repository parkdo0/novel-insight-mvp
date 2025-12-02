import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginUser, getMyInfo } from '../api/authApi'

export const useAuthStore = defineStore('auth', () => {
    const token = ref<string>(localStorage.getItem('accessToken') || '')
    const userEmail = ref<string>(localStorage.getItem('userEmail') || '')
    const userPoint = ref<number>(0)

    // ★ [추가] 내 ID 저장 (새로고침 대비 로컬스토리지 사용 추천하지만, 여기선 fetchUserInfo로 복구)
    const userId = ref<number>(0)

    const isAuthenticated = computed(() => {
        return !!token.value && token.value !== 'undefined' && token.value !== 'null'
    })

    const fetchUserInfo = async () => {
        if (!isAuthenticated.value) return
        try {
            const data = await getMyInfo()
            userId.value = data.id // ★ ID 저장
            userEmail.value = data.email
            userPoint.value = data.point

            localStorage.setItem('userEmail', data.email)
        } catch (e) {
            console.error("정보 갱신 실패", e)
        }
    }

    const login = async (email: string, pass: string) => {
        try {
            const newToken = await loginUser(email, pass)
            token.value = newToken
            localStorage.setItem('accessToken', newToken)

            await fetchUserInfo() // 로그인 직후 ID/포인트 가져오기
            return true
        } catch (error) {
            throw error
        }
    }

    const logout = () => {
        token.value = ''
        userEmail.value = ''
        userPoint.value = 0
        userId.value = 0 // 초기화
        localStorage.removeItem('accessToken')
        localStorage.removeItem('userEmail')
    }

    return {
        token,
        userEmail,
        userPoint,
        userId, // ★ 외부 노출
        isAuthenticated,
        login,
        logout,
        fetchUserInfo
    }
})