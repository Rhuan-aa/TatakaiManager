import { useState } from 'react';
import { expiresLabel, CLASS_LABEL, HYSTERIA, hasCondition, conditionNote, MARTIAL_TYPE_LABEL, GUARD_LABEL, LEAD_FOOT_LABEL } from './combatLabels';

function Bar({ name, value, max, color }) {
  const pct = max > 0 ? Math.max(0, Math.min(100, (value / max) * 100)) : 0;
  return (
    <div className="flex items-center gap-2 text-[11px]">
      <span className="min-w-6 shrink-0 whitespace-nowrap">{name}</span>
      <div className="meter flex-1" style={{ height: 8, borderWidth: 2 }} role="meter" aria-label={name}
        aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
        <div className="meter-fill" style={{ width: `${pct}%`, '--meter-color': color }} />
      </div>
      <span className="w-14 shrink-0 text-right">{value}/{max}</span>
    </div>
  );
}

function AdrenalineMeter({ value }) {
  const pct = Math.max(0, Math.min(100, value * 10));
  return (
    <div className="flex items-center gap-2 text-[11px]">
      <span className="shrink-0 whitespace-nowrap">Adrenalina</span>
      <div className="relative flex-1">
        <div className="meter" style={{ height: 8, borderWidth: 2 }} role="meter" aria-label="Adrenalina"
          aria-valuenow={value} aria-valuemin={0} aria-valuemax={10}>
          <div className="meter-fill" style={{ width: `${pct}%`, '--meter-color': 'var(--highlight)' }} />
        </div>
        <span className="absolute top-0 h-2 border-l-2 border-[var(--line)]" style={{ left: '50%' }} aria-hidden />
        <span className="absolute -top-0.5 right-0 h-3 border-r-2 border-[var(--line)]" aria-hidden />
        <div className="relative mt-0.5 h-3 text-[10px] opacity-80">
          <span className="absolute -translate-x-1/2" style={{ left: '50%' }}>5 Fluxo</span>
          <span className="absolute right-0">10 Ápice</span>
        </div>
      </div>
      <span className="w-14 shrink-0 text-right">{value}/10</span>
    </div>
  );
}

function Dots({ label, n }) {
  return (
    <span className="text-[11px]" aria-label={`${label} ${n} de 3`}>
      {label} {[0, 1, 2].map((i) => (i < n ? '●' : '○')).join('')}
    </span>
  );
}

function VitalsAdjust({ onSubmit }) {
  const [resource, setResource] = useState('HP');
  const [delta, setDelta] = useState('');
  const [reason, setReason] = useState('');
  return (
    <form className="flex flex-wrap items-center gap-1 text-[11px]" onSubmit={(e) => {
      e.preventDefault();
      if (!delta || Number(delta) === 0 || !reason.trim()) return;
      onSubmit({ resource, delta: Number(delta), reason: reason.trim() });
      setDelta(''); setReason('');
    }}>
      <select className="field !w-auto !py-0.5 text-xs" aria-label="Recurso" value={resource} onChange={(e) => setResource(e.target.value)}>
        <option value="HP">HP</option><option value="SP">SP</option><option value="KP">PQ</option>
      </select>
      <input type="number" className="field !w-16 !py-0.5 text-xs" placeholder="±" aria-label="Valor" value={delta} onChange={(e) => setDelta(e.target.value)} />
      <input className="field !w-32 !py-0.5 text-xs" maxLength={100} placeholder="Motivo" aria-label="Motivo" value={reason} onChange={(e) => setReason(e.target.value)} />
      <button type="submit" className="btn-ghost text-xs" disabled={!delta || !reason.trim()}>Ajustar</button>
    </form>
  );
}

function NumberAdjust({ label, value, onSave }) {
  const [v, setV] = useState(value ?? '');
  return (
    <label className="flex items-center gap-1 text-[11px]">
      {label}
      <input type="number" className="field !w-16 !py-0.5 text-xs" value={v}
        onChange={(e) => setV(e.target.value)}
        onBlur={() => v !== '' && Number(v) !== value && onSave(Number(v))} />
    </label>
  );
}

