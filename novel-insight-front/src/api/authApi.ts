import api from './axiosConfig';

export const loginUser = async (email: string, pass: string) => {
    const response = await api.post('/login', { email, password: pass });
    return response.data;
};

export const signupUser = async (email: string, pass: string, nickname: string) => {
    const response = await api.post('/signup', {
        email,
        password: pass,
        nickname
    });
    return response.data;
};

// ★ [추가] 내 정보 가져오기
export const getMyInfo = async () => {
    const response = await api.get('/users/me');
    return response.data; // { email, nickname, point }
};