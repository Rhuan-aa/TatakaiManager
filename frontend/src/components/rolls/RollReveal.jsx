import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import RollCard from './RollCard';
import { queueSlot } from './revealQueue';

const ROLL_MS = 900;   // d20 girando no centro
const SHOW_MS = 550;   // número parado no centro
const FLY_MS = 450;    // número voa até o total do card
const REVEAL_MS = ROLL_MS + SHOW_MS + FLY_MS;

const seen = new Set(); // ids já exibidos nesta sessão: nunca animam de novo
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// Silhueta por tipo de dado (viewBox 100×100); d20 e desconhecidos usam o icosaedro.
const SHAPES = {
  4: '50,8 94,88 6,88',
  6: '14,14 86,14 86,86 14,86',
  8: '50,4 92,50 50,96 8,50',
  10: '50,4 90,38 76,92 24,92 10,38',
  12: '50,4 88,30 78,80 50,96 22,80 12,30',
  100: '50,4 90,38 76,92 24,92 10,38',
  20: '50,4 93,28 93,72 50,96 7,72 7,28',
};
const MAX_DICE = 8;

/** Lados de cada dado da fórmula ("2d6+1d4 | dano 1d8" → [6, 6, 4, 8]); sem dados reconhecíveis → [20]. */
export function diceSides(formula) {
  const out = [];
  for (const m of (formula ?? '').matchAll(/(\d+)d(\d+)/g)) {
    for (let i = 0; i < Number(m[1]) && out.length < MAX_DICE; i++) out.push(Number(m[2]));
  }
  return out.length ? out : [20];
}

function Die({ sides, face }) {
  return (
    <svg viewBox="0 0 100 100" className="dice-rolling h-20 w-20" aria-hidden="true">
      <polygon points={SHAPES[sides] ?? SHAPES[20]} fill="var(--paper)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
      <text x="50" y="62" textAnchor="middle" fontSize={sides === 100 ? 24 : 30} fontWeight="900" fill="var(--ink)">{face}</text>
    </svg>
  );
}

/**
 * Revela uma rolagem em duas etapas: o d20 gira no centro da tela, o total aparece grande no centro e
 * depois voa/encolhe até o número do RollCard. `fresh` = rolagem que chegou com a página aberta.
 */
export default function RollReveal({ roll, fresh, onReveal, onAnimated }) {
  // waiting → rolling → result → fly → done
  const [stage, setStage] = useState(() => (fresh && !seen.has(roll.id) && !reduced() ? 'waiting' : 'done'));
  const sides = useMemo(() => diceSides(roll.formula), [roll.formula]);
  const [faces, setFaces] = useState(() => sides.map((n) => n));
  const slot = useRef(null);
  const cardRef = useRef(null);
  const numRef = useRef(null);

  useEffect(() => {
    seen.add(roll.id);
    if (stage !== 'waiting') return undefined;
    slot.current ??= queueSlot(REVEAL_MS);
    const t = setTimeout(() => setStage('rolling'), slot.current);
    return () => clearTimeout(t);
  }, [roll.id, stage]);

  useEffect(() => {
    if (stage === 'rolling') {
      const tick = setInterval(() => setFaces(sides.map((n) => 1 + Math.floor(Math.random() * n))), 70);
      const t = setTimeout(() => setStage('result'), ROLL_MS);
      return () => { clearInterval(tick); clearTimeout(t); };
    }
    if (stage === 'result') {
      const t = setTimeout(() => setStage('fly'), SHOW_MS);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [stage]);

  // Voo: do centro da tela até o total do card (FLIP com Web Animations). Card escondido (ex.: <details>
  // fechado) não tem caixa: o número só some.
  useLayoutEffect(() => {
    if (stage !== 'fly') return undefined;
    const el = numRef.current;
    const target = cardRef.current?.querySelector('[data-roll-total]')?.getBoundingClientRect();
    if (!el?.animate) { setStage('done'); return undefined; }
    const from = el.getBoundingClientRect();
    const frames = target && target.width > 0
      ? [{ transform: 'translate(0, 0) scale(1)' }, {
          transform: `translate(${target.left + target.width / 2 - (from.left + from.width / 2)}px, `
            + `${target.top + target.height / 2 - (from.top + from.height / 2)}px) scale(${target.height / from.height})`,
        }]
      : [{ opacity: 1 }, { opacity: 0, transform: 'scale(0.6)' }];
    const anim = el.animate(frames, { duration: FLY_MS, easing: 'cubic-bezier(.5,0,.2,1)', fill: 'forwards' });
    anim.onfinish = () => setStage('done');
    return () => anim.cancel();
  }, [stage]);

  useEffect(() => { if (stage === 'done') onAnimated?.(); }, [stage]); // eslint-disable-line react-hooks/exhaustive-deps

  if (stage === 'done') return <RollCard roll={roll} onReveal={onReveal} />;
  const who = roll.characterName || roll.actorName;
  return (
    <div ref={cardRef} aria-live="polite" aria-label={stage === 'waiting' ? undefined : 'Rolando dados'}>
      {/* Card real (sem crítico/falha) invisível: mantém a altura e dá o alvo do voo. */}
      <div className="invisible" aria-hidden="true">
        <RollCard roll={{ ...roll, critical: false, fumble: false }} />
      </div>
      {stage !== 'waiting' && createPortal(
        <div className="roll-stage" aria-hidden="true">
          {stage === 'rolling' && (
            <div className="flex flex-wrap items-center justify-center gap-3">
              {sides.map((n, i) => <Die key={i} sides={n} face={faces[i]} />)}
            </div>
          )}
          {stage !== 'rolling' && (
            <div className="flex flex-col items-center gap-1">
              <span ref={numRef} className={`roll-stage-number${roll.critical ? ' is-crit' : roll.fumble ? ' is-fumble' : ''}`}>
                {roll.total}
              </span>
              {stage === 'result' && (
                <span className="roll-stage-caption">{who}{roll.sourceName ? ` — ${roll.sourceName}` : ''}</span>
              )}
            </div>
          )}
        </div>,
        document.body,
      )}
    </div>
  );
}
