import { useEffect, useState } from 'react';
import RollCard from './RollCard';

const RUN_MS = 900;
const seen = new Set(); // ids já exibidos nesta sessão: nunca animam de novo
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** Mostra uma fase "rolando" (d20 girando) antes do RollCard. `fresh` = rolagem que chegou com a página aberta. */
export default function RollReveal({ roll, fresh, onReveal }) {
  const [rolling, setRolling] = useState(() => !!fresh && !seen.has(roll.id) && !reduced());
  const [face, setFace] = useState(20);

  useEffect(() => {
    seen.add(roll.id);
    if (!rolling) return undefined;
    const tick = setInterval(() => setFace(1 + Math.floor(Math.random() * 20)), 70);
    const done = setTimeout(() => setRolling(false), RUN_MS);
    return () => { clearInterval(tick); clearTimeout(done); };
  }, [roll.id, rolling]);

  if (!rolling) return <RollCard roll={roll} onReveal={onReveal} />;
  return (
    <div className="relative" aria-live="polite" aria-label="Rolando dados">
      {/* Card real (sem crítico/falha) invisível: mantém a altura estável. */}
      <div className="invisible" aria-hidden="true">
        <RollCard roll={{ ...roll, critical: false, fumble: false }} />
      </div>
      <div className="panel absolute inset-0 flex items-center justify-center">
        <svg viewBox="0 0 100 100" className="dice-rolling h-16 w-16" aria-hidden="true">
          <polygon points="50,4 93,28 93,72 50,96 7,72 7,28" fill="var(--paper)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
          <path d="M50 4 L7 72 M50 4 L93 72 M7 28 L93 28 M7 72 L93 72" stroke="var(--ink)" strokeWidth="2" fill="none" opacity="0.4" />
          <text x="50" y="62" textAnchor="middle" fontSize="30" fontWeight="900" fill="var(--ink)">{face}</text>
        </svg>
      </div>
    </div>
  );
}
