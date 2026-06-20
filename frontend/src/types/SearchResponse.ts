import type { Suggestion } from './Suggestion';

export interface SearchResponse {
  suggestions: Suggestion[];
  timeTakenMs: number;
}
