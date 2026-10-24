import { useState } from 'react';
import { createSheet, createNpcSheet, updateSheet, submitSheet } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import {
  ATTRS, BODY_TYPES, DOMINANCES, BAND_LABELS,
  mod, band, attributeLimiter, statusLimiter, initialHpMax, initialSpMax, initialKpMax,
} from './sheetRules';

const num = (v) => (v === '' ? 0 : Number(v));

export function initialSheetFields(sheet, preset) {
  const a = sheet?.attributes ?? {};
  return {
    name: sheet?.name ?? '',
    bodyType: sheet?.bodyType ?? 'MESOMORPH',
    dominance: sheet?.dominance ?? 'RIGHT',
    heightM: sheet?.heightM ?? 1.75,
    weightKg: sheet?.weightKg ?? '',
    str: a.STR ?? 10, dex: a.DEX ?? 10, con: a.CON ?? 10, intl: a.INT ?? 10,
    cha: a.CHA ?? 10, men: a.MEN ?? 10, tal: a.TAL ?? 10,
    ...(sheet ? {} : preset),
  };
}

export function sheetPayload(f) {
  return {
    name: f.name.trim(),
    bodyType: f.bodyType,
    dominance: f.dominance,
    heightM: Number(f.heightM),
    weightKg: f.weightKg === '' || f.weightKg == null ? null : Number(f.weightKg),
    attributes: Object.fromEntries(ATTRS.map((a) => [a.key, num(f[a.key])])),
  };
}

/** Campos da ficha (sem o nome) — usados aqui e na criação de NPC completo. */
export function SheetFields({ f, set, errors = {} }) {
  const err = (k) => errors[k] && <span className="text-xs" style={{ color: 'var(--accent)' }}>{errors[k]}</span>;
  const con = num(f.con);
  const tal = num(f.tal);
  const hpMax = initialHpMax(con) + (f.bodyType === 'ENDOMORPH' ? 10 : f.bodyType === 'MESOMORPH' ? 5 : 0);
  const spMax = initialSpMax(con) + (f.bodyType === 'MESOMORPH' ? 5 : f.bodyType === 'ECTOMORPH' ? 10 : 0);
  const kpMax = initialKpMax(con);
  // ponytail: bônus de corpo em HP/SP inferido da §8.5; o servidor é a fonte da verdade.
  return (
    <>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs uppercase">
          Tipo corporal
          <select className="field mt-1 w-full" value={f.bodyType} onChange={set('bodyType')}>
            {BODY_TYPES.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
          </select>
        </label>
        <label className="text-xs uppercase">
          Dominância
          <select className="field mt-1 w-full" value={f.dominance} onChange={set('dominance')}>
            {DOMINANCES.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
          </select>
        </label>
        <label className="text-xs uppercase">
          Altura (m)
          <input type="number" step="0.01" min="1" max="2.5" required className="field mt-1 w-full"
            value={f.heightM} onChange={set('heightM')} />
          {err('heightM')}
        </label>
        <label className="text-xs uppercase">
          Peso (kg) — opcional
          <input type="number" step="0.1" min="0" className="field mt-1 w-full" value={f.weightKg}
            onChange={set('weightKg')} placeholder="Calculado se vazio" />
        </label>
      </div>

      <fieldset>
        <legend className="panel-title mb-2">Atributos</legend>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {ATTRS.map((a) => {
            const v = num(f[a.key]);
            const m = mod(v);
            const lim = attributeLimiter(a.enumName, tal, f.bodyType);
            return (
              <label key={a.key} className="panel p-2 text-xs uppercase">
                {a.label}
                <input type="number" min={0} max={999} required className="field mt-1 w-full"
                  value={f[a.key]} onChange={set(a.key)} />
                <span className="mt-1 block normal-case">
                  Mod {m === null ? 'Crítico' : m} · {BAND_LABELS[band(v)]}
                  {lim !== null && ` · Lim ${lim}`}
                </span>
                {err(`attributes.${a.key}`)}
              </label>
            );
          })}
        </div>
      </fieldset>

      <div className="caption" aria-live="polite">
        Prévia inicial — HP {hpMax} (lim. {statusLimiter('HP', tal, f.bodyType)}) · SP {spMax} (lim. {statusLimiter('SP', tal, f.bodyType)}) · PQ {kpMax} (lim. {statusLimiter('KP', tal, f.bodyType)})
      </div>
    </>
  );
}

/** Criação (sem `sheet`) ou edição. Modo NPC: `npcId` (sem aprovação); `initialValues` pré-preenche a criação. */
export default function CharacterSheetForm({ campaignId, npcId, initialValues, sheet, onSaved, onCancel }) {
  const toast = useToast();
  const [f, setF] = useState(() => initialSheetFields(sheet, initialValues));
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));

  const payload = () => sheetPayload(f);

  async function save(andSubmit) {
    setBusy(true);
    setErrors({});
    try {
      let saved = sheet ? await updateSheet(sheet.id, payload()) : await (npcId ? createNpcSheet(npcId, payload()) : createSheet(campaignId, payload()));
      if (andSubmit) saved = await submitSheet(saved.id);
      toast(andSubmit ? 'Ficha enviada para aprovação.' : 'Ficha salva.');
      onSaved?.(saved);
    } catch (err) {
      const { message, fields } = parseApiError(err);
      setErrors(fields);
      toast(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const err = (k) => errors[k] && <span className="text-xs" style={{ color: 'var(--accent)' }}>{errors[k]}</span>;

  return (
    <form onSubmit={(e) => { e.preventDefault(); save(false); }} className="flex flex-col gap-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-xs uppercase sm:col-span-2">
          Nome
          <input required maxLength={100} className="field mt-1 w-full" value={f.name} onChange={set('name')} />
          {err('name')}
        </label>
      </div>
      <SheetFields f={f} set={set} errors={errors} />

      <div className="flex flex-wrap gap-2">
        <button type="submit" className="btn-secondary" disabled={busy}>{npcId ? 'Salvar ficha' : 'Salvar rascunho'}</button>
        {!npcId && (
          <button type="button" className="btn-primary" disabled={busy} onClick={() => save(true)}>
            Enviar para aprovação
          </button>
        )}
        {onCancel && <button type="button" className="btn-ghost" onClick={onCancel}>Cancelar</button>}
      </div>
    </form>
  );
}
