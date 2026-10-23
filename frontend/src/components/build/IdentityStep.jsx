import { useState } from 'react';
import { setIdentity } from '../../api/characterBuild';
import { BODY_TYPES, DOMINANCES } from '../sheet/sheetRules';
import { StepActions } from './buildUi';

export default function IdentityStep({ build, canAct, busy, act, confirm }) {
  const [f, setF] = useState({
    bodyType: build.bodyType ?? 'MESOMORPH',
    dominance: build.dominance ?? 'RIGHT',
    heightM: build.heightM ?? 1.75,
    weightKg: build.weightKg ?? '',
  });
  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));
  const h = Number(f.heightM);
  const valid = h >= 1 && h <= 2.5;
  const body = () => ({
    bodyType: f.bodyType, dominance: f.dominance, heightM: h,
    weightKg: f.weightKg === '' ? null : Number(f.weightKg),
  });

  return (
    <div className="flex flex-col gap-4">
      <fieldset disabled={!canAct} className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs uppercase">Tipo corporal
          <select className="field mt-1 w-full" value={f.bodyType} onChange={set('bodyType')}>
            {BODY_TYPES.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
          </select>
        </label>
        <label className="text-xs uppercase">Dominância
          <select className="field mt-1 w-full" value={f.dominance} onChange={set('dominance')}>
            {DOMINANCES.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
          </select>
        </label>
        <label className="text-xs uppercase">Altura (m)
          <input className="field mt-1 w-full" type="number" step="0.01" min="1" max="2.5"
            value={f.heightM} onChange={set('heightM')} aria-invalid={!valid} />
        </label>
        <label className="text-xs uppercase">Peso (kg, opcional)
          <input className="field mt-1 w-full" type="number" step="0.1" min="0" value={f.weightKg} onChange={set('weightKg')} />
        </label>
      </fieldset>
      {f.dominance === 'LEFT' && <p className="caption">Canhoto reserva 3 dos 6 pontos livres.</p>}
      {!valid && <p className="text-xs" style={{ color: 'var(--accent)' }}>Altura deve ficar entre 1,00 e 2,50 m.</p>}
      <StepActions canAct={canAct} busy={busy} disabled={!valid}
        onConfirm={() => confirm(() => setIdentity(build.sheetId, body()))} />
      {/* act mantido na assinatura por uniformidade */ void act}
    </div>
  );
}
