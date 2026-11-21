import { useState } from 'react';
import BodyMap, { BODY_PART_LABELS, BRUISE } from './BodyMap';
import AuraLayers from '../ui/AuraLayers';
import { digitsOf, limbOf } from './bodyGeometry';
import { SEVERITIES, INJURY_TYPES } from './GmAdjustmentPanel';
import { ATTRS } from './sheetRules';
import { addWound, gmAdjust, removeWound } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const SEV = Object.fromEntries(SEVERITIES);
const EMPTY = { type: 'CUTTING', severity: 'MINOR', region: '', damage: '', affectedAttribute: '', reason: '' };
const TOOLS = [
  [null, 'Selecionar'], ['IMPACT', 'Mancha'], ['CUT', 'Corte'], ['SCAR', 'Cicatriz'], ['HOLE', 'Furo'],
];
const TOOL_LABEL = Object.fromEntries(TOOLS);
// tipo de dano padrão da regra conforme a marca
const RULE_TYPE = { IMPACT: 'BLUNT', CUT: 'CUTTING', SCAR: 'CUTTING', HOLE: 'PIERCING' };

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

export default function InjuryBoard({ sheet, isMaster, onChange, conditions = [] }) {
  const toast = useToast();
  const [sel, setSel] = useState(null);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [pending, setPending] = useState(null); // { kind: 'TREAT_INJURY'|'REMOVE_INJURY'|'MISSING', id? }
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  // desenho (Mestre)
  const [tool, setTool] = useState(null);
  const [severity, setSeverity] = useState('MODERATE');
  const [rule, setRule] = useState(false);
  const [ruleForm, setRuleForm] = useState({ type: '', damage: '', affectedAttribute: '', region: '' });

  const missing = sheet.missingParts ?? [];
  const wounds = sheet.wounds ?? [];
  const all = sheet.injuries;
  const list = sel ? all.filter((i) => i.bodyPart === sel) : all;
  const marks = sel ? wounds.filter((w) => w.part === sel) : [];
  const isMissing = sel && missing.includes(sel);
  const digits = sel ? digitsOf(sel) : [];
  const showBody = sheet.missingParts != null;

  async function run(fn, ok, done) {
    setBusy(true);
    try {
      const updated = await fn();
      toast(ok);
      onChange?.(updated);
      done?.();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }
  const send = (body, done) => run(() => gmAdjust(sheet.id, body), 'Ajuste aplicado.', done);

  const choose = (p) => { setSel(p === sel ? null : p); setAdding(false); setPending(null); };

  function draw(part, type, geometry) {
    setSel(part);
    const injury = rule ? {
      type: ruleForm.type || RULE_TYPE[type],
      severity,
      region: (ruleForm.region || BODY_PART_LABELS[part]).slice(0, 100),
      damage: ruleForm.damage === '' ? null : Number(ruleForm.damage),
      affectedAttribute: ruleForm.affectedAttribute || null,
      bodyPart: part,
    } : null;
    run(() => addWound(sheet.id, { part, type, geometry, severity: type === 'IMPACT' || rule ? severity : null, injury }),
      rule ? `${TOOL_LABEL[type]} desenhado e ferimento criado.` : `${TOOL_LABEL[type]} desenhado.`);
  }

  function point(pt) {
    setSel(pt.part);
    setForm((f) => ({ ...f, region: pt.label }));
    setRuleForm((f) => ({ ...f, region: pt.label }));
  }

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
    <div className="flex flex-col gap-4">
      {isMaster && showBody && (
        <div className="well flex flex-col gap-3">
          <div className="wound-tools" role="group" aria-label="Ferramenta do boneco">
            {TOOLS.map(([t, l]) => (
              <button key={l} type="button" className="btn-secondary min-h-[40px]" aria-pressed={tool === t} onClick={() => setTool(t)}>{l}</button>
            ))}
          </div>
          {tool && (
            <>
              <div className="flex flex-wrap items-end gap-3">
                <label className="label">
                  Gravidade
                  <span className="mt-1 flex items-center gap-2">
                    {tool === 'IMPACT' && <span aria-hidden="true" className="inline-block h-5 w-5 rounded-full border-2 border-[var(--line)]" style={{ background: BRUISE[severity] }} />}
                    <select className="field min-h-[40px]" value={severity} onChange={(e) => setSeverity(e.target.value)}>
                      {SEVERITIES.map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                    </select>
                  </span>
                </label>
                <label className="flex min-h-[40px] items-center gap-2 text-sm">
                  <input type="checkbox" checked={rule} onChange={(e) => setRule(e.target.checked)} />
                  Seguir a regra de Ferimentos Permanentes
                </label>
              </div>
              {rule && (
                <div className="grid gap-2 sm:grid-cols-2">
                  <Pick lbl="Tipo de dano" val={ruleForm.type || RULE_TYPE[tool]} set={(v) => setRuleForm({ ...ruleForm, type: v })} opts={INJURY_TYPES} />
                  <label className="label">
                    Região
                    <input maxLength={100} className="field mt-1 w-full min-h-[40px]" placeholder="Parte desenhada"
                      value={ruleForm.region} onChange={(e) => setRuleForm({ ...ruleForm, region: e.target.value })} />
                  </label>
                  <label className="label">
                    Dano (só contusão)
                    <input type="number" min={0} className="field mt-1 w-full min-h-[40px]" value={ruleForm.damage}
                      onChange={(e) => setRuleForm({ ...ruleForm, damage: e.target.value })} />
                  </label>
                  <Pick lbl="Atributo afetado" blank val={ruleForm.affectedAttribute}
                    set={(v) => setRuleForm({ ...ruleForm, affectedAttribute: v })} opts={ATTRS.map((a) => [a.enumName, a.label])} />
                </div>
              )}
              <p className="hint">
                {tool === 'IMPACT' || tool === 'HOLE' ? 'Toque na parte e arraste para definir o tamanho.' : 'Arraste sobre a parte para traçar.'}
                {' '}{rule ? 'Cria também o Ferimento Permanente (reduz HP máximo).' : 'Só visual: não muda a ficha.'}
              </p>
            </>
          )}
        </div>
      )}

      <div className="flex flex-col gap-4 sm:flex-row">
        {showBody && (
          <div className="cond-host relative mx-auto w-full max-w-[280px] shrink-0">
            <AuraLayers keys={conditions} />
            <BodyMap injuries={all} missingParts={missing} wounds={wounds} selected={sel} onSelect={choose}
              tool={isMaster ? tool : null} severity={severity} onDraw={draw} onPoint={point} />
          </div>
        )}
        <div className="flex min-w-0 flex-1 flex-col gap-3" aria-live="polite">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="font-display text-lg">{sel ? BODY_PART_LABELS[sel] : 'Todos os ferimentos'}</h3>
            {sel && <button type="button" className="btn-ghost min-h-[44px]" onClick={() => choose(sel)}>Ver todos</button>}
          </div>
          {digits.length > 0 && (
            <div className="flex flex-wrap gap-1" role="group" aria-label="Dedos">
              <button type="button" className="tag" aria-pressed={sel === limbOf(sel)} onClick={() => setSel(limbOf(sel))}>
                {BODY_PART_LABELS[limbOf(sel)]}
              </button>
              {digits.map((d) => (
                <button key={d} type="button" className={sel === d ? 'tag tag-accent' : 'tag'} aria-pressed={sel === d} onClick={() => setSel(d)}>
                  {BODY_PART_LABELS[d].replace(/ \(.*\)$/, '')}{missing.includes(d) ? ' ✕' : ''}
                </button>
              ))}
            </div>
          )}
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
          {marks.length > 0 && (
            <div className="flex flex-col gap-1">
              <p className="caption self-start">Marcas desenhadas</p>
              <ul className="flex flex-wrap gap-2 text-sm">
                {marks.map((w) => (
                  <li key={w.id} className="tag flex items-center gap-2">
                    {TOOL_LABEL[w.type]}{w.severity ? ` · ${SEV[w.severity]}` : ''}
                    {isMaster && (
                      <button type="button" className="underline" disabled={busy} aria-label={`Apagar ${TOOL_LABEL[w.type]}`}
                        onClick={() => run(() => removeWound(sheet.id, w.id), 'Marca apagada.')}>apagar</button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
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
              <p className="hint">Parte perdida leva junto o que vem depois dela (ex.: antebraço leva a mão). Só visual.</p>
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
    </div>
  );
}
