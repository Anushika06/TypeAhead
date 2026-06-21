import React, { useState, useEffect, useRef } from 'react';
import { Search, X, Loader2 } from 'lucide-react';
import { useDebounce } from '../hooks/useDebounce';
import { suggestionApi } from '../api/suggestionApi';
import type { Suggestion } from '../types/Suggestion';
import { SuggestionDropdown } from './SuggestionDropdown';
import { cn } from './GlassCard';

export const SearchBox: React.FC = () => {
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [isFetching, setIsFetching] = useState(false);
  const [isFocused, setIsFocused] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);

  const debouncedQuery = useDebounce(query, 300);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const isTyping = query !== debouncedQuery && query.length >= 3;
  const isLoading = isTyping || isFetching;

  useEffect(() => {
    if (debouncedQuery.length < 3) {
      setSuggestions([]);
      setIsFetching(false);
      return;
    }

    const controller = new AbortController();
    let active = true;

    const fetch = async () => {
      setIsFetching(true);
      try {
        const results = await suggestionApi.getSuggestions(debouncedQuery, controller.signal);
        if (active) {
          setSuggestions(results.slice(0, 10));
          setSelectedIndex(-1);
          setIsFetching(false);
        }
      } catch (error) {
        if (active) {
          setIsFetching(false);
        }
      }
    };
    fetch();

    return () => {
      active = false;
      controller.abort();
    };
  }, [debouncedQuery]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsFocused(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleClear = () => {
    setQuery('');
    setSuggestions([]);
    setSelectedIndex(-1);
    setIsFetching(false);
    inputRef.current?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setIsFocused(true);
      setSelectedIndex(p => Math.min(p + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setIsFocused(true);
      setSelectedIndex(p => Math.max(p - 1, -1));
    } else if (e.key === 'Enter' && selectedIndex >= 0) {
      e.preventDefault();
      const s = suggestions[selectedIndex];
      if (s) {
        setQuery(s.query);
        setSuggestions([]);
        setSelectedIndex(-1);
        setIsFocused(false);
      }
    } else if (e.key === 'Escape') {
      setIsFocused(false);
    }
  };

  const dropdownVisible = isFocused && query.length > 0 && (isLoading || suggestions.length > 0 || query.length >= 3);

  return (
    <div className="relative w-full z-40" ref={containerRef}>


      <div
        className={cn(
          'glass-input relative flex items-center w-full rounded-2xl transition-all duration-200',
          isFocused ? 'focus-ring-sky' : 'hover:border-white/[0.10]'
        )}
      >

        <div className="absolute left-5 pointer-events-none">
          {isLoading && query.length >= 3
            ? <Loader2 className="w-5 h-5 text-sky-400 animate-spin" />
            : <Search className={cn('w-5 h-5 transition-colors duration-200', isFocused ? 'text-sky-400' : 'text-slate-500')} />
          }
        </div>

        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={e => { 
            const val = e.target.value;
            setQuery(val); 
            setSelectedIndex(-1);
            setIsFocused(true);
            if (val.length < 3) setSuggestions([]);
          }}
          onFocus={() => setIsFocused(true)}
          onKeyDown={handleKeyDown}
          placeholder="Search anything..."
          className="
            w-full bg-transparent border-none outline-none
            py-5 pl-14 pr-12
            text-[1.05rem] text-slate-100 placeholder-slate-600
            font-normal tracking-[-0.01em]
          "
          autoComplete="off"
          spellCheck={false}
          aria-label="Search"
          aria-autocomplete="list"
          aria-expanded={dropdownVisible}
        />


        {query && (
          <button
            onClick={handleClear}
            className="
              absolute right-5 p-1 rounded-lg
              text-slate-600 hover:text-slate-400 hover:bg-white/[0.05]
              transition-all duration-150
            "
            aria-label="Clear search"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>


      {!isFocused && !query && (
        <div className="absolute right-5 top-1/2 -translate-y-1/2 pointer-events-none">
          <kbd className="hidden md:inline-flex items-center gap-1 px-2 py-1 rounded-md bg-white/[0.04] border border-white/[0.07] text-slate-600 text-[11px] font-mono">
            ⌘K
          </kbd>
        </div>
      )}


      <SuggestionDropdown
        suggestions={suggestions}
        isLoading={isLoading}
        isVisible={dropdownVisible}
        query={query}
        selectedIndex={selectedIndex}
        onSelect={s => {
          setQuery(s.query);
          setSuggestions([]);
          setSelectedIndex(-1);
          setIsFocused(false);
        }}
      />
    </div>
  );
};
