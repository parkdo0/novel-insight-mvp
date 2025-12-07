import api from './axiosConfig';

// 포인트 충전
export const chargePoint = async (amount: number) => {
    const response = await api.post('/api/payments/charge', { amount });
    return response.data;
};

// 내역 조회
export interface PointHistory {
    id: number;
    type: 'CHARGE' | 'USE';
    amount: number;
    description: string;
    createdAt: string;
}

export const getMyHistory = async () => {
    const response = await api.get('/users/me/history');
    return response.data as PointHistory[];
};