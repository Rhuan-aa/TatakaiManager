import { useState } from 'react';
import { ACTIONS, ACTION_CATEGORIES, ACTION_STANCH, GUARD_LABEL, hasCondition } from './combatLabels';

/** Botão de ação do HUD: rótulo, custo e, se indisponível, o motivo visível embaixo. */
function ActButton({ label, cost, reason, onClick, title }) {
  return (
    <div>
      <button type="button" className="act-btn" disabled={!!reason} title={reason ?? title} onClick={onClick}>
        <span>{label}</span><span className="act-cost">{cost}</span>
      </button>
      {reason && <p className="mt-0.5 text-[0.7rem] opacity-75">{reason}</p>}
    </div>
  );
}

/** Menu lateral de ações do participante da vez, agrupado por tipo (Maior/Menor/Movimento/Livre). */
export default function ActionBar({ participant: p, participants = [], canAct, onAct, onAttack }) {
  const [spCost, setSpCost] = useState(1);
  const [label, setLabel] = useState('');
  const [slow, setSlow] = useState(false);
  const [target, setTarget] = useState('');
  const [guard, setGuard] = useState('');
  const [base, setBase] = useState('');
  const [dyingTarget, setDyingTarget] = useState('');
  const [medical, setMedical] = useState(false);
  const basePa = p.activeBaseId && base && base !== '__none' ? 2 : 1;
  const sp = p.resources?.currentSp;
  const dissociated = hasCondition(p, 'DISSOCIADO');
  const exhausted = p.exhausted || hasCondition(p, 'EXAUSTO');
  const flow = p.adrenalineTier === 'FLOW' || p.adrenalineTier === 'APEX';
  const allies = participants.filter((x) => x.id !== p.id && x.team === p.team);
  const dying = allies.filter((x) => hasCondition(x, 'BEIRA_DA_MORTE'));
  const stabilizeId = dyingTarget || dying[0]?.id;
  // custo exibido: Fluxo/Ápice -1 (mín. 0); Dissociado dobra técnicas (Ação maior/Preparada)
  const effSp = (a) => {
    const v = Math.max(0, spCost - (flow ? 1 : 0));
    return dissociated && (a.action === 'MAJOR' || a.action === 'PREPARED') ? v * 2 : v;
  };
  const paralyzed = hasCondition(p, 'PARALISADO');
  const paReason = (pa) => (!canAct ? 'Não é o seu turno' : paralyzed ? 'Paralisado: nenhuma ação'
    : p.paCurrent < pa ? `PA insuficiente (${p.paCurrent}/${pa})` : null);

  function disabledReason(a) {
    const pa = a.action === 'MAJOR' && slow ? 4 : a.pa;
    const r = paReason(pa);
    if (r) return r;
    if (dissociated && (a.action === 'MINOR' || a.action === 'MINOR_OFFENSIVE')) return 'Dissociado: sem Ações Menores';
    if (a.sp && exhausted && effSp(a) > 0) return 'Exausto: sem ações com custo de SP';
    if (a.sp && sp != null && sp < effSp(a)) return `SP insuficiente (${sp}/${effSp(a)})`;
    return null;
  }

  const stanchReason = paReason(3)
    ?? ((hasCondition(p, 'AGARRADO') || hasCondition(p, 'IMOBILIZADO')) ? 'Agarrado/Imobilizado' : null);

  const act = (a) => onAct({
    participantId: p.id, action: a.action,
    ...(a.sp ? { spCost } : {}),
    ...(a.action === 'MAJOR' && slow ? { slow: true } : {}),
    ...(label.trim() ? { label: label.trim() } : {}),
  });

  // ações situacionais (ex.: Apagar chamas) só aparecem quando a condição está presente
  const actionButtons = (cat) => ACTIONS.filter((a) => a.cat === cat && (!a.needs || hasCondition(p, a.needs))).map((a) => {
    const pa = a.action === 'MAJOR' && slow ? 4 : a.pa;
    return <ActButton key={a.action} label={a.label} reason={disabledReason(a)} onClick={() => act(a)}
      cost={`${pa} PA${a.sp ? ` · ${effSp(a)} SP` : ''}`} />;
  });

  // extras de cada grupo além das ações simples
  const extras = {
    MAJOR: (
      <>
        <ActButton label="Atacar" cost="3 PA" reason={paReason(3)} onClick={() => onAttack('MAJOR')} />
        {(p.preparations ?? []).map((t) => (
          <ActButton key={`${t.techniqueId}:${t.variation ?? ''}`} label={`${t.name} (Preparação)`} cost={`3 PA · ${t.spCost} SP`}
            title="Prepara o corpo até o início do próximo turno"
            reason={paReason(3) ?? (sp != null && sp < t.spCost ? `SP insuficiente (${sp}/${t.spCost})` : exhausted ? 'Exausto: sem técnicas' : null)}
            onClick={() => onAct({ participantId: p.id, action: 'PREPARED', techniqueId: t.techniqueId, variation: t.variation })} />
        ))}
        <ActButton label={ACTION_STANCH.label} cost={`${ACTION_STANCH.pa} PA`} reason={stanchReason}
          onClick={() => onAct({ participantId: p.id, action: 'STANCH', ...(target ? { targetParticipantId: target } : {}) })} />
        {allies.length > 0 && (
          <label className="flex items-center gap-1 text-xs">Estancar em
            <select className="field !mt-0 !w-auto !py-0.5" value={target} onChange={(e) => setTarget(e.target.value)}>
              <option value="">Si mesmo</option>
              {allies.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
            </select>
          </label>
        )}
        {dying.length > 0 && (
          <>
            <ActButton label="Estabilizar" cost="3 PA" title="INT vs ND 12 (+3 a cada falha); vantagem com kit médico"
              reason={paReason(3)}
              onClick={() => onAct({ participantId: p.id, action: 'STABILIZE', targetParticipantId: stabilizeId, medical })} />
            <div className="flex flex-wrap items-center gap-2 text-xs">
              {dying.length > 1 && (
                <select className="field !mt-0 !w-auto !py-0.5" aria-label="Aliado a estabilizar" value={stabilizeId}
                  onChange={(e) => setDyingTarget(e.target.value)}>
                  {dying.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
                </select>
              )}
              {dying.length === 1 && <span>em {dying[0].name}</span>}
              <label className="flex items-center gap-1">
                <input type="checkbox" checked={medical} onChange={(e) => setMedical(e.target.checked)} /> Kit médico / medicina
              </label>
            </div>
          </>
        )}
        <div className="flex flex-wrap items-center gap-3 text-xs">
          <label className="flex items-center gap-1">Custo SP
            <input type="number" min={0} className="field !mt-0 !w-14 !py-0.5" value={spCost}
              onChange={(e) => setSpCost(Math.max(0, Number(e.target.value)))} />
          </label>
          <label className="flex items-center gap-1">
            <input type="checkbox" checked={slow} onChange={(e) => setSlow(e.target.checked)} /> Lenta (4 PA)
          </label>
        </div>
      </>
    ),
    MINOR: (
      <>
        <ActButton label="Golpe rápido" cost="2 PA" title="Ataque como Ação Menor ofensiva (Técnica Rápida ou Soco)"
          reason={paReason(2) ?? (dissociated ? 'Dissociado: sem Ações Menores' : null)} onClick={() => onAttack('MINOR_OFFENSIVE')} />
        <select className="field !mt-0 !py-0.5 text-xs" aria-label="Nova guarda" value={guard} onChange={(e) => setGuard(e.target.value)}>
          <option value="">Guarda… (atual: {GUARD_LABEL[p.guard] ?? '—'})</option>
          {Object.entries(GUARD_LABEL).filter(([k]) => k !== p.guard).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <ActButton label="Trocar guarda" cost="1 PA" title="Reinicia o Timing (ambidestro: 1ª troca do turno custa 0)"
          reason={paReason(1) ?? (!guard ? 'Escolha a guarda' : null)}
          onClick={() => { onAct({ participantId: p.id, action: 'CHANGE_GUARD', guard }); setGuard(''); }} />
        {p.bases?.length > 0 && (
          <>
            <select className="field !mt-0 !py-0.5 text-xs" aria-label="Nova base" value={base} onChange={(e) => setBase(e.target.value)}>
              <option value="">Base… (atual: {p.activeBaseName ?? 'nenhuma'})</option>
              {p.activeBaseId && <option value="__none">(sair da base)</option>}
              {p.bases.filter((b) => b.id !== p.activeBaseId).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
            <ActButton label={!p.activeBaseId ? 'Entrar na base' : base === '__none' ? 'Sair da base' : 'Trocar base'}
              cost={`${basePa} PA`} title="Entrar ou sair: 1 PA · trocar de base: 2 PA"
              reason={paReason(basePa) ?? (!base ? 'Escolha a base' : null)}
              onClick={() => { onAct({ participantId: p.id, action: 'CHANGE_BASE', baseId: base === '__none' ? null : base }); setBase(''); }} />
          </>
        )}
      </>
    ),
  };

  return (
    <div className="panel space-y-4 p-3">
      <div>
        <span className="panel-title text-base">Ações de {p.name}</span>
        <label className="mt-1 block text-xs">Descrição (opcional)
          <input className="field !mt-0.5 !py-0.5" maxLength={100} value={label} placeholder="Técnica X"
            onChange={(e) => setLabel(e.target.value)} />
        </label>
      </div>
      {ACTION_CATEGORIES.map((c) => (
        <section key={c.key} className="act-group space-y-1.5" style={{ '--act': c.color }}>
          <h4 className="act-group-title text-sm">{c.label}</h4>
          {extras[c.key]}
          {actionButtons(c.key)}
        </section>
      ))}
    </div>
  );
}
