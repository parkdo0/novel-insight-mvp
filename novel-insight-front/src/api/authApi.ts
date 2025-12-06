import api from './axiosConfig';

//
// # 인증 API
//
// ## Cookie 기반 인증
//
// - 백엔드가 HttpOnly Cookie로 토큰을 관리
// - localStorage 사용 안 함 (XSS 방지)
// - 로그인 성공 시: 백엔드가 Set-Cookie 헤더로 토큰 설정
// - 이후 모든 요청: 자동으로 Cookie 포함됨
//
// ## 로그인 흐름
//
// 1. `loginUser(email, password)` 호출
// 2. 백엔드 응답: "로그인 성공" (토큰은 Cookie에)
// 3. 이후 모든 API 요청에 자동으로 Cookie 포함
//

//
// 로그인
//
// ## 반환값
//
// - 성공: "로그인 성공" 문자열
// - 실패: 예외 발생 (axios error)
//
// ## 사용 예시
//
// ```typescript
// try {
//   const message = await loginUser('user@example.com', 'password123');
//   console.log(message);  // "로그인 성공"
//   // 이제 모든 API 요청에 Cookie가 자동으로 포함됨
// } catch (error) {
//   console.error('로그인 실패:', error.response?.data);
// }
// ```
//
export const loginUser = async (email: string, pass: string) => {
    const response = await api.post('/login', { email, password: pass });
    return response.data;  // "로그인 성공"
};

//
// 회원가입
//
// ## 반환값
//
// - 성공: "회원가입 성공" 문자열
// - 실패: 예외 발생
//
// ## 사용 예시
//
// ```typescript
// try {
//   const message = await signupUser('user@example.com', 'password123', '독자A', 'READER');
//   console.log(message);  // "회원가입 성공"
//   // 회원가입 후 자동 로그인되지 않으므로 수동으로 loginUser 호출 필요
// } catch (error) {
//   console.error('회원가입 실패:', error.response?.data);
// }
// ```
//
export const signupUser = async (email: string, pass: string, nickname: string, role?: 'READER' | 'AUTHOR') => {
    const response = await api.post('/signup', {
        email,
        password: pass,
        nickname,
        role: role || 'READER'
    });
    return response.data;  // "회원가입 성공"
};

//
// 내 정보 가져오기
//
// ## 반환값
//
// ```typescript
// {
//   id: number,
//   email: string,
//   nickname: string,
//   point: number
// }
// ```
//
// ## 사용 예시
//
// ```typescript
// try {
//   const user = await getMyInfo();
//   console.log(user);  // { id: 123, email: "...", nickname: "...", point: 5000 }
// } catch (error) {
//   // 401 에러: 로그인 안 됨 → axiosConfig.ts에서 자동으로 로그인 페이지로 리다이렉트
//   console.error('인증 실패:', error);
// }
// ```
//
export const getMyInfo = async () => {
    const response = await api.get('/users/me');
    return response.data;  // { id, email, nickname, point }
};

//
// 로그아웃 (현재 기기만)
//
// ## 반환값
//
// - 성공: "로그아웃 성공" 문자열
// - 실패: 예외 발생
//
// ## 사용 예시
//
// ```typescript
// try {
//   const message = await logoutUser();
//   console.log(message);  // "로그아웃 성공"
//   // 백엔드가 Cookie를 삭제함
//   window.location.href = '/login';  // 로그인 페이지로 이동
// } catch (error) {
//   console.error('로그아웃 실패:', error);
// }
// ```
//
export const logoutUser = async () => {
    const response = await api.post('/api/auth/logout');
    return response.data;  // "로그아웃 성공"
};

//
// 로그아웃 (모든 기기)
//
// ## 반환값
//
// - 성공: "모든 기기에서 로그아웃되었습니다" 문자열
// - 실패: 예외 발생
//
// ## 사용 예시
//
// ```typescript
// try {
//   const message = await logoutAllDevices();
//   console.log(message);  // "모든 기기에서 로그아웃되었습니다"
//   window.location.href = '/login';
// } catch (error) {
//   console.error('전체 로그아웃 실패:', error);
// }
// ```
//
export const logoutAllDevices = async () => {
    const response = await api.post('/api/auth/logout-all');
    return response.data;  // "모든 기기에서 로그아웃되었습니다"
};
