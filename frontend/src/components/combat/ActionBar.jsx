import { useState } from 'react';
import { ACTIONS, ACTION_STANCH, GUARD_LABEL, hasCondition } from './combatLabels';

export default function ActionBar({ participant: p, participants = [], canAct, onAct, onAttack }) {
  const [spCost, setSpCost] = useState(1);
  const [label, setLabel] = useState('');
  const [slow, setSlow] = useState(false);
  const [target, setTarget] = useState('');
  const [guard, setGuard] = useState('');
  const [base, setBase] = useState('');
  const basePa = p.activeBaseId && base && base !== '__none' ? 2 : 1;
  const sp = p.resources?.currentSp;
  const dissociated = hasCondition(p, 'DISSOCIADO');
  const exhausted = p.exhausted || hasCondition(p, 'EXAUSTO');
  const flow = p.adrenalineTier === 'FLOW' || p.adrenalineTier === 'APEX';
  const allies = participants.filter((x) => x.id !== p.id && x.team === p.team);
  // custo exibido: Fluxo/Ápice -1 (mín. 0); Dissociado dobra técnicas (Ação maior/Preparada)
  const effSp = (a) => {
    const base = Math.max(0, spCost - (flow ? 1 : 0));
    return dissociated && (a.action === 'MAJOR' || a.action === 'PREPARED') ? base * 2 : base;
  };

  function disabledReason(a) {
    if (!canAct) return 'Não é o seu turno';
    const pa = a.action === 'MAJOR' && slow ? 4 : a.pa;
    if (p.paCurrent < pa) return `PA insuficiente (${p.paCurrent}/${pa})`;
    if (dissociated && (a.action === 'MINOR' || a.action === 'MINOR_OFFENSIVE')) return 'Dissociado: sem Ações Menores';
    if (a.sp && exhausted && effSp(a) > 0) return 'Exausto: sem ações com custo de SP';
    if (a.sp && sp != null && sp < effSp(a)) return `SP insuficiente (${sp}/${effSp(a)})`;
    return null;
  }

  const stanchReason = !canAct ? 'Não é o seu turno'
    : p.paCurrent < 3 ? `PA insuficiente (${p.paCurrent}/3)`
    : (hasCondition(p, 'AGARRADO') || hasCondition(p, 'IMOBILIZADO')) ? 'Agarrado/Imobilizado' : null;

  return (
    <div className="panel p-3">
      <div className="mb-2 flex flex-wrap items-center gap-3">
        <span className="panel-title text-base">Ações de {p.name}</span>
        <label className="flex items-center gap-1 text-xs">Custo SP
          <input type="number" min={0} className="field !w-16 !py-0.5" value={spCost}
            onChange={(e) => setSpCost(Math.max(0, Number(e.target.value)))} />
        </label>
        <label className="flex items-center gap-1 text-xs">Descrição
          <input className="field !w-40 !py-0.5" maxLength={100} value={label} placeholder="Técnica X"
            onChange={(e) => setLabel(e.target.value)} />
        </label>
        <label className="flex items-center gap-1 text-xs">Estancar em
          <select className="field !w-auto !py-0.5" value={target} onChange={(e) => setTarget(e.target.value)}>
            <option value="">Si mesmo</option>
            {allies.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
          </select>
        </label>
        <label className="flex items-center gap-1 text-xs">
          <input type="checkbox" checked={slow} onChange={(e) => setSlow(e.target.checked)} /> Lenta (maior: 4 PA)
        </label>
      </div>
      <div className="flex flex-wrap gap-2">
        <button type="button" className="btn-primary px-3 py-1 text-xs" disabled={!canAct} onClick={onAttack}>Atacar</button>
        {ACTIONS.map((a) => {
          const reason = disabledReason(a);
          const pa = a.action === 'MAJOR' && slow ? 4 : a.pa;
          return (
            <button key={a.action} type="button" className="btn-secondary px-3 py-1 text-xs"
              disabled={!!reason} title={reason ?? undefined}
              onClick={() => onAct({
                participantId: p.id, action: a.action,
                ...(a.sp ? { spCost } : {}),
                ...(a.action === 'MAJOR' && slow ? { slow: true } : {}),
                ...(label.trim() ? { label: label.trim() } : {}),
              })}>
              {a.label} · {pa} PA{a.sp ? ` · ${effSp(a)} SP` : ''}
            </button>
          );
        })}
        <button type="button" className="btn-secondary px-3 py-1 text-xs" disabled={!!stanchReason} title={stanchReason ?? undefined}
          onClick={() => onAct({ participantId: p.id, action: 'STANCH', ...(target ? { targetParticipantId: target } : {}) })}>
          {ACTION_STANCH.label} · {ACTION_STANCH.pa} PA
        </button>
        <span className="flex items-center gap-1">
          <select className="field !w-auto !py-0.5 text-xs" aria-label="Nova guarda" value={guard} onChange={(e) => setGuard(e.target.value)}>
            <option value="">Guarda…</option>
            {Object.entries(GUARD_LABEL).filter(([k]) => k !== p.guard).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <button type="button" className="btn-secondary px-3 py-1 text-xs" disabled={!guard || !canAct || p.paCurrent < 1}
            title="Reinicia o Timing (ambidestro: 1ª troca do turno custa 0)"
            onClick={() => { onAct({ participantId: p.id, action: 'CHANGE_GUARD', guard }); setGuard(''); }}>
            Trocar guarda · 1 PA
          </button>
        </span>
        {p.bases?.length > 0 && (
          <span className="flex items-center gap-1">
            <select className="field !w-auto !py-0.5 text-xs" aria-label="Nova base" value={base} onChange={(e) => setBase(e.target.value)}>
              <option value="">Base…</option>
              {p.activeBaseId && <option value="__none">(sair da base)</option>}
              {p.bases.filter((b) => b.id !== p.activeBaseId).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
            <button type="button" className="btn-secondary px-3 py-1 text-xs" disabled={!base || !canAct || p.paCurrent < basePa}
              title="Entrar ou sair: 1 PA · trocar de base: 2 PA"
              onClick={() => { onAct({ participantId: p.id, action: 'CHANGE_BASE', baseId: base === '__none' ? null : base }); setBase(''); }}>
              {!p.activeBaseId ? 'Entrar na base' : base === '__none' ? 'Sair da base' : 'Trocar base'} · {basePa} PA
            </button>
          </span>
        )}
      </div>
    </div>
  );
}
