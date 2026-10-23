import { ATTRS } from '../sheet/sheetRules';

// Mesma regra de BuildRules.crossesBarrier (3→4, 6→7, 9→10); o servidor é a autoridade.
export const crossesBarrier = (before, after) => [3, 6, 9].some((t) => before <= t && after > t);

/** Valores dos dados + explosão onomatopeica (burst muda a cada rolagem para reiniciar a animação). */
export function DiceResult({ label, dice, burst }) {
  return (
    <div className="relative flex flex-wrap items-center gap-3" aria-live="polite">
      <span className="text-xs uppercase">{label}</span>
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
