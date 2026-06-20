import React, { useState, useEffect, useCallback } from 'react';
import { GlassCard, cn } from './GlassCard';
import { TrendingUp, Flame, RefreshCw, AlertCircle } from 'lucide-react';
import { trendingApi, type TrendingItem } from '../api/trendingApi';

// ── Rank badge colours ──────────────────────────────────────────────────────
const rankBadgeClass = (rank: number) =>
  rank === 1 ? 'bg-amber-400/20  text-amber-300  ring-amber-400/30'  :
  rank === 2 ? 'bg-slate-300/15  text-slate-300  ring-slate-400/20'  :
  rank === 3 ? 'bg-orange-400/20 text-orange-300 ring-orange-400/30' :
               'bg-white/[0.04]  text-slate-500  ring-white/[0.06]';

// ── Skeleton row ────────────────────────────────────────────────────────────
const SkeletonRow: React.FC = () => (
  <li className="flex items-center gap-3 px-3 py-3">
    <div className="skeleton w-7 h-7 rounded-lg shrink-0" />
    <div className="flex-1 space-y-1.5">
      <div className="skeleton h-3 rounded w-2/5" />
      <div className="skeleton h-2.5 rounded w-1/4" />
    </div>
    <div className="skeleton h-3 rounded w-10 shrink-0" />
  </li>
);

// ── Formats a large integer nicely, e.g. 32532 → "32.5 K" ──────────────────
function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)} M`;
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)} K`;
  return String(n);
}

// ── Component ───────────────────────────────────────────────────────────────
const POLL_INTERVAL_MS = 30_000;

export const TrendingSearches: React.FC = () => {
  const [items,     setItems]     = useState<TrendingItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error,     setError]     = useState<string | null>(null);
  const [lastFetch, setLastFetch] = useState(0);     // for "last refreshed" display

  const fetchTrending = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await trendingApi.getTopTrending();
      setItems(data);
      setLastFetch(Date.now());
    } catch {
      setError('Unable to load trending searches');
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Initial load + polling every 30 s
  useEffect(() => {
    fetchTrending();
    const id = setInterval(fetchTrending, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [fetchTrending]);

  // ── last-refreshed relative label ──
  const elapsed = Math.floor((Date.now() - lastFetch) / 1000);
  const refreshLabel = lastFetch === 0 ? '' :
    elapsed < 5  ? 'just now' :
    elapsed < 60 ? `${elapsed}s ago` :
    `${Math.floor(elapsed / 60)}m ago`;

  return (
    <GlassCard className="flex-1 overflow-hidden">

      {/* ── Header ── */}
      <div className="flex items-center gap-3 mb-5">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-indigo-500/15 ring-1 ring-indigo-500/20 shrink-0">
          <TrendingUp className="w-4 h-4 text-indigo-400" />
        </div>

        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-slate-200 leading-none">Trending Searches</h2>
          <p className="text-[11px] text-slate-600 mt-0.5 truncate">
            {refreshLabel ? `Updated ${refreshLabel}` : 'Top 5 by score'}
          </p>
        </div>

        {/* Live badge */}
        <span className="ml-auto flex items-center gap-1.5 badge bg-indigo-500/10 text-indigo-400 ring-1 ring-indigo-500/20 text-[10px] shrink-0">
          <span className="h-1.5 w-1.5 rounded-full bg-indigo-400 animate-pulse" />
          Live
        </span>
      </div>

      {/* ── Loading skeletons ── */}
      {isLoading && (
        <ul className="space-y-0.5">
          {[...Array(5)].map((_, i) => <SkeletonRow key={i} />)}
        </ul>
      )}

      {/* ── Error state ── */}
      {!isLoading && error && (
        <div className="flex flex-col items-center justify-center py-8 gap-3 text-center">
          <div className="flex items-center justify-center w-10 h-10 rounded-full bg-rose-500/10 ring-1 ring-rose-500/20">
            <AlertCircle className="w-5 h-5 text-rose-400" />
          </div>
          <div>
            <p className="text-sm text-slate-400 font-medium">{error}</p>
            <p className="text-xs text-slate-600 mt-0.5">Backend may be unavailable</p>
          </div>
          <button
            onClick={fetchTrending}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 bg-white/[0.04] hover:bg-white/[0.07] ring-1 ring-white/[0.08] transition-all duration-150"
          >
            <RefreshCw className="w-3 h-3" />
            Retry
          </button>
        </div>
      )}

      {/* ── Results list ── */}
      {!isLoading && !error && items.length === 0 && (
        <div className="flex flex-col items-center justify-center py-8 gap-2 text-center">
          <TrendingUp className="w-6 h-6 text-slate-700" />
          <p className="text-sm text-slate-500">No trending data yet</p>
          <p className="text-xs text-slate-700">Submit some searches to populate this</p>
        </div>
      )}

      {!isLoading && !error && items.length > 0 && (
        <ul className="space-y-0.5">
          {items.map((item, i) => {
            const rank = i + 1;
            return (
              <li
                key={item.query}
                className={cn(
                  'group flex items-center gap-3 px-3 py-2.5 rounded-xl',
                  'transition-all duration-200 cursor-default',
                  'hover:bg-white/[0.04]',
                  'animate-slide-up',
                )}
                style={{ animationDelay: `${i * 40}ms`, animationFillMode: 'both' }}
              >
                {/* Rank badge */}
                <span className={cn(
                  'flex items-center justify-center w-7 h-7 rounded-lg text-[11px] font-bold shrink-0 ring-1',
                  rankBadgeClass(rank)
                )}>
                  {rank}
                </span>

                {/* Term + score sub-line */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-slate-300 group-hover:text-slate-100 transition-colors font-medium truncate capitalize">
                    {item.query}
                  </p>
                  <p className="text-[10px] text-slate-600 font-mono mt-0.5">
                    score&nbsp;{item.score.toFixed(2)}
                  </p>
                </div>

                {/* Hot flame for top 3 */}
                {rank <= 3 && (
                  <Flame className={cn(
                    'w-3.5 h-3.5 shrink-0 transition-colors',
                    rank === 1 ? 'text-amber-400' : 'text-slate-700 group-hover:text-slate-600'
                  )} />
                )}

                {/* Volume (totalCount formatted) */}
                <div className="text-right shrink-0">
                  <div className="text-xs text-slate-400 font-mono font-medium">
                    {formatCount(item.totalCount)}
                  </div>
                  <div className="text-[10px] text-slate-700 mt-0.5">searches</div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </GlassCard>
  );
};
