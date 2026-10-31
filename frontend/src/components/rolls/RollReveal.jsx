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

// Silhueta por tipo de dado (viewBox 100×100): contorno + arestas internas para cada sólido ser reconhecível.
const poly = (n, r, rot = -90) => Array.from({ length: n }, (_, i) => {
  const a = ((rot + (360 / n) * i) * Math.PI) / 180;
  return [50 + r * Math.cos(a), 50 + r * Math.sin(a)];
});
const pts = (arr) => arr.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
const lines = (pairs) => pairs.map(([[x1, y1], [x2, y2]]) => `M${x1.toFixed(1)} ${y1.toFixed(1)} L${x2.toFixed(1)} ${y2.toFixed(1)}`).join(' ');
const dec = poly(10, 46);
const pent = poly(5, 22);
const hex = poly(6, 46);
const SHAPES = {
  // tetraedro: triângulo com vértice frontal
  4: { outline: '50,6 95,88 5,88', edges: 'M50 6 L50 64 M5 88 L50 64 M95 88 L50 64' },
  // cubo em perspectiva (3 faces)
  6: { outline: pts(hex), edges: 'M50 50 L10 27 M50 50 L90 27 M50 50 L50 96' },
  // octaedro: losango com cintura e faces triangulares
  8: { outline: '50,3 94,50 50,97 6,50', edges: 'M6 50 L94 50 M50 3 L30 50 M50 3 L70 50 M50 97 L30 50 M50 97 L70 50' },
  // trapezoedro pentagonal: pipa com arestas em "V"
  10: { outline: '50,2 93,40 50,98 7,40', edges: 'M7 40 L50 26 L93 40 M50 26 L50 2', textY: 66 },
  // dodecaedro: decágono com pentágono central ligado aos vértices
  12: { outline: pts(dec), edges: `${lines(pent.map((p, i) => [p, pent[(i + 1) % 5]]))} ${lines(pent.map((p, i) => [p, dec[i * 2]]))}` },
  // icosaedro: hexágono com triângulo interno
  20: { outline: pts(hex), edges: 'M50 18 L78 66 L22 66 Z M50 4 L50 18 M93 27 L78 66 M7 27 L22 66 M93 73 L78 66 M7 73 L22 66 M50 96 L22 66 M50 96 L78 66' },
};
SHAPES[100] = SHAPES[10];
const countDice = (text) => [...(text ?? '').matchAll(/(\d+)d\d+/g)].reduce((n, m) => n + Number(m[1]), 0);
SHAPES[3] = SHAPES[6]; // d3 = d6 ÷ 2
const MAX_DICE = 8;

/** Lados de cada dado de um trecho de fórmula ("2d6+1d4" → [6, 6, 4]); sem dados reconhecíveis → []. */
export function diceSides(text) {
  const out = [];
  for (const m of (text ?? '').matchAll(/(\d+)d(\d+)/g)) {
    for (let i = 0; i < Number(m[1]) && out.length < MAX_DICE; i++) out.push(Number(m[2]));
  }
  return out;
}

function Die({ sides, face, extreme, small }) {
  const size = small ? 'h-14 w-14' : 'h-20 w-20';
  const cls = extreme ? ` die-${extreme}` : small ? '' : ' dice-rolling';
  if (sides === 2) { // d2 = d6 ÷ 3: moeda
    return (
      <svg viewBox="0 0 100 100" className={`${size}${cls}`} aria-hidden="true">
        <circle cx="50" cy="50" r="44" fill="var(--paper)" stroke="var(--ink)" strokeWidth="5" />
        <circle cx="50" cy="50" r="34" fill="none" stroke="var(--ink)" strokeWidth="2" opacity="0.4" />
        <text x="50" y="62" textAnchor="middle" fontSize="32" fontWeight="900" fill="var(--ink)">{face}</text>
      </svg>
    );
  }
  const shape = SHAPES[sides] ?? SHAPES[20];
  return (
    <svg viewBox="0 0 100 100" className={`${size}${cls}`} aria-hidden="true">
      <polygon points={shape.outline} fill="var(--paper)" stroke="var(--ink)" strokeWidth="5" strokeLinejoin="round" />
      <path d={shape.edges} stroke="var(--ink)" strokeWidth="2" fill="none" opacity="0.45" />
      <text x="50" y={shape.textY ?? 62} textAnchor="middle" fontSize={sides === 100 ? 22 : 28} fontWeight="900" fill="var(--ink)" stroke="var(--paper)" strokeWidth="3" paintOrder="stroke">{face}</text>
    </svg>
  );
}

/**
 * Revela uma rolagem em duas etapas: o d20 gira no centro da tela, o total aparece grande no centro e
 * depois voa/encolhe até o número do RollCard. `fresh` = rolagem que chegou com a página aberta.
 */
