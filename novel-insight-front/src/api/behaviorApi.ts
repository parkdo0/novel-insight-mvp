import api from './axiosConfig';

//
// # Behavior API (행동 로그)
//
// 사용자가 에피소드를 읽을 때의 행동을 추적합니다.
//
// ## 수집 데이터
//
// 1. 읽기 시작/종료 시간
// 2. 스크롤 깊이 (진도율)
// 3. 이탈 지점
// 4. 읽기 시간
//
// 이 데이터가 대시보드의 이탈 분석 차트로 표시됩니다.
//

//
// 읽기 세션 시작
//
// ## 호출 시점
//
// - 에피소드 페이지 진입 시 (onMounted)
//
// ## 반환값
//
// ```typescript
// {
//   logId: number,
//   sessionId: string
// }
// ```
//
export const startReading = async (episodeId: number, novelId: number) => {
    const response = await api.post('/api/behavior/start', {
        episodeId,
        novelId
    });
    return response.data;
};

//
// 스크롤 깊이 업데이트
//
// ## 호출 시점
//
// - 5초마다 자동 호출 (setInterval)
// - 스크롤 이벤트 발생 시
//
// ## 사용 예시
//
// ```typescript
// setInterval(() => {
//   const scrollDepth = (window.scrollY / document.body.scrollHeight) * 100;
//   updateScroll(scrollDepth);
// }, 5000);
// ```
//
export const updateScroll = async (scrollDepth: number) => {
    const response = await api.post('/api/behavior/scroll', {
        scrollDepth
    });
    return response.data;
};

//
// 읽기 세션 종료
//
// ## 호출 시점
//
// - 페이지 이탈 시 (beforeunload)
// - navigator.sendBeacon() 사용 권장
//
// ## 사용 예시
//
// ```typescript
// window.addEventListener('beforeunload', () => {
//   // Beacon API로 전송 (페이지 unload 중에도 전송 보장)
//   const data = JSON.stringify({ scrollDepth: maxScrollDepth });
//   navigator.sendBeacon('/api/behavior/end', data);
// });
// ```
//
export const endReading = async (scrollDepth: number) => {
    const response = await api.post('/api/behavior/end', {
        scrollDepth
    });
    return response.data;
};
