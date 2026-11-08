import { useEffect, useRef, useState } from 'react';
import { ATTRS } from '../sheet/sheetRules';
import { Die } from '../rolls/RollReveal';

// Mesma regra de BuildRules.crossesBarrier (3→4, 6→7, 9→10); o servidor é a autoridade.
export const crossesBarrier = (before, after) => [3, 6, 9].some((t) => before <= t && after > t);

/** Valores dos dados + explosão onomatopeica (burst muda a cada rolagem para reiniciar a animação). */
export function DiceResult({ label, dice, burst }) {
  return (
    <div className="relative flex flex-wrap items-center gap-3" aria-live="polite">
      <span className="label">{label}</span>
      {dice.map((d, i) => (
        <span key={i} className="panel panel-title px-3 py-1 text-2xl" aria-label={`Dado ${i + 1}: ${d}`}>{d}</span>
      ))}
      {burst && <span key={burst.k} className="onomatopoeia text-3xl">{burst.text}</span>}
    </div>
  );
}

export function StepActions({ canAct, busy, onBack, onConfirm, disabled, confirmLabel = 'Confirmar' }) {
  if (!canAct) return null;
  return (
    <div className="flex flex-wrap gap-3">
      {onBack && <button type="button" className="btn-secondary" disabled={busy} onClick={onBack}>Voltar</button>}
      <button type="button" className="btn-primary" disabled={busy || disabled} onClick={onConfirm}>
        {busy ? 'Aguarde…' : confirmLabel}
      </button>
    </div>
  );
}

export const attrName = (enumName) => ATTRS.find((a) => a.enumName === enumName)?.label ?? enumName;

const SPIN_MS = 900;
const AUTO_MS = 1200;
const reduced = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/**
 * Revela rolagens já feitas pelo servidor, uma de cada vez, com suspense.
 * beats: [{ label, sides: [n], values: [v], caption }]. O jogador clica "Rolar próximo" (ou liga o Automático / Pula).
 * onBeat(i) é chamado quando o beat i termina de girar; onDone() ao revelar o último.
 */
export function DiceSequence({ beats, onBeat, onDone }) {
  const [shown, setShown] = useState(0);      // beats já revelados
  const [spinning, setSpinning] = useState(false);
  const [faces, setFaces] = useState([]);
  const [auto, setAuto] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const cb = useRef({ onBeat, onDone });
  cb.current = { onBeat, onDone };
  const done = shown >= beats.length;
  const current = beats[shown];

  const reveal = (i) => {
    setShown(i + 1);
    setSpinning(false);
    cb.current.onBeat?.(i);
    if (i + 1 >= beats.length) finish();
  };
  // deixa os dados pousados à vista, depois o painel sai e a tela de resultados entra
  const finish = () => {
    if (reduced()) { cb.current.onDone?.(); return; }
    setTimeout(() => { setLeaving(true); setTimeout(() => cb.current.onDone?.(), 450); }, 900);
  };
  const next = () => {
    if (done || spinning) return;
    if (reduced()) { reveal(shown); return; }
    setSpinning(true);
  };
  const skip = () => {
    for (let i = shown; i < beats.length; i++) cb.current.onBeat?.(i);
    setShown(beats.length);
    setSpinning(false);
    finish();
  };

  useEffect(() => { // gira e revela
    if (!spinning || !current) return undefined;
    const tick = setInterval(() => setFaces(current.sides.map((n) => 1 + Math.floor(Math.random() * n))), 70);
    const stop = setTimeout(() => { clearInterval(tick); reveal(shown); }, SPIN_MS);
    return () => { clearInterval(tick); clearTimeout(stop); };
  }, [spinning]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { // automático: dispara o próximo depois de uma pausa
    if (!auto || spinning || done) return undefined;
    const t = setTimeout(next, shown === 0 ? 300 : AUTO_MS);
    return () => clearTimeout(t);
  }, [auto, spinning, shown, done]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className={`panel flex flex-col gap-3 p-3 overflow-hidden${leaving ? ' seq-exit' : ''}`} aria-live="polite">
      <ol className="flex flex-wrap gap-3">
        {beats.slice(0, shown).map((b, i) => (
          <li key={i} className="die-settle flex flex-col items-center gap-1">
            <span className="label">{b.label}</span>
            <div className="flex gap-1">{b.sides.map((n, k) => <Die key={k} sides={n} face={b.values[k]} small />)}</div>
            {b.caption && <span className="caption">{b.caption}</span>}
          </li>
        ))}
        {spinning && current && (
          <li className="flex flex-col items-center gap-1">
            <span className="label">{current.label}</span>
            <div className="flex gap-1">{current.sides.map((n, k) => <Die key={k} sides={n} face={faces[k] ?? n} />)}</div>
          </li>
        )}
      </ol>
      {!done && (
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-primary" disabled={spinning} onClick={next}>
            {shown === 0 ? 'Rolar' : 'Rolar próximo'}{current ? ` — ${current.label}` : ''}
          </button>
          <button type="button" className={auto ? 'btn-primary' : 'btn-secondary'} aria-pressed={auto}
            onClick={() => setAuto((a) => !a)}>Automático</button>
          <button type="button" className="btn-ghost" onClick={skip}>Pular</button>
        </div>
      )}
    </div>
  );
}