export default function RollReveal({ roll, fresh, onReveal, onAnimated, damage = true }) {
  // waiting → rolling → result → fly → (dano: rolling → result → fly) → done
  const [stage, setStage] = useState(() => (fresh && !seen.has(roll.id) && !reduced() ? 'waiting' : 'done'));
  const [phase, setPhase] = useState('main'); // main = rolagem; damage = dado de dano depois do acerto
  const [mainText, dmgText] = (roll.formula ?? '').split('|');
  const mainSides = useMemo(() => {
    const d = diceSides(mainText);
    return d.length ? d : [20];
  }, [mainText]);
  const dmgSides = useMemo(() => (damage && roll.damageTotal > 0 ? diceSides(dmgText) : []), [damage, roll.damageTotal, dmgText]);
  const sides = phase === 'damage' ? dmgSides : mainSides;
  // Valores reais dos dados da fase (diceResults: dados do ataque primeiro, depois os de dano)
  const values = useMemo(() => {
    const all = roll.diceResults ?? [];
    const from = phase === 'damage' ? countDice(mainText) : 0;
    const n = phase === 'damage' ? countDice(dmgText) : countDice(mainText);
    const v = all.slice(from, from + n).slice(0, sides.length);
    return v.length === sides.length ? v : null;
  }, [roll.diceResults, phase, mainText, dmgText, sides.length]);
  // Pontas: dado máximo/mínimo (o d20 já tem crítico/falha)
  const extremes = values ? values.map((v, i) => (sides[i] === 20 ? null : v === sides[i] ? 'max' : v === 1 ? 'min' : null)) : [];
  const allMax = values && sides.every((n, i) => n !== 20 && extremes[i] === 'max');
  const allMin = values && sides.every((n, i) => n !== 20 && extremes[i] === 'min');
  const [faces, setFaces] = useState(() => mainSides.map((n) => n));
  const slot = useRef(null);
  const cardRef = useRef(null);
  const numRef = useRef(null);

  useEffect(() => {
    seen.add(roll.id);
    if (stage !== 'waiting') return undefined;
    slot.current ??= queueSlot(dmgSides.length ? REVEAL_MS * 2 : REVEAL_MS);
    const t = setTimeout(() => setStage('rolling'), slot.current);
    return () => clearTimeout(t);
  }, [roll.id, stage, dmgSides.length]);

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
  }, [stage, sides]);

  // Voo: do centro da tela até o total do card (FLIP com Web Animations). Card escondido (ex.: <details>
  // fechado) não tem caixa: o número só some.
  useLayoutEffect(() => {
    if (stage !== 'fly') return undefined;
    const el = numRef.current;
    const target = cardRef.current?.querySelector(phase === 'damage' ? '[data-roll-damage]' : '[data-roll-total]')?.getBoundingClientRect();
    const next = () => {
      if (phase === 'main' && dmgSides.length) { setPhase('damage'); setStage('rolling'); } else setStage('done');
    };
    if (!el?.animate) { next(); return undefined; }
    const from = el.getBoundingClientRect();
    const frames = target && target.width > 0
      ? [{ transform: 'translate(0, 0) scale(1)' }, {
          transform: `translate(${target.left + target.width / 2 - (from.left + from.width / 2)}px, `
            + `${target.top + target.height / 2 - (from.top + from.height / 2)}px) scale(${target.height / from.height})`,
        }]
      : [{ opacity: 1 }, { opacity: 0, transform: 'scale(0.6)' }];
    const anim = el.animate(frames, { duration: FLY_MS, easing: 'cubic-bezier(.5,0,.2,1)', fill: 'forwards' });
    anim.onfinish = next;
    return () => anim.cancel();
  }, [stage]); // eslint-disable-line react-hooks/exhaustive-deps

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
              {sides.map((n, i) => <Die key={i} sides={n} face={faces[i] ?? n} />)}
            </div>
          )}
          {stage !== 'rolling' && (
            <div className="flex flex-col items-center gap-1">
              {values && (
                <div className="flex flex-wrap items-center justify-center gap-2">
                  {sides.map((n, i) => <Die key={i} sides={n} face={values[i]} extreme={extremes[i]} small />)}
                </div>
              )}
              <span ref={numRef} className={`roll-stage-number${phase === 'damage' ? '' : roll.critical ? ' is-crit' : roll.fumble ? ' is-fumble' : ''}`}>
                {phase === 'damage' ? roll.damageTotal : roll.total}
              </span>
              {stage === 'result' && (
                <span className="roll-stage-caption">{allMax ? 'Dado máximo! ' : allMin ? 'Dado mínimo. ' : ''}{phase === 'damage' ? 'Dano — ' : ''}{who}{roll.sourceName ? ` — ${roll.sourceName}` : ''}</span>
              )}
            </div>
          )}
        </div>,
        document.body,
      )}
    </div>
  );
}
