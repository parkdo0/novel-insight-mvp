import api from './axiosConfig';

export const createReview = async (novelId: number, content: string) => {
    const response = await api.post('/reviews', { novelId, content });
    return response.data;
};