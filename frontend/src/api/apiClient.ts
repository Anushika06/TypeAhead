/**
 * Central API base URL.
 *
 * Resolved from the VITE_API_BASE_URL environment variable at build time.
 *
 * Local development  → http://localhost:8080  (via .env)
 * Docker             → http://localhost:8080   (nginx proxies /api, or direct via port mapping)
 *
 * Set VITE_API_BASE_URL in your .env file or as a docker-compose build-arg.
 */
export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string) ?? 'http://localhost:8080';
