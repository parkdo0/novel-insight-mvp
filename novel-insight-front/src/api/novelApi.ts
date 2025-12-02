import api from './axiosConfig';

export interface Review {
    id: number;
    content: string;
}

export interface Comment {
    id: number;
    content: string;
    writerName: string;
    likeCount: number;
    dislikeCount: number;
    isMine: boolean;
}

export interface Episode {
    id: number;
    title: string;
    content: string;
    price: number;
    isFree: boolean;
    isPurchased: boolean;
    viewCount: number;
    purchaseCount: number;
}

export interface Novel {
    id: number;
    title: string;
    author: string;
    description: string;
    viewCount: number;
    reviews: Review[];
    episodes?: Episode[];
    authorId: number;
}

// --- API 함수들 ---

export const getNovels = async (page = 0, size = 10) => {
    const response = await api.get(`/novels/search?page=${page}&size=${size}`);
    return response.data;
};

export const getNovelDetail = async (id: number) => {
    const response = await api.get(`/novels/${id}`);
    return response.data;
};

export const getEpisodes = async (novelId: number) => {
    const response = await api.get(`/novels/${novelId}/episodes`);
    return response.data;
};

// ★ [신규] 회차 내용 읽기 (백엔드에서 조회수 증가 트랜잭션 실행됨)
export const readEpisode = async (episodeId: number) => {
    const response = await api.get(`/episodes/${episodeId}`);
    return response.data; // 최신 조회수가 반영된 에피소드 정보 반환
};

export const purchaseEpisode = async (episodeId: number) => {
    const response = await api.post(`/episodes/${episodeId}/purchase`);
    return response.data;
};

export const createEpisode = async (novelId: number, title: string, content: string, isFree: boolean) => {
    const response = await api.post(`/novels/${novelId}/episodes`, { title, content, isFree });
    return response.data;
};

export const getComments = async (episodeId: number) => {
    const response = await api.get(`/episodes/${episodeId}/comments`);
    return response.data as Comment[];
};

export const addComment = async (episodeId: number, content: string) => {
    const response = await api.post(`/episodes/${episodeId}/comments`, { content });
    return response.data;
};

export const toggleLike = async (commentId: number, type: 'LIKE' | 'DISLIKE') => {
    const response = await api.post(`/comments/${commentId}/like?type=${type}`);
    return response.data;
};