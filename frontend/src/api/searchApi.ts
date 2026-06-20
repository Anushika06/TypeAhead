import type { SearchResponse } from '../types/SearchResponse';
// Placeholder for future use

export const searchApi = {
  performSearch: async (query: string): Promise<SearchResponse> => {
    // UI shell placeholder
    console.log(`Executing full search for: ${query}`);
    return {
      suggestions: [],
      timeTakenMs: 0
    };
  }
};
