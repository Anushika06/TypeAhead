import React, { useState, useEffect } from 'react';
import { GlassCard } from './GlassCard';
import { cn } from './GlassCard';
import { TrendingUp, Flame } from 'lucide-react';

const TRENDING = [
  { rank: 1, term: 'google',   volume: '98 K',  delta: '+4.2%' },
  { rank: 2, term: 'yahoo',    volume: '85 K',  delta: '+1.8%' },
  { rank: 3, term: 'ebay',     volume: '72 K',  delta: '+0.9%' },
  { rank: 4, term: 'amazon',   volume: '64 K',  delta: '-0.3%' },
  { rank: 5, term: 'netflix',  volume: '59 K',  delta: '+2.1%' },
];

const rankBadgeClass = (rank: number) =>
  rank === 1 ? 'bg-amber-400/20  text-amber-300  ring-amber-400/30' :
  rank === 2 ? 'bg-slate-300/15  text-slate-300  ring-slate-400/20' :
  rank === 3 ? 'bg-orange-400/20 text-orange-300 ring-orange-400/30' :
               'bg-white/[0.04]  text-slate-500  ring-white/[0.06]';

export const TrendingSearches: React.FC = () => {
  const [ready, setReady] = useState(false);
  useEffect(() => { const t = setTimeout(() => setReady(true), 60); return () => clearTimeout(t); }, []);

  return (
    <GlassCard className="flex-1 overflow-hidden">
      {/* ── Header ── */}
      <div className="flex items-center gap-3 mb-5">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-indigo-500/15 ring-1 ring-indigo-500/20 shrink-0">
          <TrendingUp className="w-4 h-4 text-indigo-400" />
        </div>
        <div>
          <h2 className="text-sm font-semibold text-slate-200 leading-none">Trending Searches</h2>
          <p className="text-xs text-slate-600 mt-0.5">Last 24 hours</p>
        </div>
        <span className="ml-auto badge bg-indigo-500/10 text-indigo-400 ring-1 ring-indigo-500/20 text-[10px]">
          Mock
        </span>
      </div>

      {/* ── List ── */}
      <ul className="space-y-1">
        {TRENDING.map((item, i) => {
          const up = item.delta.startsWith('+');
          return (
            <li
              key={item.rank}
              className={cn(
                'group flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer',
                'transition-all duration-200',
                'hover:bg-white/[0.04]',
                ready ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'
              )}
              style={{ transitionDelay: `${i * 50}ms` }}
            >
              {/* Rank badge */}
              <span className={cn(
                'flex items-center justify-center w-7 h-7 rounded-lg text-[11px] font-bold shrink-0 ring-1',
                rankBadgeClass(item.rank)
              )}>
                {item.rank}
              </span>

              {/* Term */}
              <span className="flex-1 text-sm text-slate-300 capitalize group-hover:text-slate-100 transition-colors font-medium">
                {item.term}
              </span>

              {/* Hot flame for top 3 */}
              {item.rank <= 3 && (
                <Flame className={cn(
                  'w-3.5 h-3.5 shrink-0',
                  item.rank === 1 ? 'text-amber-400' : 'text-slate-700'
                )} />
              )}

              {/* Volume + delta */}
              <div className="text-right shrink-0">
                <div className="text-xs text-slate-400 font-mono">{item.volume}</div>
                <div className={cn('text-[10px] font-medium', up ? 'text-emerald-500' : 'text-rose-500')}>
                  {item.delta}
                </div>
              </div>
            </li>
          );
        })}
      </ul>
    </GlassCard>
  );
};