const RARITY_NAME = { COMMON: 'Comum', RARE: 'Raro', EPIC: 'Épico', LEGENDARY: 'Lendário', DIVINE: 'Divino' };

/** Sequência de rolagem de traços: quantidade, depois (raridade + tabela) de cada traço. beat.slot = índice do slot que ele revela. */
export function traitBeats(build) {
  const se = build.settings;
  const prodigy = se.traitMethod === 'PRODIGY';
  const beats = [];
  if (build.traitCountRoll != null && se.traitMethod !== 'BALANCED_CHOICE') {
    const n = build.traitCountRoll;
    beats.push({
      label: 'Quantidade', sides: [prodigy ? 2 : 4], values: [prodigy ? n - 1 : n],
      caption: prodigy ? `+1 Prodígio = ${n} traços` : `${n} traço${n > 1 ? 's' : ''}`,
    });
  }
  (build.traitSlots ?? []).forEach((s) => {
    if (s.divineBonus) {
      beats.push({ label: 'Traço Divino grátis', sides: [10], values: [s.tableRoll], caption: s.traitName ?? 'Aguardando Mestre', slot: s.index });
      return;
    }
    if (s.rarityRoll == null) return;
    beats.push({
      label: `Raridade ${s.index + 1}`, sides: [100], values: [s.rarityRoll],
      caption: RARITY_NAME[s.rarity] + (s.rarityRoll === 100 && se.divineMode === 'ROLL_100_GRANTS_DIVINE' ? ' (100!)' : ''),
    });
    beats.push({
      label: `Traço ${s.index + 1}`, sides: [s.rarity === 'DIVINE' ? 10 : 20], values: [s.tableRoll],
      caption: s.status === 'PENDING_GM' ? 'Pendente' : s.traitName, slot: s.index,
    });
  });
  return beats;
}

/** Chips com o que traços e clãs dão a um atributo: "+3 traços", "+2 clã", "+10%". `b` = build.bonuses[atributo]. */
export function AttrBonus({ b }) {
  if (!b || (!b.traitFlat && !b.clanFlat && !b.percent)) return null;
  return (
    <span className="flex flex-wrap gap-1 text-xs" aria-label="Bônus de traços e clãs">
      {b.traitFlat !== 0 && <span className="tag-accent" title="Soma dos traços">{b.traitFlat > 0 ? '+' : ''}{b.traitFlat} traços</span>}
      {b.clanFlat !== 0 && <span className="tag-accent" title="Soma dos clãs">{b.clanFlat > 0 ? '+' : ''}{b.clanFlat} clã</span>}
      {b.percent !== 0 && <span className="tag-accent" title="Percentual aplicado depois da soma">{b.percent > 0 ? '+' : ''}{b.percent}%</span>}
    </span>
  );
}
