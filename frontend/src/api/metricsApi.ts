import axios from 'axios';

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

const BASE = 'http://localhost:8080';

export const metricsApi = {
  getMetrics: async (): Promise<MetricsData> => {
    const response = await axios.get<MetricsData>(`${BASE}/metrics`);
    return response.data;
  },
};
