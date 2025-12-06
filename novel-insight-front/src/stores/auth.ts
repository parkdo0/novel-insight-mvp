import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginUser, getMyInfo, logoutUser } from '../api/authApi'

//
// # 인증 Store (Pinia)
//
// ## Cookie 기반 인증으로 변경
//
// ### Before (localStorage):
// - Access Token을 localStorage에 저장
// - XSS 공격에 취약
// - 수동으로 토큰 관리 필요
//
// ### After (HttpOnly Cookie):
// - 백엔드가 Set-Cookie로 토큰 설정
// - JavaScript 접근 불가 (XSS 방지)
// - 브라우저가 자동으로 Cookie 전송
//
// ## 주요 변경사항
//
// 1. `token` state 제거 (Cookie로 관리되므로 프론트에서 알 필요 없음)
// 2. `isAuthenticated` 계산 방식 변경:
//    - Before: `!!token.value`
//    - After: `userId.value > 0` (백엔드에서 가져온 사용자 정보 유무)
// 3. 새로고침 시: `checkAuth()` 호출하여 로그인 상태 복원
//

export const useAuthStore = defineStore('auth', () => {
    //
    // State
    //
    const userId = ref<number>(0)
    const userEmail = ref<string>('')
    const userNickname = ref<string>('')
    const userPoint = ref<number>(0)

    //
    // Computed
    //
    // 로그인 여부 판단: userId가 있으면 로그인 상태
    //
    const isAuthenticated = computed(() => userId.value > 0)

    //
    // Actions
    //

    //
    // 로그인 상태 확인 (새로고침 시 호출)
    //
    // ## 목적
    //
    // - 새로고침 시 Cookie가 유효하면 사용자 정보 복원
    // - 앱 시작 시 main.ts에서 호출
    //
    // ## 동작
    //
    // 1. `/users/me` API 호출 (Cookie 자동 전송)
    // 2. 성공: 사용자 정보 저장 → 로그인 상태
    // 3. 실패 (401): 로그인 안 됨
    //
    const checkAuth = async () => {
        try {
            const data = await getMyInfo()
            userId.value = data.id
            userEmail.value = data.email
            userNickname.value = data.nickname
            userPoint.value = data.point
            return true
        } catch (error) {
            // 401 에러: 로그인 안 됨 (정상)
            // 에러 로그 출력 안 함 (정상적인 상황)
            userId.value = 0
            userEmail.value = ''
            userNickname.value = ''
            userPoint.value = 0
            return false
        }
    }

    //
    // 사용자 정보 갱신
    //
    // ## 목적
    //
    // - 포인트 변경 후 최신 정보 반영
    // - 프로필 수정 후 닉네임 업데이트
    //
    const fetchUserInfo = async () => {
        if (!isAuthenticated.value) return
        try {
            const data = await getMyInfo()
            userId.value = data.id
            userEmail.value = data.email
            userNickname.value = data.nickname
            userPoint.value = data.point
        } catch (e) {
            console.error('정보 갱신 실패', e)
        }
    }

    //
    // 로그인
    //
    // ## 프로세스
    //
    // 1. `loginUser(email, password)` 호출
    // 2. 백엔드가 Set-Cookie로 토큰 설정
    // 3. `getMyInfo()` 호출하여 사용자 정보 가져오기
    // 4. Store에 사용자 정보 저장
    //
    // ## 반환값
    //
    // - 성공: true
    // - 실패: 예외 발생
    //
    const login = async (email: string, pass: string) => {
        try {
            // 1. 로그인 (백엔드가 Set-Cookie로 토큰 설정)
            await loginUser(email, pass)

            // 2. 사용자 정보 가져오기 (Cookie가 자동으로 전송됨)
            const data = await getMyInfo()
            userId.value = data.id
            userEmail.value = data.email
            userNickname.value = data.nickname
            userPoint.value = data.point

            return true
        } catch (error) {
            throw error
        }
    }

    //
    // 로그아웃
    //
    // ## 프로세스
    //
    // 1. `/api/auth/logout` 호출 (백엔드가 Cookie 삭제)
    // 2. Store 상태 초기화
    //
    const logout = async () => {
        try {
            // 백엔드에 로그아웃 요청 (Cookie 삭제)
            await logoutUser()
        } catch (error) {
            console.error('로그아웃 API 호출 실패', error)
            // 에러가 나도 로컬 상태는 초기화
        } finally {
            // Store 상태 초기화
            userId.value = 0
            userEmail.value = ''
            userNickname.value = ''
            userPoint.value = 0
        }
    }

    return {
        // State
        userId,
        userEmail,
        userNickname,
        userPoint,

        // Computed
        isAuthenticated,

        // Actions
        checkAuth,
        fetchUserInfo,
        login,
        logout
    }
})
