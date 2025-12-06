import axios from 'axios';

//
// # Axios 설정
//
// ## 중요: Cookie 기반 인증
//
// 백엔드가 HttpOnly Cookie로 토큰을 관리하므로:
// - `withCredentials: true` 필수 (Cookie 전송 활성화)
// - localStorage 사용 안 함 (XSS 공격 방지)
// - 모든 요청에 자동으로 Cookie가 포함됨
//
// ## 토큰 갱신 자동화
//
// 401 에러 발생 시 자동으로 Refresh Token으로 갱신 시도
// 갱신 성공: 원래 요청 재시도
// 갱신 실패: 로그인 페이지로 이동
//
const BASE_URL = 'http://localhost:8080';

const api = axios.create({
    baseURL: BASE_URL,
    withCredentials: true,  // ⭐ Cookie 전송 필수
    headers: { 'Content-Type': 'application/json' }
});

//
// Response Interceptor: 401 에러 시 자동 토큰 갱신
//
// ## 동작 원리
//
// 1. API 요청 → 401 에러 (Access Token 만료)
// 2. `/api/auth/refresh` 호출 (Refresh Token으로 갱신)
// 3. 갱신 성공 → 원래 요청 재시도
// 4. 갱신 실패 → 로그인 페이지로 리다이렉트
//
// ## 주의사항
//
// - Refresh Token도 만료되면 재로그인 필요
// - 무한 루프 방지: 갱신 API는 재시도 안 함
//
api.interceptors.response.use(
    // 성공 응답은 그대로 반환
    (response) => response,

    // 에러 응답 처리
    async (error) => {
        const originalRequest = error.config;

        // 401 에러 && 갱신 API가 아님 && 재시도 안 한 요청
        if (error.response?.status === 401 &&
            !originalRequest.url?.includes('/api/auth/refresh') &&
            !originalRequest._retry) {

            originalRequest._retry = true;

            try {
                // Refresh Token으로 갱신 시도
                await axios.post(`${BASE_URL}/api/auth/refresh`, {}, {
                    withCredentials: true
                });

                // 갱신 성공 → 원래 요청 재시도
                return api.request(originalRequest);
            } catch (refreshError) {
                // Refresh Token도 만료 → 로그인 페이지로
                console.error('토큰 갱신 실패. 재로그인이 필요합니다.');

                // Vue Router 사용 불가 (Axios 모듈이므로)
                // 따라서 window.location 사용
                if (typeof window !== 'undefined') {
                    window.location.href = '/login';
                }

                return Promise.reject(refreshError);
            }
        }

        return Promise.reject(error);
    }
);

export default api;
