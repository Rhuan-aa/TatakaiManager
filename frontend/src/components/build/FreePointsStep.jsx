import { useState } from 'react';
import { setFreePoints } from '../../api/characterBuild';
import { ATTRS } from '../sheet/sheetRules';
import { StepActions, crossesBarrier } from './buildUi';

export default function FreePointsStep({ build, canAct, busy, confirm, onBack }) {
  const skipped = build.settings.randomAttributes;
  const [fp, setFp] = useState(() => ({ ...(build.freePoints ?? {}) }));
  const layers = build.layers ?? {};
  const spent = ATTRS.reduce((s, a) => s + (fp[a.enumName] ?? 0), 0);
  const left = build.freePointsAvailable - spent;
  const blocked = (en) => {
    const base = layers[en]?.afterPercent ?? 0;
    return crossesBarrier(base, base + (fp[en] ?? 0));
  };
  const anyBlocked = ATTRS.some((a) => blocked(a.enumName));
  const step = (en, d) => setFp((p) => ({ ...p, [en]: Math.max(0, (p[en] ?? 0) + d) }));

  if (skipped) {
    return (
      <div className="flex flex-col gap-4">
        <p className="caption">Atributos aleatórios (7d20) não têm pontos livres.</p>
        <StepActions canAct={canAct} busy={busy} onBack={onBack} onConfirm={() => confirm(null)} />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {build.dominance === 'LEFT' && <p className="caption">Canhoto: 3 dos 6 pontos livres estão reservados.</p>}
      <p className="font-bold" aria-live="polite">Pontos disponíveis: {left} / {build.freePointsAvailable}</p>
      <ul className="grid gap-2 sm:grid-cols-2">
        {ATTRS.map((a) => {
          const l = layers[a.enumName];
          const v = fp[a.enumName] ?? 0;
          const next = (l?.afterPercent ?? 0) + v + 1;
          return (
            <li key={a.key} className="panel flex items-center justify-between gap-2 p-2">
              <span className="font-bold">{a.label} <span className="opacity-70">{l?.afterPercent ?? '—'}</span></span>
              <span className="flex items-center gap-2">
                <button type="button" className="btn-secondary" disabled={!canAct || busy || v === 0}
                  aria-label={`Diminuir ${a.label}`} onClick={() => step(a.enumName, -1)}>−</button>
                <output className="panel-title w-8 text-center text-xl" aria-label={`${a.label}: ${v} pontos`}>{v}</output>
                <button type="button" className="btn-secondary"
                  disabled={!canAct || busy || left <= 0 || crossesBarrier(l?.afterPercent ?? 0, next)}
                  aria-label={`Aumentar ${a.label}`} onClick={() => step(a.enumName, 1)}>+</button>
              </span>
            </li>
          );
        })}
      </ul>
      <p className="text-xs">Pontos livres não podem cruzar as barreiras 3→4, 6→7 e 9→10.</p>
      {anyBlocked && <p className="caption">Algum atributo cruza uma barreira; ajuste antes de confirmar.</p>}
      <StepActions canAct={canAct} busy={busy} disabled={left < 0 || anyBlocked} onBack={onBack}
        onConfirm={() => confirm(() => setFreePoints(build.sheetId, { freePoints: fp }))} />
    </div>
  );
}
