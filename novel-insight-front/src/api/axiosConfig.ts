import axios from 'axios';

// 도커 내부 통신용(백엔드) 주소
const BASE_URL = 'http://localhost:8080';

const api = axios.create({
    baseURL: BASE_URL,
    headers: { 'Content-Type': 'application/json' }
});

// 요청 채기 (Interceptor): 토큰이 있으면 헤더에 실어 보낸다.
api.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export default api;