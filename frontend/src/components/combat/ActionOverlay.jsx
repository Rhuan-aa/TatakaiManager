import { useEffect, useRef, useState } from 'react';
import ActionBar from './ActionBar';
import AttackBuilder from './AttackBuilder';

/** Opções da preferência `combat.autoActions` (quando o pop-up abre sozinho). */
const PREF_OPTIONS = {
  master: [['all', 'Em todos os turnos'], ['npc', 'Só em turnos de NPC'], ['manual', 'Só ao clicar em "Ações"']],
  player: [['auto', 'No meu turno'], ['manual', 'Só ao clicar em "Ações"']],
};

/**
 * Pop-up de ações do turno (mesmo quadro da tela de reação). O ActionBar fica sempre montado (só escondido)
 * para não perder guarda/base/alvo escolhidos; "Atacar" troca o corpo pelo AttackBuilder.
 * `suspended`: há reação aguardando — a tela de reação (z 60) manda; aqui não captura Esc nem foco.
 */
export default function ActionOverlay({ open, suspended, participant: p, participants, canAct, isMaster, pref, onPref,
  onAct, onGrapple, onDeclare, onClose }) {
  const [attacking, setAttacking] = useState(null); // null | 'MAJOR' | 'MINOR_OFFENSIVE'
  const card = useRef(null);

  useEffect(() => {
    if (!open || suspended) return undefined;
    const prev = document.activeElement;
    card.current?.focus();
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => { window.removeEventListener('keydown', onKey); prev?.focus?.(); };
  }, [open, suspended, onClose]);

  return (
    <div className="reaction-overlay" style={{ zIndex: 55, display: open ? undefined : 'none' }}
      role="dialog" aria-modal="true" aria-labelledby="actions-title" aria-hidden={!open || suspended || undefined}>
      <div className="relative w-full max-w-3xl">
        <span className="reaction-burst" aria-hidden="true">Sua vez!</span>
        <div className="reaction-card" ref={card} tabIndex={-1}>
          <h2 id="actions-title" className="sr-only">Ações de {p.name}</h2>
          <div className={attacking ? 'hidden' : 'mt-2'}>
            <ActionBar bare active={open && !suspended} participant={p} participants={participants} canAct={canAct} onAct={onAct}
              onGrapple={onGrapple} onAttack={setAttacking} />
          </div>
          {attacking && (
            <div className="mt-2">
              <AttackBuilder key={attacking} participant={p} participants={participants} initialKind={attacking}
                cancelLabel="Voltar" onCancel={() => setAttacking(null)}
                onSubmit={(b) => onDeclare(b).then((ok) => ok !== false && setAttacking(null))} />
            </div>
          )}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
            <label className="flex items-center gap-1 text-xs">Abrir sozinho
              <select className="field !mt-0 !w-auto !py-0.5 text-xs" value={pref} onChange={(e) => onPref(e.target.value)}>
                {PREF_OPTIONS[isMaster ? 'master' : 'player'].map(([k, l]) => <option key={k} value={k}>{l}</option>)}
              </select>
            </label>
            <button type="button" className="btn-ghost text-xs" onClick={onClose}>Fechar (Esc)</button>
          </div>
        </div>
      </div>
    </div>
  );
}