export default function InitiativeTracker({ enc, isMaster, canControl = () => false, onHysteria, onVitals, onAdjust, onReroll, onRemove, onRemoveCondition, onPickCondition, onCounter }) {
  const setup = enc.status === 'SETUP';
  return (
    <ol className="space-y-3">
      {enc.participants.map((p) => {
        const current = p.id === enc.currentParticipantId && enc.status === 'ACTIVE';
        const r = p.resources;
        return (
          <li key={p.id} data-participant-id={p.id} className="panel p-3"
            style={current ? { outline: '4px solid var(--accent)', outlineOffset: 2 } : undefined}
            aria-current={current ? 'step' : undefined}>
            <div className="flex items-center justify-between gap-2">
              <div className="min-w-0">
                <span className="panel-title text-base">{p.name}</span>
                {p.npc && <span className="tag ml-2">NPC</span>}
                {current && <span className="tag tag-accent ml-2">Turno atual</span>}
              </div>
              <span className="tag shrink-0" title="Iniciativa">Ini {p.initiative ?? '—'}</span>
            </div>

            <div className="mt-2 flex flex-wrap items-center gap-1 text-[11px]">
              {isMaster ? (
                <select className="field !w-auto !py-0 text-[11px]" aria-label={`Classificação de ${p.name}`} value={p.classification ?? 'REAL'}
                  onChange={(e) => onAdjust(p.id, { classification: e.target.value })}>
                  {Object.entries(CLASS_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
              ) : p.classification && <span className="tag">{CLASS_LABEL[p.classification]}</span>}
              <span className="tag" title="Time">Time {p.team ?? '—'}</span>
              {p.openWounds > 0 && <span className="tag tag-accent" title="Feridas abertas (sangramento)">Feridas {p.openWounds}</span>}
              {hasCondition(p, 'MORTO') && <span className="tag tag-accent">Morto</span>}
              {hasCondition(p, 'INCONSCIENTE') && (
                <span className="tag tag-accent">Inconsciente{conditionNote(p, 'INCONSCIENTE') ? ` (${conditionNote(p, 'INCONSCIENTE')})` : ''}</span>
              )}
              {(p.exhausted || hasCondition(p, 'EXAUSTO')) && <span className="tag tag-accent">Exausto</span>}
              {hasCondition(p, 'DISSOCIADO') && <span className="tag tag-accent">Dissociado</span>}
              {hasCondition(p, 'BEIRA_DA_MORTE') && (
                <>
                  <span className="tag tag-accent">Beira da Morte</span>
                  <Dots label="Sucessos" n={p.deathSuccesses ?? 0} />
                  <Dots label="Falhas" n={p.deathFailures ?? 0} />
                </>
              )}
            </div>

            {!setup && p.adrenaline != null && (
              <div className="mt-2 space-y-1">
                <AdrenalineMeter value={p.adrenaline} />
                {p.adrenalineTier === 'APEX' && (
                  <label className="flex flex-wrap items-center gap-1 text-[11px]">Histeria
                    {canControl(p) ? (
                      <select className="field !w-auto !py-0 text-[11px]" value={p.hysteria ?? ''}
                        onChange={(e) => e.target.value && onHysteria(p.id, e.target.value)}>
                        <option value="">Escolha…</option>
                        {Object.entries(HYSTERIA).map(([k, h]) => <option key={k} value={k}>{h.label} — {h.text}</option>)}
                      </select>
                    ) : <span className="tag">{p.hysteria ? HYSTERIA[p.hysteria].label : 'não escolhida'}</span>}
                    {p.hysteria && <span className="opacity-80">{HYSTERIA[p.hysteria].text}</span>}
                  </label>
                )}
              </div>
            )}

            {!setup && (
              <div className="mt-2 flex items-center gap-1" aria-label={`PA ${p.paCurrent}`}>
                <span className="mr-1 text-[11px]">PA</span>
                {Array.from({ length: Math.max(5, p.paCurrent) }, (_, i) => (
                  <span key={i} className="inline-block h-3 w-3 border-2 border-[var(--line)]"
                    style={{ background: i < p.paCurrent ? 'var(--accent)' : 'transparent' }} />
                ))}
                <span className="ml-1 text-[11px]">{p.paCurrent}</span>
              </div>
            )}

            {r && (
              <div className="mt-2 space-y-1">
                <Bar name="HP" value={r.currentHp} max={r.hpMax} color="var(--meter-hp)" />
                <Bar name="SP" value={r.currentSp} max={r.spMax} color="var(--meter-sp)" />
                <Bar name="PQ" value={r.currentKp} max={r.kpMax} color="var(--meter-kp)" />
                {(r.physicalFatigue != null || r.mentalFatigue != null) && (
                  <div className="text-[11px]">Desgaste: Físico {r.physicalFatigue ?? 0} · Mental {r.mentalFatigue ?? 0}</div>
                )}
              </div>
            )}

            {(p.martialType || p.guard || p.mainArtName || p.counters?.length > 0) && (
              <details className="mt-2" open={p.counters?.length > 0}>
                <summary className="caption cursor-pointer">
                  Estilo{p.martialType ? ` · ${MARTIAL_TYPE_LABEL[p.martialType]}` : ''}
                </summary>
                <div className="mt-1 space-y-1 text-[11px]">
                  <div className="flex flex-wrap items-center gap-1">
                    {p.martialType && <span className="tag tag-accent" title="Tipo marcial">{MARTIAL_TYPE_LABEL[p.martialType]}</span>}
                    {p.arts ? (
                      <select className="field !w-auto !py-0 text-[11px]" aria-label={`Arte principal de ${p.name}`} value={p.mainArtId ?? ''}
                        onChange={(e) => e.target.value && onAdjust(p.id, { mainArtId: e.target.value })}>
                        <option value="">Arte principal…</option>
                        {p.arts.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
                      </select>
                    ) : p.mainArtName && <span className="tag">{p.mainArtName}</span>}
                    {p.guard && <span className="tag">Guarda {GUARD_LABEL[p.guard]}</span>}
                    {p.leadFoot && <span className="tag">{LEAD_FOOT_LABEL[p.leadFoot]}</span>}
                    {p.activeBaseName && <span className="tag">Base: {p.activeBaseName}</span>}
                  </div>
                  {p.counters?.map((c) => {
                    const tgt = enc.participants.find((x) => x.id === c.targetParticipantId);
                    return (
                      <div key={c.id} className="flex items-center gap-1">
                        <div className="flex-1"><Bar name={c.name + (tgt ? ` (${tgt.name})` : '')} value={c.value} max={c.max} color="var(--highlight)" /></div>
                        {canControl(p) && (
                          <>
                            <button type="button" className="btn-ghost px-1 text-xs" aria-label={`Diminuir ${c.name}`}
                              disabled={c.value <= c.min} onClick={() => onCounter(c.id, c.value - 1)}>−</button>
                            <button type="button" className="btn-ghost px-1 text-xs" aria-label={`Aumentar ${c.name}`}
                              disabled={c.value >= c.max} onClick={() => onCounter(c.id, c.value + 1)}>+</button>
                          </>
                        )}
                      </div>
                    );
                  })}
                </div>
              </details>
            )}

            {p.conditions.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {p.conditions.map((c) => (
                  <span key={c.id} className="tag tag-accent" title={c.note || undefined}>
                    {c.name}{c.intensity ? ` ${c.intensity}` : ''} · {expiresLabel(c)}
                    {isMaster && (
                      <button type="button" className="ml-1 font-bold" aria-label={`Remover ${c.name}`}
                        onClick={() => onRemoveCondition(c.id)}>×</button>
                    )}
                  </span>
                ))}
              </div>
            )}

            {isMaster && enc.status !== 'ENDED' && (
              <div className="mt-2 flex flex-wrap items-center gap-2 border-t-2 border-[var(--line)] pt-2">
                <NumberAdjust key={`i${p.initiative}`} label="Ini" value={p.initiative}
                  onSave={(v) => onAdjust(p.id, { initiative: v })} />
                <NumberAdjust key={`t${p.team}`} label="Time" value={p.team} onSave={(v) => onAdjust(p.id, { team: v })} />
                {!setup && p.adrenaline != null && (
                  <NumberAdjust key={`a${p.adrenaline}`} label="Adr" value={p.adrenaline}
                    onSave={(v) => onAdjust(p.id, { adrenaline: Math.max(0, Math.min(10, v)) })} />
                )}
                {!setup && (
                  <NumberAdjust key={`p${p.paCurrent}`} label="PA" value={p.paCurrent}
                    onSave={(v) => onAdjust(p.id, { paCurrent: v })} />
                )}
                <button type="button" className="btn-ghost text-xs" onClick={() => onReroll(p.id)}>Rerrolar</button>
                <button type="button" className="btn-ghost text-xs" onClick={() => onPickCondition(p)}>+ Condição</button>
                <button type="button" className="btn-ghost text-xs" onClick={() => onRemove(p.id)}>Remover</button>
                {!setup && r && <VitalsAdjust onSubmit={(b) => onVitals(p.id, b)} />}
              </div>
            )}
          </li>
        );
      })}
    </ol>
  );
}
