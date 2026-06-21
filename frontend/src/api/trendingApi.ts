import axios from 'axios';
import { API_BASE_URL } from './apiClient';

export interface TrendingItem {
  query:      string;
  score:      number;
  totalCount: number;
  trendScore: number;
}

const BASE = API_BASE_URL;

export const trendingApi = {
  getTopTrending: async (): Promise<TrendingItem[]> => {
    const response = await axios.get<TrendingItem[]>(`${BASE}/trending`);
    return response.data;
  },
};
