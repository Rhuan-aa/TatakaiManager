import { useState } from 'react';

// "Efeitos reduzidos" por usuário (neste navegador): <html data-fx="reduced"> desliga as animações de condição
// e as onomatopeias. prefers-reduced-motion do sistema também vale.
const KEY = 'fx.reduced';
const root = () => document.documentElement;

export function initFxPref() {
  try { if (localStorage.getItem(KEY) === '1') root().dataset.fx = 'reduced'; } catch { /* sem storage */ }
}

export function fxReduced() {
  return root().dataset.fx === 'reduced' || !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

export function useFxPref() {
  const [reduced, setReduced] = useState(() => root().dataset.fx === 'reduced');
  function toggle() {
    const v = !reduced;
    setReduced(v);
    if (v) root().dataset.fx = 'reduced';
    else delete root().dataset.fx;
    try { localStorage.setItem(KEY, v ? '1' : '0'); } catch { /* sem storage */ }
  }
  return [reduced, toggle];
}
