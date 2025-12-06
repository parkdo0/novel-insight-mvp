import api from './axiosConfig';

//
// # 대시보드 API
//
// 작가 대시보드 관련 모든 API 호출을 관리합니다.
//
// ## 주요 기능
//
// 1. 작가 대시보드 조회
// 2. 트렌드 분석 (최근 N일)
// 3. 베스트/워스트 에피소드 조회
// 4. 에피소드 이탈 지점 분석
// 5. 실시간 통계 (현재 읽는 사용자 수, 조회수)
//

//
// ## Type Definitions
//

export interface AuthorDashboardDto {
    authorId: number;
    novelId: number | null;
    aggregatedDate: string;  // YYYY-MM-DD
    periodType: 'DAILY' | 'WEEKLY' | 'MONTHLY';
    totalViews: number;
    uniqueReaders: number;
    avgReadingTimeMinutes: number;
    completionRate: number;  // 0-100
    totalReviews: number;
    positiveRate: number;  // 0-100
    negativeRate: number;  // 0-100
    insightScore: number;  // 0-100
    topEpisodes: string | null;  // JSON string
    dropOffEpisodes: string | null;  // JSON string
}

export interface EpisodeStatsDto {
    episodeId: number;
    completionRate: number;  // 0-100
    totalReaders: number;
    completedReaders: number;
    avgScrollDepth: number;  // 0-100
}

export interface DropOffDetailDto {
    episodeId: number;
    dropOffMap: Record<string, number>;  // "0-10%": 50, "10-20%": 120, ...
    highestDropOffRange: string;
    recommendation: string;
}

export interface CurrentReadersDto {
    episodeId: number;
    currentReaders: number;
}

export interface ViewsDto {
    episodeId: number;
    totalViews: number;
}

//
// ## API Functions
//

//
// 작가 대시보드 조회
//
// ## 사용 예시
//
// ```typescript
// // 오늘 일간 통계
// const dashboard = await getAuthorDashboard(10);
//
// // 특정 날짜 조회
// const dashboard = await getAuthorDashboard(10, '2025-12-01');
//
// // 주간 통계
// const dashboard = await getAuthorDashboard(10, '2025-12-01', 'WEEKLY');
// ```
//
export const getAuthorDashboard = async (
    authorId: number,
    date?: string,  // YYYY-MM-DD
    periodType: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY'
): Promise<AuthorDashboardDto> => {
    const params: Record<string, string> = { periodType };
    if (date) params.date = date;

    const response = await api.get(`/api/dashboard/author/${authorId}`, { params });
    return response.data;
};

//
// 소설별 대시보드 조회
//
export const getNovelDashboard = async (
    novelId: number,
    date?: string,
    periodType: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY'
): Promise<AuthorDashboardDto> => {
    const params: Record<string, string> = { periodType };
    if (date) params.date = date;

    const response = await api.get(`/api/dashboard/novels/${novelId}`, { params });
    return response.data;
};

//
// 작가의 모든 소설 비교
//
// ## 반환값
//
// ```typescript
// [
//   {
//     novelId: 456,
//     totalViews: 10000,
//     completionRate: 75.0,
//     ...
//   }
// ]
// ```
//
export const compareNovels = async (
    authorId: number,
    date?: string,
    periodType: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY'
): Promise<AuthorDashboardDto[]> => {
    const params: Record<string, string> = { periodType };
    if (date) params.date = date;

    const response = await api.get(`/api/dashboard/author/${authorId}/novels`, { params });
    return response.data;
};

//
// 트렌드 분석 조회 (최근 N일)
//
// ## 사용 예시
//
// ```typescript
// // 최근 30일 트렌드
// const trend = await getTrend(10);
//
// // 최근 7일 트렌드
// const trend = await getTrend(10, 7);
//
// // 최근 12주 트렌드
// const trend = await getTrend(10, 12, 'WEEKLY');
// ```
//
export const getTrend = async (
    authorId: number,
    limit: number = 30,
    periodType: 'DAILY' | 'WEEKLY' | 'MONTHLY' = 'DAILY'
): Promise<AuthorDashboardDto[]> => {
    const response = await api.get(`/api/dashboard/author/${authorId}/trend`, {
        params: { limit, periodType }
    });
    return response.data;
};

//
// 베스트 에피소드 조회 (완독률 높은 순)
//
// ## 사용 예시
//
// ```typescript
// // TOP 10 조회
// const best = await getBestEpisodes(456);
//
// // TOP 5만 조회
// const best = await getBestEpisodes(456, 5);
// ```
//
export const getBestEpisodes = async (
    novelId: number,
    limit: number = 10,
    date?: string
): Promise<EpisodeStatsDto[]> => {
    const params: Record<string, any> = { limit };
    if (date) params.date = date;

    const response = await api.get(`/api/dashboard/novels/${novelId}/episodes/best`, { params });
    return response.data;
};

//
// 워스트 에피소드 조회 (완독률 낮은 순)
//
export const getWorstEpisodes = async (
    novelId: number,
    limit: number = 10,
    date?: string
): Promise<EpisodeStatsDto[]> => {
    const params: Record<string, any> = { limit };
    if (date) params.date = date;

    const response = await api.get(`/api/dashboard/novels/${novelId}/episodes/worst`, { params });
    return response.data;
};

//
// 에피소드 이탈 지점 상세 조회
//
// ## 반환값
//
// ```typescript
// {
//   episodeId: 789,
//   dropOffMap: {
//     "0-10%": 50,
//     "10-20%": 120,
//     "20-30%": 200,  // 이탈 급증 구간
//     ...
//   },
//   highestDropOffRange: "20-30%",
//   recommendation: "에피소드 중반부를 검토하세요..."
// }
// ```
//
export const getDropOffDetail = async (
    episodeId: number,
    date?: string
): Promise<DropOffDetailDto> => {
    const params: Record<string, string> = {};
    if (date) params.date = date;

    const response = await api.get(`/api/dashboard/episodes/${episodeId}/drop-off`, { params });
    return response.data;
};

//
// 현재 읽는 사용자 수 조회 (실시간)
//
// ## 사용 예시
//
// ```typescript
// // 5초마다 갱신
// setInterval(async () => {
//   const data = await getCurrentReaders(789);
//   console.log(`현재 ${data.currentReaders}명이 읽는 중`);
// }, 5000);
// ```
//
export const getCurrentReaders = async (
    episodeId: number
): Promise<CurrentReadersDto> => {
    const response = await api.get(`/api/dashboard/episodes/${episodeId}/current-readers`);
    return response.data;
};

//
// 에피소드 누적 조회수 조회 (실시간)
//
export const getTotalViews = async (
    episodeId: number
): Promise<ViewsDto> => {
    const response = await api.get(`/api/dashboard/episodes/${episodeId}/views`);
    return response.data;
};
