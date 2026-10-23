import { useState } from 'react';
import { rollAttributes, assignAttributes } from '../../api/characterBuild';
import { ATTRS } from '../sheet/sheetRules';
import { DiceResult, StepActions } from './buildUi';

export default function AttributesStep({ build, canAct, busy, act, confirm, burst, onBack }) {
  const fixed = build.settings.randomAttributes; // 7d20 em ordem estrita
  const dice = build.attributeDice ?? [];
  const [assign, setAssign] = useState(() => ({ ...(build.assignment ?? {}) }));
  const [sel, setSel] = useState(null); // índice do dado selecionado
  const used = new Set(Object.values(assign));
  const complete = ATTRS.every((a) => assign[a.enumName] != null);

  // Clicar no dado e depois no atributo; clicar num atributo já preenchido o libera.
  function pickAttr(en) {
    if (!canAct || fixed) return;
    if (sel == null) { if (assign[en] != null) setAssign((p) => { const n = { ...p }; delete n[en]; return n; }); return; }
    setAssign((p) => ({ ...p, [en]: sel }));
    setSel(null);
  }

  return (
    <div className="flex flex-col gap-4">
      {canAct && dice.length === 0 && (
        <div><button type="button" className="btn-primary" disabled={busy}
          onClick={() => act(() => rollAttributes(build.sheetId), 'ROLL!')}>
          Rolar {fixed ? '7d20' : '7d12'}</button></div>
      )}
      {dice.length > 0 && (
        <>
          {fixed ? (
            <>
              <DiceResult label="7d20 (ordem fixa)" dice={dice} burst={burst} />
              <ul className="grid grid-cols-7 gap-2" aria-label="Atributos em ordem">
                {ATTRS.map((a, i) => (
                  <li key={a.key} className="panel p-2 text-center"><div className="text-xs">{a.label}</div>
                    <div className="panel-title text-2xl">{dice[i]}</div></li>
                ))}
              </ul>
            </>
          ) : (
            <>
              <DiceResult label="7d12" dice={[]} burst={burst} />
              <div role="group" aria-label="Dados rolados" className="flex flex-wrap gap-2">
                {dice.map((d, i) => (
                  <button key={i} type="button" disabled={!canAct || used.has(i)} aria-pressed={sel === i}
                    aria-label={`Dado ${i + 1}: ${d}${used.has(i) ? ' (já atribuído)' : ''}`}
                    className={sel === i ? 'btn-primary' : 'btn-secondary'} onClick={() => setSel(sel === i ? null : i)}>
                    {d}
                  </button>
                ))}
              </div>
              <p className="text-xs">{canAct ? 'Escolha um dado e depois o atributo. Clique num atributo preenchido para liberá-lo.' : ''}</p>
              <ul className="grid grid-cols-2 gap-2 sm:grid-cols-7">
                {ATTRS.map((a) => (
                  <li key={a.key}>
                    <button type="button" className="panel w-full p-2 text-center" disabled={!canAct}
                      aria-label={`${a.label}: ${assign[a.enumName] != null ? dice[assign[a.enumName]] : 'vazio'}`}
                      onClick={() => pickAttr(a.enumName)}>
                      <div className="text-xs">{a.label}</div>
                      <div className="panel-title text-2xl">{assign[a.enumName] != null ? dice[assign[a.enumName]] : '—'}</div>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </>
      )}
      <StepActions canAct={canAct} busy={busy} disabled={dice.length === 0 || (!fixed && !complete)} onBack={onBack}
        onConfirm={() => confirm(fixed ? null : () => assignAttributes(build.sheetId, { assignment: assign }))} />
    </div>
  );
}
