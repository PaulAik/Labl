import { useState, useEffect } from 'react';
import { fetchLabels } from '../api.js';

/** Polls the pending label count every 15 s for the AppBar badge. */
export function useLabelsCount() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function refresh() {
      try {
        const labels = await fetchLabels('pending');
        if (!cancelled) setCount(labels.length);
      } catch { /* silent */ }
    }
    refresh();
    const id = setInterval(refresh, 15_000);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  return count;
}
