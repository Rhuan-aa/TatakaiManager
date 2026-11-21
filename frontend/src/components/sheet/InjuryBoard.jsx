import { useState } from 'react';
import BodyMap, { BODY_PART_LABELS } from './BodyMap';
import { SEVERITIES, INJURY_TYPES } from './GmAdjustmentPanel';
import { ATTRS } from './sheetRules';
import { gmAdjust } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const SEV = Object.fromEntries(SEVERITIES);
const EMPTY = { type: 'CUTTING', severity: 'MINOR', region: '', damage: '', affectedAttribute: '', reason: '' };

function Pick({ lbl, val, set, opts, blank }) {
  return (
    <label className="label">
      {lbl}
      <select className="field mt-1 w-full min-h-[44px]" value={val} onChange={(e) => set(e.target.value)}>
        {blank && <option value="">Nenhum</option>}
        {opts.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
      </select>
    </label>
  );
}

export default function InjuryBoard({ sheet, isMaster, onChange }) {
  const toast = useToast();
  const [sel, setSel] = useState(null);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [pending, setPending] = useState(null); // { kind: 'TREAT_INJURY'|'REMOVE_INJURY'|'MISSING', id? }
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const missing = sheet.missingParts ?? [];
  const all = sheet.injuries;
  const list = sel ? all.filter((i) => i.bodyPart === sel) : all;
  const isMissing = sel && missing.includes(sel);

  async function send(body, done) {
    setBusy(true);
    try {
      const updated = await gmAdjust(sheet.id, body);
      toast('Ajuste aplicado.');
      onChange?.(updated);
      done();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const choose = (p) => { setSel(p === sel ? null : p); setAdding(false); setPending(null); };

  function addInjury(e) {
    e.preventDefault();
    const { reason: r, damage, affectedAttribute, ...rest } = form;
    send({ action: 'ADD_INJURY', reason: r.trim(), injury: {
      ...rest, damage: damage === '' ? null : Number(damage),
      affectedAttribute: affectedAttribute || null, bodyPart: sel,
    } }, () => { setForm(EMPTY); setAdding(false); });
  }

  function confirm(e) {
    e.preventDefault();
    const body = pending.kind === 'MISSING'
      ? { action: 'SET_MISSING_PART', bodyPart: sel, missing: !isMissing, reason: reason.trim() }
      : { action: pending.kind, targetId: pending.id, reason: reason.trim() };
    send(body, () => { setPending(null); setReason(''); });
  }

  const reasonField = (val, set) => (
    <label className="label">
      Motivo (obrigatório)
      <input required maxLength={500} className="field mt-1 w-full min-h-[44px]" value={val} onChange={(e) => set(e.target.value)} />
    </label>
  );

  return (
    <div className="flex flex-col gap-4 sm:flex-row">
      {/* missingParts null = boneco oculto (BODY_MAP do NPC): só a lista */}
      {sheet.missingParts != null && (
        <div className="mx-auto w-full max-w-[220px] shrink-0">
          <BodyMap injuries={all} missingParts={missing} selected={sel} onSelect={choose} />
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-3" aria-live="polite">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-display text-lg">{sel ? BODY_PART_LABELS[sel] : 'Todos os ferimentos'}</h3>
          {sel && <button type="button" className="btn-ghost min-h-[44px]" onClick={() => choose(sel)}>Ver todos</button>}
        </div>
        {isMissing && <p className="tag tag-accent self-start">Parte perdida</p>}
        {list.length === 0 ? <p className="text-sm">{sel ? 'Nenhum ferimento nesta parte.' : 'Nenhum ferimento.'}</p> : (
          <ul className="flex flex-col gap-2 text-sm">
            {list.map((i) => (
              <li key={i.id} className="well flex flex-col gap-2">
                <div>
                  <span className="tag mr-2">{SEV[i.severity] ?? i.severity}</span>
                  <strong>{BODY_PART_LABELS[i.bodyPart] ?? 'Sem parte definida'}</strong>
                  {' · '}{INJURY_TYPES.find(([k]) => k === i.type)?.[1] ?? i.type}
                  {i.region && ` · ${i.region}`} — HP máx. −{i.hpMaxReduction}
                  {i.affectedAttribute && `, limitador ${i.affectedAttribute} −${i.limiterReduction}`}
                  {i.treated && ' (tratado)'}
                </div>
                {isMaster && sel && (
                  <div className="flex flex-wrap gap-2">
                    {!i.treated && <button type="button" className="btn-secondary min-h-[44px]" onClick={() => { setPending({ kind: 'TREAT_INJURY', id: i.id }); setReason(''); }}>Tratar</button>}
                    <button type="button" className="btn-secondary min-h-[44px]" onClick={() => { setPending({ kind: 'REMOVE_INJURY', id: i.id }); setReason(''); }}>Remover</button>
                  </div>
                )}
                {pending?.id === i.id && (
                  <form onSubmit={confirm} className="flex flex-col gap-2">
                    {reasonField(reason, setReason)}
                    <div className="flex gap-2">
                      <button type="submit" className="btn-primary min-h-[44px]" disabled={busy || !reason.trim()}>Confirmar</button>
                      <button type="button" className="btn-ghost min-h-[44px]" onClick={() => setPending(null)}>Cancelar</button>
                    </div>
                  </form>
                )}
              </li>
            ))}
          </ul>
        )}
        {isMaster && sel && (
          <div className="flex flex-col gap-3">
            {!adding && (
              <div className="flex flex-wrap gap-2">
                <button type="button" className="btn-secondary min-h-[44px]" onClick={() => setAdding(true)}>Adicionar ferimento aqui</button>
                <button type="button" className="btn-secondary min-h-[44px]" onClick={() => { setPending({ kind: 'MISSING' }); setReason(''); }}>
                  {isMissing ? 'Restaurar parte' : 'Marcar parte como perdida'}
                </button>
              </div>
            )}
            <p className="hint">Marcação visual; os efeitos vêm dos ferimentos.</p>
            {pending?.kind === 'MISSING' && (
              <form onSubmit={confirm} className="well flex flex-col gap-2">
                {reasonField(reason, setReason)}
                <div className="flex gap-2">
                  <button type="submit" className="btn-primary min-h-[44px]" disabled={busy || !reason.trim()}>Confirmar</button>
                  <button type="button" className="btn-ghost min-h-[44px]" onClick={() => setPending(null)}>Cancelar</button>
                </div>
              </form>
            )}
            {adding && (
              <form onSubmit={addInjury} className="well grid gap-2 sm:grid-cols-2">
                <Pick lbl="Tipo" val={form.type} set={(v) => setForm({ ...form, type: v })} opts={INJURY_TYPES} />
                <Pick lbl="Gravidade" val={form.severity} set={(v) => setForm({ ...form, severity: v })} opts={SEVERITIES} />
                <label className="label">
                  Região
                  <input required maxLength={100} className="field mt-1 w-full min-h-[44px]" value={form.region}
                    onChange={(e) => setForm({ ...form, region: e.target.value })} />
                </label>
                <label className="label">
                  Dano (só contusão)
                  <input type="number" min={0} className="field mt-1 w-full min-h-[44px]" value={form.damage}
                    onChange={(e) => setForm({ ...form, damage: e.target.value })} />
                </label>
                <Pick lbl="Atributo afetado" blank val={form.affectedAttribute}
                  set={(v) => setForm({ ...form, affectedAttribute: v })} opts={ATTRS.map((a) => [a.enumName, a.label])} />
                {reasonField(form.reason, (v) => setForm({ ...form, reason: v }))}
                <div className="flex gap-2 sm:col-span-2">
                  <button type="submit" className="btn-primary min-h-[44px]" disabled={busy || !form.reason.trim()}>Adicionar</button>
                  <button type="button" className="btn-ghost min-h-[44px]" onClick={() => setAdding(false)}>Cancelar</button>
                </div>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
