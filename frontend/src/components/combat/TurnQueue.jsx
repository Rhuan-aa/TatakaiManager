import { useEffect, useRef } from 'react';
import Icon from '../ui/Icon';
import { hasCondition, teamColor } from './combatLabels';

/** Fila horizontal de turnos: todos os participantes em ordem de iniciativa; clicar rola até o cartão do rastreador. */
export default function TurnQueue({ enc, pendingIds }) {
  const box = useRef(null);
  useEffect(() => {
    box.current?.querySelector('[aria-current="step"]')?.scrollIntoView?.({ block: 'nearest', inline: 'center' });
  }, [enc.currentParticipantId]);

  const goTo = (id) => document.querySelector(`[data-participant-id="${id}"]`)
    ?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });

  return (
    <ol ref={box} className="flex gap-2 overflow-x-auto px-1 pb-2 pt-3" aria-label="Ordem de iniciativa">
      {enc.participants.map((p) => {
        const current = p.id === enc.currentParticipantId;
        const r = p.resources;
        const down = hasCondition(p, 'MORTO') || hasCondition(p, 'INCONSCIENTE');
        const pct = r?.hpMax > 0 ? Math.max(0, Math.min(100, (r.currentHp / r.hpMax) * 100)) : 0;
        return (
          <li key={p.id} className="shrink-0">
            <button type="button" onClick={() => goTo(p.id)} aria-current={current ? 'step' : undefined}
              title={`${p.name} — Ini ${p.initiative ?? '—'}, time ${p.team ?? '—'}`}
              className={`block min-w-24 max-w-40 ${current ? 'turn-active' : ''} border-2 border-[var(--line)] bg-[var(--panel-bg)] px-2 py-1 text-left text-xs`}
              style={{
                borderTop: `5px solid ${teamColor(p.team)}`,
                ...(current ? { background: 'color-mix(in srgb, var(--highlight) 22%, var(--panel-bg))' } : {}),
                ...(down ? { opacity: 0.6 } : {}),
              }}>
              <span className="flex items-center gap-1">
                <strong className="truncate">{p.name}</strong>
                {pendingIds.has(p.id) && <span className="shrink-0" title="Reação pendente"><Icon name="swords" size={14} /></span>}
                {down && <span className="shrink-0" title="Fora de combate"><Icon name="rest" size={14} /></span>}
              </span>
              {r && (
                <span className="meter mt-1 block" style={{ height: 6, borderWidth: 2 }} role="meter" aria-label={`HP de ${p.name}`}
                  aria-valuenow={r.currentHp} aria-valuemin={0} aria-valuemax={r.hpMax}>
                  <span className="meter-fill block" style={{ width: `${pct}%`, height: '100%', '--meter-color': 'var(--meter-hp)' }} />
                </span>
              )}
            </button>
          </li>
        );
      })}
    </ol>
  );
}
