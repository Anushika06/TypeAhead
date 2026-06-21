import type { SearchResponse } from '../types/SearchResponse';


export const searchApi = {
  performSearch: async (query: string): Promise<SearchResponse> => {

    console.log(`Executing full search for: ${query}`);
    return {
      suggestions: [],
      timeTakenMs: 0
    };
  }
};
