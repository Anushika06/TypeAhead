import axios from 'axios';
import type { Suggestion } from '../types/Suggestion';
import { API_BASE_URL } from './apiClient';

const SUGGEST_URL = `${API_BASE_URL}/suggest`;

export const suggestionApi = {
  getSuggestions: async (query: string, signal?: AbortSignal): Promise<Suggestion[]> => {
    try {
      const response = await axios.get<Suggestion[]>(SUGGEST_URL, {
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
