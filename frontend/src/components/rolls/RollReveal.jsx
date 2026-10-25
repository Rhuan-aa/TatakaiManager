import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import RollCard from './RollCard';
import { queueSlot } from './revealQueue';

const ROLL_MS = 900;   // d20 girando no centro
const SHOW_MS = 550;   // número parado no centro
const FLY_MS = 450;    // número voa até o total do card
const REVEAL_MS = ROLL_MS + SHOW_MS + FLY_MS;

const seen = new Set(); // ids já exibidos nesta sessão: nunca animam de novo
const reduced = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

function Die({ face }) {
  return (
    <svg viewBox="0 0 100 100" className="dice-rolling h-24 w-24" aria-hidden="true">
      <polygon points="50,4 93,28 93,72 50,96 7,72 7,28" fill="var(--paper)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
      <path d="M50 4 L7 72 M50 4 L93 72 M7 28 L93 28 M7 72 L93 72" stroke="var(--ink)" strokeWidth="2" fill="none" opacity="0.4" />
      <text x="50" y="62" textAnchor="middle" fontSize="30" fontWeight="900" fill="var(--ink)">{face}</text>
    </svg>
  );
}

/**
 * Revela uma rolagem em duas etapas: o d20 gira no centro da tela, o total aparece grande no centro e
 * depois voa/encolhe até o número do RollCard. `fresh` = rolagem que chegou com a página aberta.
 */
export default function RollReveal({ roll, fresh, onReveal }) {
  // waiting → rolling → result → fly → done
  const [stage, setStage] = useState(() => (fresh && !seen.has(roll.id) && !reduced() ? 'waiting' : 'done'));
  const [face, setFace] = useState(20);
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
      const tick = setInterval(() => setFace(1 + Math.floor(Math.random() * 20)), 70);
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
          {stage === 'rolling' && <Die face={face} />}
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
