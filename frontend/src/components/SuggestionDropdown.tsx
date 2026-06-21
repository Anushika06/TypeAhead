import React from 'react';
import type { Suggestion } from '../types/Suggestion';
import { Search, CornerDownLeft } from 'lucide-react';
import { cn } from './GlassCard';


const SkeletonRow: React.FC = () => (
  <li className="px-4 py-3 flex items-center gap-3">
    <div className="h-4 w-4 skeleton rounded-full shrink-0" />
    <div className="skeleton h-3 rounded" style={{ width: `${52 + Math.random() * 36}%` }} />
    <div className="ml-auto skeleton h-3 w-10 rounded shrink-0" />
  </li>
);

interface SuggestionDropdownProps {
  suggestions: Suggestion[];
  isLoading: boolean;
  isVisible: boolean;
  query: string;
  selectedIndex: number;
  onSelect: (suggestion: Suggestion) => void;
}

export const SuggestionDropdown: React.FC<SuggestionDropdownProps> = ({
  suggestions,
  isLoading,
  isVisible,
  query,
  selectedIndex,
  onSelect,
}) => {
  if (!isVisible) return null;


  const highlight = (text: string) => {
    const lq = query.toLowerCase();
    const idx = text.toLowerCase().indexOf(lq);
    if (idx === -1) return <span>{text}</span>;
    return (
      <>
        {text.slice(0, idx)}
        <span className="text-sky-300 font-semibold">{text.slice(idx, idx + lq.length)}</span>
        {text.slice(idx + lq.length)}
      </>
    );
  };

  return (
    <div
      className="
        absolute top-full left-0 right-0 mt-2 z-50 overflow-hidden
        glass rounded-xl stripe-top
        animate-slide-up origin-top
      "
    >

      {isLoading && (
        <ul className="py-2">
          {[...Array(5)].map((_, i) => <SkeletonRow key={i} />)}
        </ul>
      )}


      {!isLoading && suggestions.length > 0 && (
        <>
          <div className="px-4 pt-3 pb-1.5">
            <p className="text-[11px] font-medium uppercase tracking-widest text-slate-600">
              Suggestions
            </p>
          </div>
          <ul className="max-h-72 overflow-y-auto pb-2">
            {suggestions.map((s, i) => (
              <li
                key={i}
                onClick={() => onSelect(s)}
                className={cn(
                  'group px-4 py-2.5 flex items-center gap-3 cursor-pointer',
                  'transition-colors duration-100',
                  selectedIndex === i
                    ? 'bg-white/[0.06] text-slate-100'
                    : 'text-slate-300 hover:bg-white/[0.04] hover:text-slate-100'
                )}
              >

                <Search
                  className={cn(
                    'w-3.5 h-3.5 shrink-0 transition-colors',
                    selectedIndex === i ? 'text-sky-400' : 'text-slate-600 group-hover:text-slate-500'
                  )}
                />


                <span className="flex-1 text-sm truncate leading-5">
                  {highlight(s.query)}
                </span>


                <span className="text-[11px] text-slate-600 font-mono shrink-0">
                  {s.score.toFixed(1)}
                </span>


                {selectedIndex === i && (
                  <CornerDownLeft className="w-3 h-3 text-slate-600 shrink-0" />
                )}
              </li>
            ))}
          </ul>


          <div className="divider px-4 py-2 flex items-center justify-between">
            <p className="text-[11px] text-slate-700">
              {suggestions.length} result{suggestions.length !== 1 && 's'}
            </p>
            <p className="text-[11px] text-slate-700">↑↓ navigate &nbsp; ↵ select</p>
          </div>
        </>
      )}


      {!isLoading && suggestions.length === 0 && query.length >= 3 && (
        <div className="px-4 py-8 text-center">
          <div className="inline-flex items-center justify-center w-9 h-9 rounded-full bg-white/[0.04] mb-3">
            <Search className="w-4 h-4 text-slate-600" />
          </div>
          <p className="text-sm text-slate-500 font-medium">No suggestions found</p>
          <p className="text-xs text-slate-700 mt-1">Try a different search term</p>
        </div>
      )}
    </div>
  );
};
