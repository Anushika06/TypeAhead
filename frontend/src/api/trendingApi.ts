import axios from 'axios';

export interface TrendingItem {
  query:      string;
  score:      number;
  totalCount: number;
  trendScore: number;
}

const BASE = 'http://localhost:8080';

export const trendingApi = {
  getTopTrending: async (): Promise<TrendingItem[]> => {
    const response = await axios.get<TrendingItem[]>(`${BASE}/trending`);
    return response.data;
  },
};
