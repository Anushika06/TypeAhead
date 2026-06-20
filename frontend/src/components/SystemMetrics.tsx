import React, { useState, useEffect, useCallback } from 'react';
import { GlassCard, cn } from './GlassCard';
import {
  Activity, Zap, DatabaseZap, Pencil,
  RefreshCw, AlertCircle, ArrowDownRight,
  Radio, Send, Layers,
} from 'lucide-react';
import { metricsApi, type MetricsData } from '../api/metricsApi';

// ── Polling interval ────────────────────────────────────────────────────────
const POLL_INTERVAL_MS = 10_000;

// ── Number formatter ────────────────────────────────────────────────────────
function fmt(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)}K`;
  return String(n);
}

// ── Skeleton card ───────────────────────────────────────────────────────────
const SkeletonCard: React.FC = () => (
  <div className="flex items-center gap-4 px-4 py-3 rounded-xl">
    <div className="skeleton w-8 h-8 rounded-lg shrink-0" />
    <div className="flex-1 space-y-1.5">
      <div className="skeleton h-2.5 rounded w-2/5" />
      <div className="skeleton h-2 rounded w-3/5" />
    </div>
    <div className="skeleton h-4 rounded w-14 shrink-0" />
  </div>
);

// ── Metric row shape ────────────────────────────────────────────────────────
interface MetricRow {
  label:     string;
  value:     string;
  sub:       string;
  icon:      React.ReactNode;
  accent:    string;
  iconColor: string;
}

function buildMetricRows(d: MetricsData): MetricRow[] {
  return [
    {
      label:     'Cache Hit Rate',
      value:     `${d.cacheHitRate.toFixed(1)}%`,
      sub:       'requests served from Redis',
      icon:      <Zap className="w-4 h-4" />,
      accent:    'bg-amber-500/15 ring-amber-500/25',
      iconColor: 'text-amber-400',
    },
    {
      label:     'Cache Hits',
      value:     fmt(d.cacheHits),
      sub:       'total Redis hits this session',
      icon:      <Activity className="w-4 h-4" />,
      accent:    'bg-emerald-500/15 ring-emerald-500/25',
      iconColor: 'text-emerald-400',
    },
    {
      label:     'Cache Misses',
      value:     fmt(d.cacheMisses),
      sub:       'cache bypass → DB fallback',
      icon:      <ArrowDownRight className="w-4 h-4" />,
      accent:    'bg-rose-500/15 ring-rose-500/25',
      iconColor: 'text-rose-400',
    },
    {
      label:     'DB Reads',
      value:     fmt(d.dbReads),
      sub:       'PostgreSQL SELECT executions',
      icon:      <DatabaseZap className="w-4 h-4" />,
      accent:    'bg-sky-500/15 ring-sky-500/25',
      iconColor: 'text-sky-400',
    },
    {
      label:     'DB Writes',
      value:     fmt(d.dbWrites),
      sub:       'UPSERT rows flushed to Postgres',
      icon:      <Pencil className="w-4 h-4" />,
      accent:    'bg-violet-500/15 ring-violet-500/25',
      iconColor: 'text-violet-400',
    },
    {
      label:     'Stream Events Published',
      value:     fmt(d.streamEventsPublished),
      sub:       'search events → Redis Stream',
      icon:      <Send className="w-4 h-4" />,
      accent:    'bg-indigo-500/15 ring-indigo-500/25',
      iconColor: 'text-indigo-400',
    },
    {
      label:     'Stream Events Consumed',
      value:     fmt(d.streamEventsConsumed),
      sub:       'events read by the aggregator',
      icon:      <Radio className="w-4 h-4" />,
      accent:    'bg-cyan-500/15 ring-cyan-500/25',
      iconColor: 'text-cyan-400',
    },
    {
      label:     'Batch Flushes',
      value:     fmt(d.batchFlushes),
      sub:       `avg size · ${fmt(d.avgFlushSize)} events/flush`,
      icon:      <Layers className="w-4 h-4" />,
      accent:    'bg-orange-500/15 ring-orange-500/25',
      iconColor: 'text-orange-400',
    },
  ];
}

// ── Component ───────────────────────────────────────────────────────────────
export const SystemMetrics: React.FC = () => {
  const [data,      setData]      = useState<MetricsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error,     setError]     = useState<string | null>(null);

  const fetchMetrics = useCallback(async () => {
    setError(null);
    try {
      const d = await metricsApi.getMetrics();
      setData(d);
    } catch {
      setError('Unable to load metrics');
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Initial load + polling every 10 s
  useEffect(() => {
    fetchMetrics();
    const id = setInterval(fetchMetrics, POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [fetchMetrics]);

  const rows = data ? buildMetricRows(data) : [];

  return (
    <GlassCard className="flex-1">

      {/* ── Header ── */}
      <div className="flex items-center gap-3 mb-5">
        <div className="flex items-center justify-center w-8 h-8 rounded-lg bg-emerald-500/15 ring-1 ring-emerald-500/20 shrink-0">
          <Activity className="w-4 h-4 text-emerald-400" />
        </div>

        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-slate-200 leading-none">System Metrics</h2>
          <p className="text-[11px] text-slate-600 mt-0.5">Refreshes every 10 s</p>
        </div>

        {/* Real-time badge */}
        <span className="ml-auto flex items-center gap-1.5 badge bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20 text-[10px] shrink-0">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
          Real-time
        </span>
      </div>

      {/* ── Loading skeletons ── */}
      {isLoading && (
        <div className="space-y-1">
          {[...Array(8)].map((_, i) => <SkeletonCard key={i} />)}
        </div>
      )}

      {/* ── Error state ── */}
      {!isLoading && error && (
        <div className="flex flex-col items-center justify-center py-10 gap-3 text-center">
          <div className="flex items-center justify-center w-10 h-10 rounded-full bg-rose-500/10 ring-1 ring-rose-500/20">
            <AlertCircle className="w-5 h-5 text-rose-400" />
          </div>
          <div>
            <p className="text-sm text-slate-400 font-medium">{error}</p>
            <p className="text-xs text-slate-600 mt-0.5">Check that the backend is running</p>
          </div>
          <button
            onClick={fetchMetrics}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 bg-white/[0.04] hover:bg-white/[0.07] ring-1 ring-white/[0.08] transition-all duration-150"
          >
            <RefreshCw className="w-3 h-3" />
            Retry
          </button>
        </div>
      )}

      {/* ── Metric rows ── */}
      {!isLoading && !error && rows.length > 0 && (
        <div className="space-y-1">
          {rows.map((m, i) => (
            <div
              key={m.label}
              className={cn(
                'group flex items-center gap-4 px-4 py-3 rounded-xl',
                'border border-transparent',
                'transition-all duration-200',
                'hover:bg-white/[0.03] hover:border-white/[0.05]',
                'animate-slide-up',
              )}
              style={{ animationDelay: `${i * 35}ms`, animationFillMode: 'both' }}
            >
              {/* Icon */}
              <div className={cn(
                'flex items-center justify-center w-8 h-8 rounded-lg ring-1 shrink-0',
                m.accent,
                m.iconColor,
              )}>
                {m.icon}
              </div>

              {/* Label + sub-label */}
              <div className="flex-1 min-w-0">
                <p className="text-xs text-slate-500 font-medium leading-none truncate">
                  {m.label}
                </p>
                <p className="text-[10px] text-slate-700 mt-1 truncate leading-none">
                  {m.sub}
                </p>
              </div>

              {/* Value */}
              <span className="text-sm font-semibold text-slate-200 font-mono tabular-nums shrink-0">
                {m.value}
              </span>
            </div>
          ))}
        </div>
      )}
    </GlassCard>
  );
};
