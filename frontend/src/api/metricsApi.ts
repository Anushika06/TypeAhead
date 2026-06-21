import axios from 'axios';
import { API_BASE_URL } from './apiClient';

export interface MetricsData {
  cacheHits:             number;
  cacheMisses:           number;
  cacheHitRate:          number;
  dbReads:               number;
  dbWrites:              number;
  streamEventsPublished: number;
  streamEventsConsumed:  number;
  batchFlushCount:       number;
  avgFlushSize:          number;
}

const BASE = API_BASE_URL;

export const metricsApi = {
  getMetrics: async (): Promise<MetricsData> => {
    const response = await axios.get<MetricsData>(`${BASE}/metrics`);
    return response.data;
  },
};
