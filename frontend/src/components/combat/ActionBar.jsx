import { useState } from 'react';
import { ACTIONS, ACTION_CATEGORIES, ACTION_STANCH, GUARD_LABEL, GRIP_LABEL, HOLD_KEYS, STRANGLE_LABEL, hasCondition } from './combatLabels';

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
/** Agarre (§39): fuga de quem está preso e golpes de quem agarra, a partir das condições com `sourceParticipantId`. */
function GrappleGroup({ p, participants, paReason, onGrapple }) {
  const [escAttr, setEscAttr] = useState('STR');
  const [strangle, setStrangle] = useState('BLOOD');
  const [twist, setTwist] = useState(false);
  const sp = p.resources?.currentSp;
  const spReason = (n) => (n && sp != null && sp < n ? `SP insuficiente (${sp}/${n})` : null);
  const send = (body) => onGrapple({ participantId: p.id, ...body });

  // fuga: a condição mais forte que prende p (espelha CombatService.escape)
  const hold = HOLD_KEYS.map((k) => p.conditions.find((c) => c.key === k && c.sourceParticipantId)).find(Boolean);
  const holder = hold && participants.find((x) => x.id === hold.sourceParticipantId);
  const escCost = hold?.key === 'IMOBILIZADO' ? 3 : 1;
  const dexOk = hold && (hold.key === 'IMOBILIZADO'
    || (hold.key !== 'CLINCH' && ['TAKEDOWN', 'SWEEP'].includes(p.martialType)));

  // vítimas de p: condições de agarre nos outros com origem em p
  const victims = participants.filter((t) => t.id !== p.id)
    .map((t) => ({ t, cs: t.conditions.filter((c) => c.sourceParticipantId === p.id && HOLD_KEYS.includes(c.key)) }))
    .filter((v) => v.cs.length > 0);

  if (!hold && victims.length === 0) return null;
  return (
    <section className="act-group space-y-1.5" style={{ '--act': 'var(--act-major)' }}>
      <h4 className="act-group-title text-sm">Agarre</h4>
      {hold && (
        <>
          <ActButton label={`Escapar de ${holder?.name ?? '?'}`} cost={`${escCost} PA`}
            title={`${hold.name}: competição contra quem agarra`}
            reason={paReason(escCost)}
            onClick={() => send({ action: 'ESCAPE', attribute: dexOk ? escAttr : 'STR' })} />
          {dexOk && (
            <select className="field !mt-0 !py-0.5 text-xs" aria-label="Atributo da fuga" value={escAttr}
              onChange={(e) => setEscAttr(e.target.value)}>
              <option value="STR">com Força</option><option value="DEX">com Destreza</option>
            </select>
          )}
        </>
      )}
      {victims.map(({ t, cs }) => {
        const grab = cs.find((c) => c.key === 'AGARRADO');
        const initiated = cs.some((c) => c.key === 'CLINCH' && c.note === 'INICIOU');
        const downed = hasCondition(t, 'DERRUBADO');
        const kept = cs.filter((c) => c.key === 'ESTRANGULADO' || (c.key === 'IMOBILIZADO' && c.note === 'CONSTRICAO'));
        const constricted = kept.some((c) => c.key === 'IMOBILIZADO');
        const on = { targetParticipantId: t.id };
        return (
          <div key={t.id} className="space-y-1.5 border-t border-[var(--line)] pt-1.5">
            <p className="text-xs font-bold">{t.name}: {cs.map((c) => c.name + (GRIP_LABEL[c.note] ? ` (${GRIP_LABEL[c.note]})` : '')).join(', ')}</p>
            {(initiated || grab?.note === 'TORSO') && (
              <ActButton label="Projeção" cost="3 PA · 1 SP" title="FOR × melhor de FOR/DES: Derrubado + dano, solta"
                reason={paReason(3) ?? spReason(1)} onClick={() => send({ action: 'PROJECT', ...on })} />
            )}
            {grab?.note === 'LEG' && (
              <ActButton label="Derrubar pela perna" cost="3 PA · 1 SP" title="FOR × FOR: Derrubado"
                reason={paReason(3) ?? spReason(1)} onClick={() => send({ action: 'LEG_TAKEDOWN', ...on })} />
            )}
            {grab && (grab.note === 'ARM' || downed) && (
              <ActButton label="Constrição" cost="3 PA · 1 SP" title="FOR × FOR: Imobilizado (fuga sem desvantagem)"
                reason={paReason(3) ?? spReason(1)} onClick={() => send({ action: 'CONSTRICT', ...on })} />
            )}
            {(initiated || grab?.note === 'NECK') && (
              <>
                <ActButton label="Estrangular" cost="3 PA" reason={paReason(3)}
                  onClick={() => send({ action: 'STRANGLE', strangleType: strangle, ...on })} />
                <select className="field !mt-0 !py-0.5 text-xs" aria-label="Tipo de estrangulamento" value={strangle}
                  onChange={(e) => setStrangle(e.target.value)}>
                  {Object.entries(STRANGLE_LABEL).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
                </select>
              </>
            )}
            {kept.length > 0 && (
              <>
                <ActButton label="Manter o agarre" cost="2 PA · 1 SP" title="Sem manter, estrangulamento/constrição se desfaz no fim do turno"
                  reason={paReason(2) ?? spReason(1)}
                  onClick={() => send({ action: 'MAINTAIN', ...(constricted && twist ? { twist: true } : {}), ...on })} />
                {constricted && (
                  <label className="flex items-center gap-1 text-xs">
                    <input type="checkbox" checked={twist} onChange={(e) => setTwist(e.target.checked)} /> Torcer (1d2 + FOR)
                  </label>
                )}
              </>
            )}
            <ActButton label="Soltar" cost="0 PA" reason={paReason(0)} onClick={() => send({ action: 'RELEASE', ...on })} />
          </div>
        );
      })}
    </section>
  );
}

export default function ActionBar({ participant: p, participants = [], canAct, onAct, onAttack, onGrapple }) {
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
        <ActButton label="Golpe rápido" cost="2 PA" title="Ataque como Ação Menor ofensiva (Técnica Rápida, Soco, Mordida ou Empurrão)"
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
      {onGrapple && <GrappleGroup p={p} participants={participants} paReason={paReason} onGrapple={onGrapple} />}
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
