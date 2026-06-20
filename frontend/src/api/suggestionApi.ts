import axios from 'axios';
import type { Suggestion } from '../types/Suggestion';

const API_BASE_URL = 'http://localhost:8080/suggest';

export const suggestionApi = {
  getSuggestions: async (query: string): Promise<Suggestion[]> => {
    try {
      const response = await axios.get<Suggestion[]>(API_BASE_URL, {
        params: { q: query },
      });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch suggestions:', error);
      return [];
    }
  },
};
