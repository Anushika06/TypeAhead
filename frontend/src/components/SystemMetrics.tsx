import React, { useState, useEffect } from 'react';
import { GlassCard } from './GlassCard';
import { cn } from './GlassCard';
import {
  Activity, Zap, DatabaseZap, Pencil, Clock, ArrowUpRight, ArrowDownRight,
} from 'lucide-react';

interface Metric {
  label: string;
  value: string;
  sub: string;
  icon: React.ReactNode;
  delta: string;
  up: boolean;
  accent: string;     // tailwind bg color for the icon ring
  iconColor: string;
}

const METRICS: Metric[] = [
  {
    label: 'Cache Hit Rate',
    value: '92.4%',
    sub: 'of requests served from cache',
    icon: <Zap className="w-4 h-4" />,
    delta: '+1.2 pp',
    up: true,
    accent: 'bg-amber-500/15 ring-amber-500/25',
    iconColor: 'text-amber-400',
  },
  {
    label: 'Cache Miss Rate',
    value: '7.6%',
    sub: 'cache bypass → DB fallback',
    icon: <ArrowDownRight className="w-4 h-4" />,
    delta: '-1.2 pp',
    up: false,
    accent: 'bg-rose-500/15 ring-rose-500/25',
    iconColor: 'text-rose-400',
  },
  {
    label: 'DB Reads / sec',
    value: '1,248',
    sub: 'avg over last 5 min',
    icon: <DatabaseZap className="w-4 h-4" />,
    delta: '+14%',
    up: true,
    accent: 'bg-sky-500/15 ring-sky-500/25',
    iconColor: 'text-sky-400',
  },
  {
    label: 'DB Writes / sec',
    value: '86',
    sub: 'trie + index updates',
    icon: <Pencil className="w-4 h-4" />,
    delta: 'Stable',
    up: true,
    accent: 'bg-emerald-500/15 ring-emerald-500/25',
    iconColor: 'text-emerald-400',
  },
  {
    label: 'Avg Latency',
    value: '14 ms',
    sub: 'p95 end-to-end',
    icon: <Clock className="w-4 h-4" />,
    delta: '-2 ms',
    up: true,
    accent: 'bg-indigo-500/15 ring-indigo-500/25',
    iconColor: 'text-indigo-400',
  },
];

export const SystemMetrics: React.FC = () => {
  const [ready, setReady] = useState(false);
  useEffect(() => { const t = setTimeout(() => setReady(true), 80); return () => clearTimeout(t); }, []);

  return (
    <GlassCard className="flex-1">
      {/* ── Header ── */}
      <div className="flex items-center gap-3 mb-5">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-emerald-500/15 ring-1 ring-emerald-500/20 shrink-0">
          <Activity className="w-4 h-4 text-emerald-400" />
        </div>
        <div>
          <h2 className="text-sm font-semibold text-slate-200 leading-none">System Metrics</h2>
          <p className="text-xs text-slate-600 mt-0.5">Real-time dashboard</p>
        </div>
        <span className="ml-auto badge bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20 text-[10px]">
          Mock
        </span>
      </div>

      {/* ── Metric grid ── */}
      <div className="space-y-2">
        {METRICS.map((m, i) => (
          <div
            key={m.label}
            className={cn(
              'group flex items-center gap-4 px-4 py-3 rounded-xl',
              'border border-transparent',
              'transition-all duration-200',
              'hover:bg-white/[0.03] hover:border-white/[0.05]',
              ready ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'
            )}
            style={{ transitionDelay: `${i * 50}ms` }}
          >
            {/* Icon */}
            <div className={cn('flex items-center justify-center w-8 h-8 rounded-lg ring-1 shrink-0', m.accent, m.iconColor)}>
              {m.icon}
            </div>

            {/* Label + sub */}
            <div className="flex-1 min-w-0">
              <p className="text-xs text-slate-500 font-medium truncate">{m.label}</p>
              <p className="text-[10px] text-slate-700 truncate hidden group-hover:block">{m.sub}</p>
            </div>

            {/* Value */}
            <span className="text-sm font-semibold text-slate-200 font-mono shrink-0">
              {m.value}
            </span>

            {/* Delta chip */}
            <span className={cn(
              'badge shrink-0 text-[10px] ring-1',
              m.up
                ? 'bg-emerald-500/10 text-emerald-400 ring-emerald-500/20'
                : 'bg-rose-500/10 text-rose-400 ring-rose-500/20'
            )}>
              {m.up
                ? <ArrowUpRight className="w-2.5 h-2.5" />
                : <ArrowDownRight className="w-2.5 h-2.5" />
              }
              {m.delta}
            </span>
          </div>
        ))}
      </div>
    </GlassCard>
  );
};
