import axios from 'axios';
import type { Suggestion } from '../types/Suggestion';

const API_BASE_URL = 'http://localhost:8080/suggest';

export const suggestionApi = {
  getSuggestions: async (query: string, signal?: AbortSignal): Promise<Suggestion[]> => {
    try {
      const response = await axios.get<Suggestion[]>(API_BASE_URL, {
        params: { q: query },
        signal,
      });
      return response.data;
    } catch (error) {
      if (axios.isCancel(error)) {
        throw error; // Let the caller handle cancellation
      }
      console.error('Failed to fetch suggestions:', error);
      return [];
    }
  },
};
