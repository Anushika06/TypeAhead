import React from 'react';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  return (
    <div className="min-h-screen px-4 py-16 md:py-24 flex flex-col items-center">

      {/* ── Top noise/grid texture overlay ── */}
      <div
        aria-hidden
        className="pointer-events-none fixed inset-0 z-0"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.015'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E")`,
        }}
      />

      <div className="relative z-10 w-full max-w-4xl flex flex-col items-center">

        {/* ── Header ── */}
        <header className="mb-14 text-center w-full">

          {/* Dataset badge */}
          <div className="mb-6 flex justify-center">
            <span className="badge glass stripe-top text-slate-400">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
              128,810 indexed queries &nbsp;·&nbsp; Live
            </span>
          </div>

          {/* Main title */}
          <h1 className="text-[2.75rem] md:text-6xl font-extrabold tracking-[-0.03em] leading-none mb-4">
            <span className="text-white">TypeAhead</span>
            {' '}
            <span
              style={{
                backgroundImage: 'linear-gradient(135deg, #38bdf8 0%, #818cf8 60%, #c084fc 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              Engine
            </span>
          </h1>

          {/* Subtitle */}
          <p className="text-slate-500 text-base md:text-lg font-normal max-w-xl mx-auto leading-relaxed">
            Production-grade autocomplete backed by{' '}
            <span className="text-slate-400">trie-indexed</span>,{' '}
            <span className="text-slate-400">score-ranked</span>{' '}
            search queries.
          </p>
        </header>

        {/* ── Main content ── */}
        <main className="w-full flex flex-col gap-6">
          {children}
        </main>

        {/* ── Footer ── */}
        <footer className="mt-16 text-center text-slate-700 text-xs">
          TypeAhead Engine &nbsp;·&nbsp; Built with React + Spring Boot
        </footer>
      </div>
    </div>
  );
};
