import { useEffect, useRef } from 'react';
import { REACTION_HINT, REACTION_LABEL } from './combatLabels';

const signed = (n) => (n >= 0 ? `+${n}` : `${n}`);

/** Tela de reação em destaque: aparece quando um ataque pendente espera a resposta de quem está vendo. */
export default function ReactionOverlay({ attack: a, target: t, onReact, onMinimize }) {
  const first = useRef(null);
  useEffect(() => {
    first.current?.focus();
    const onKey = (e) => { if (e.key === 'Escape') onMinimize(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onMinimize]);

  return (
    <div className="reaction-overlay" role="dialog" aria-modal="true" aria-labelledby="reaction-title">
      <div className="relative w-full max-w-2xl">
        <span className="reaction-burst" aria-hidden="true">Ataque!</span>
        <div className="reaction-card">
        <h2 id="reaction-title" className="panel-title mt-2 text-2xl">
          {a.attackerName} ataca {t.targetName}
        </h2>
        <p className="mb-4 text-sm">
          com <strong>{a.sourceName}</strong>{a.region ? ` na ${a.region}` : ''} — escolha a reação.
        </p>
        <div className="grid gap-3 sm:grid-cols-2">
          {t.options.map((o, i) => {
            const h = REACTION_HINT[o.reaction];
            return (
              <button key={o.reaction} ref={i === 0 ? first : undefined} type="button" className="reaction-option"
                disabled={!o.available} onClick={() => onReact(a.id, t.id, o.reaction)}>
                <span className="flex items-center justify-between gap-2">
                  <span className="panel-title text-lg">{REACTION_LABEL[o.reaction]}</span>
                  {h?.attr && <span className="tag">{h.attr} {signed(o.predictedBonus)}</span>}
                </span>
                <span className="text-xs">{h?.text}</span>
                <span className="text-xs font-bold">{o.available ? h?.cost : o.reason}</span>
              </button>
            );
          })}
        </div>
        <div className="mt-4 text-right">
          <button type="button" className="btn-ghost text-xs" onClick={onMinimize}>Decidir depois (Esc)</button>
        </div>
        </div>
      </div>
    </div>
  );
}
