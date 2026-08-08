import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { PublicShell } from '../components/site-chrome';

// Memory cache by route pathname to make tab switching instant (0ms)
const pageCache = new Map<string, ReactNode>();

export function withAsync<T extends Record<string, any>>(
  ComponentOrAsync: React.ComponentType<T> | ((props: T) => Promise<ReactNode>)
) {
  if (ComponentOrAsync.constructor.name !== 'AsyncFunction') {
    return ComponentOrAsync as React.ComponentType<T>;
  }

  const AsyncComponent = ComponentOrAsync as ((props: T) => Promise<ReactNode>);

  return function Wrapper(props: T) {
    const routeKey = typeof window !== 'undefined'
      ? window.location.pathname + window.location.search
      : JSON.stringify(props);

    const cached = pageCache.get(routeKey);

    const [content, setContent] = useState<ReactNode>(cached ?? null);
    const [error, setError] = useState<Error | null>(null);
    const [loading, setLoading] = useState(!cached);

    useEffect(() => {
      let active = true;
      if (!pageCache.has(routeKey)) {
        setLoading(true);
      }

      AsyncComponent(props)
        .then((result) => {
          if (active) {
            pageCache.set(routeKey, result);
            setContent(result);
            setLoading(false);
          }
        })
        .catch((err) => {
          if (active) {
            setError(err);
            setLoading(false);
          }
        });

      return () => {
        active = false;
      };
    }, [routeKey]);

    const isDashboard = typeof window !== 'undefined' && window.location.pathname.startsWith('/dashboard');

    if (error && !content) {
      const errorBody = (
        <div style={{ padding: '3rem 1.5rem', textAlign: 'center', minHeight: '50vh', fontFamily: 'sans-serif' }}>
          <h2 style={{ fontSize: '1.3rem', color: isDashboard ? '#0f172a' : '#11182b', marginBottom: '0.5rem' }}>Không thể tải nội dung trang</h2>
          <p style={{ color: '#64748b', fontSize: '0.9rem' }}>{error.message || String(error)}</p>
        </div>
      );
      return isDashboard ? errorBody : <PublicShell>{errorBody}</PublicShell>;
    }

    if (loading && !content) {
      const loadingBody = (
        <div className="routeLoading" style={{ padding: isDashboard ? '1.5rem' : '3rem 1.5rem' }}>
          <div className="loadingHero" style={{ height: '3rem', borderRadius: '8px', background: 'rgba(203, 213, 225, 0.4)', animation: 'pulse 1.2s infinite' }}></div>
          <div className="loadingGrid" style={{ marginTop: '1.5rem', display: 'grid', gap: '1rem', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="loadingCard" style={{ height: '7rem', borderRadius: '10px', background: 'rgba(203, 213, 225, 0.3)', animation: 'pulse 1.2s infinite' }}></div>
            ))}
          </div>
        </div>
      );
      return isDashboard ? loadingBody : <PublicShell>{loadingBody}</PublicShell>;
    }

    return content;
  };
}
