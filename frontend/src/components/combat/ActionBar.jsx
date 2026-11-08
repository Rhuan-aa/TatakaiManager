import { useState } from 'react';
import { useConsumables, useWeapons } from '../ui/useCatalog';
import { ITEM_LOCATION, gearStatus } from '../ui/gear';
import { StanchMenu, ActionConfigMenu, GuardMenu, BaseMenu } from './ActionMenus';
import { ACTIONS, ACTION_CATEGORIES, ACTION_STANCH, GRIP_LABEL, HOLD_KEYS, STRANGLE_LABEL, hasCondition } from './combatLabels';

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

  const tangled = p.conditions.find((c) => c.key === 'AGARRADO' && c.note === 'ENREDAR' && !c.sourceParticipantId);
  if (!hold && !tangled && victims.length === 0) return null;
  return (
    <section className="act-group break-inside-avoid space-y-1.5" style={{ '--act': 'var(--act-major)' }}>
      <h4 className="act-group-title text-sm">Agarre</h4>
      {tangled && !hold && (
        <>
          <ActButton label="Soltar-se (sem teste)" cost="3 PA" reason={paReason(3)} onClick={() => send({ action: 'UNTANGLE' })} />
          <ActButton label="Soltar-se (teste ND 14)" cost="1 PA" title="FOR ou DES contra ND 14" reason={paReason(1)}
            onClick={() => send({ action: 'ESCAPE', attribute: escAttr })} />
          <select className="field !mt-0 !py-0.5 text-xs" aria-label="Atributo para se soltar" value={escAttr}
            onChange={(e) => setEscAttr(e.target.value)}>
            <option value="STR">com Força</option><option value="DEX">com Destreza</option>
          </select>
        </>
      )}
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

/** Armas e itens do catálogo em combate (§42): sacar/guardar, recarregar, engatilhar, destravar, trocar modo. */
function GearGroup({ p, paReason, onAct }) {
  const weapons = useWeapons();
  const [two, setTwo] = useState(false);
  const items = (p.gear ?? []).filter((i) => i.weaponKey && i.location !== 'WORN');
  if (items.length === 0) return null;
  const send = (action, i, extra = {}) => onAct({ participantId: p.id, action, itemId: i.id, ...extra });
  return (
    <section className="act-group break-inside-avoid space-y-1.5" style={{ '--act': 'var(--highlight)' }}>
      <h4 className="act-group-title text-sm">Armas</h4>
      {items.map((i) => {
        const w = weapons[i.weaponKey];
        const inHand = i.location === 'HAND';
        const status = gearStatus(i, w);
        const reloadPa = w?.reload === 'SLOW' ? 3 : 1;
        return (
          <div key={i.id} className="space-y-1 border-t border-[var(--line)] pt-1.5">
            <p className="text-xs"><strong>{i.name}</strong> · {ITEM_LOCATION[i.location]}{status.length > 0 && ` · ${status.join(', ')}`}</p>
            <ActButton label={inHand ? 'Guardar' : i.location === 'DROPPED' ? 'Pegar do chão' : 'Sacar'} cost="1 PA"
              reason={paReason(1)} onClick={() => send('DRAW', i, w?.diceTwoHands && !inHand ? { twoHanded: two } : {})} />
            {w?.diceTwoHands && !inHand && (
              <label className="flex items-center gap-1 text-xs">
                <input type="checkbox" checked={two} onChange={(e) => setTwo(e.target.checked)} /> Duas mãos ({w.diceTwoHands})
              </label>
            )}
            {inHand && w?.capacity && w.reload !== 'FAST' && i.loaded < w.capacity && (
              <ActButton label="Recarregar" cost={`${reloadPa} PA`} reason={paReason(reloadPa)} onClick={() => send('RELOAD', i)} />
            )}
            {inHand && w?.slowCock && !i.cocked && (
              <ActButton label="Engatilhar" cost="1 PA" reason={paReason(1)} onClick={() => send('COCK', i)} />
            )}
            {i.jammed && <ActButton label="Destravar" cost="3 PA" reason={paReason(3)} onClick={() => send('UNJAM', i)} />}
            {w?.alt && (
              <ActButton label={`Modo: ${weapons[w.alt]?.name ?? w.alt}`} cost="1 PA" reason={paReason(1)}
                onClick={() => send('TOGGLE_MODE', i)} />
            )}
          </div>
        );
      })}
    </section>
  );
}

/** Consumíveis em mãos ou no Acesso Rápido (§44): em si 1 PA, em aliado 3 PA, ofensivo 2 PA, +1 PA no Acesso Rápido. */
function ConsumableGroup({ p, participants, paReason, onAct }) {
  const catalog = useConsumables();
  const [targets, setTargets] = useState({});
  const items = (p.gear ?? []).filter((i) => i.consumableKey && (i.location === 'HAND' || i.location === 'QUICK')
    && catalog[i.consumableKey] && catalog[i.consumableKey].use !== 'NONE');
  if (items.length === 0) return null;
  return (
    <section className="act-group break-inside-avoid space-y-1.5" style={{ '--act': 'var(--act-free)' }}>
      <h4 className="act-group-title text-sm">Consumíveis</h4>
      {items.map((i) => {
        const c = catalog[i.consumableKey];
        const options = c.use === 'OFFENSIVE' ? participants.filter((x) => x.team !== p.team)
          : c.use === 'ALLY' ? participants.filter((x) => x.id !== p.id && x.team === p.team)
            : [p, ...participants.filter((x) => x.id !== p.id && x.team === p.team)];
        const target = targets[i.id] ?? options[0]?.id;
        const self = target === p.id;
        const pa = (c.use === 'OFFENSIVE' ? 2 : self ? 1 : 3) + (i.location === 'QUICK' ? 1 : 0);
        return (
          <div key={i.id} className="space-y-1 border-t border-[var(--line)] pt-1.5">
            <ActButton label={`${c.name} (${i.quantity})`} cost={`${pa} PA`} title={c.text ?? undefined}
              reason={paReason(pa) ?? (!target ? 'Sem alvo' : null)}
              onClick={() => onAct({ participantId: p.id, action: 'USE_ITEM', itemId: i.id,
                ...(target && !self ? { targetParticipantId: target } : {}) })} />
            {options.length > 1 && (
              <select className="field !mt-0 !py-0.5 text-xs" aria-label={`Alvo de ${c.name}`} value={target}
                onChange={(e) => setTargets({ ...targets, [i.id]: e.target.value })}>
                {options.map((x) => <option key={x.id} value={x.id}>{x.id === p.id ? 'Em si' : x.name}</option>)}
              </select>
            )}
          </div>
        );
      })}
    </section>
  );
}

// Ações que abrem um menu de configuração em vez de disparar direto
const CONFIG_KINDS = ['MAJOR', 'PREPARED', 'MINOR', 'FREE'];

export default function ActionBar({ participant: p, participants = [], canAct, onAct, onAttack, onGrapple, bare = false, active = true }) {
  const [menu, setMenu] = useState(null); // null | 'STANCH' | 'GUARD' | 'BASE' | 'MAJOR' | 'PREPARED' | 'MINOR' | 'FREE'
  const [dyingTarget, setDyingTarget] = useState('');
  const [medical, setMedical] = useState(false);
  const [nd, setNd] = useState(12);
  const sp = p.resources?.currentSp;
  const dissociated = hasCondition(p, 'DISSOCIADO');
  const exhausted = p.exhausted || hasCondition(p, 'EXAUSTO');
  const flow = p.adrenalineTier === 'FLOW' || p.adrenalineTier === 'APEX';
  const allies = participants.filter((x) => x.id !== p.id && x.team === p.team);
  const dying = allies.filter((x) => hasCondition(x, 'BEIRA_DA_MORTE'));
  const stabilizeId = dyingTarget || dying[0]?.id;
  // ataque básico custa 1 SP (Fluxo/Ápice reduzem 1)
  const basicSp = Math.max(0, 1 - (flow ? 1 : 0));
  const basicSpReason = sp != null && sp < basicSp ? `SP insuficiente (${sp}/${basicSp})` : null;
  const paralyzed = hasCondition(p, 'PARALISADO');
  const paReason = (pa) => (!canAct ? 'Não é o seu turno' : paralyzed ? 'Paralisado: nenhuma ação'
    : p.paCurrent < pa ? `PA insuficiente (${p.paCurrent}/${pa})` : null);
  const ctx = { p, allies, paReason, sp, flow, dissociated, exhausted };

  function disabledReason(a) {
    const r = paReason(a.pa);
    if (r) return r;
    if (dissociated && (a.action === 'MINOR' || a.action === 'MINOR_OFFENSIVE')) return 'Dissociado: sem Ações Menores';
    // ações com menu conferem o SP no próprio menu (o custo é escolhido lá)
    if (a.sp && !CONFIG_KINDS.includes(a.action)) {
      const eff = Math.max(0, 1 - (flow ? 1 : 0));
      if (exhausted && eff > 0) return 'Exausto: sem ações com custo de SP';
      if (sp != null && sp < eff) return `SP insuficiente (${sp}/${eff})`;
    }
    return null;
  }

  const stanchReason = paReason(3)
    ?? ((hasCondition(p, 'AGARRADO') || hasCondition(p, 'IMOBILIZADO')) ? 'Agarrado/Imobilizado' : null);

  const send = (body) => { onAct({ participantId: p.id, ...body }); setMenu(null); };
  const costLabel = (a) => (a.action === 'MAJOR' ? '3–4 PA · SP' : a.action === 'PREPARED' ? '3 PA · SP'
    : `${a.pa} PA${a.sp ? ' · 1 SP' : ''}`);

  // ações situacionais (ex.: Apagar chamas) só aparecem quando a condição está presente
  const actionButtons = (cat) => ACTIONS.filter((a) => a.cat === cat && (!a.needs || hasCondition(p, a.needs))).map((a) => (
    <ActButton key={a.action} label={a.label} reason={disabledReason(a)} cost={costLabel(a)}
      title={CONFIG_KINDS.includes(a.action) ? 'Abre o menu da ação' : undefined}
      onClick={() => (CONFIG_KINDS.includes(a.action) ? setMenu(a.action) : send({ action: a.action }))} />
  ));

  // extras de cada grupo além das ações simples
  const extras = {
    FREE: (
      <div className="flex items-center gap-2">
        <ActButton label="Teste de Vontade" cost="0 PA" reason={paReason(0)}
          title="1d20 + MEN − Desgaste Mental (Calma da base, Muralha, Estimulante e Fluxo entram sozinhos) contra o ND"
          onClick={() => onAct({ participantId: p.id, action: 'WILL_TEST', nd: Math.min(40, Math.max(1, Number(nd) || 12)) })} />
        <label className="flex items-center gap-1 text-xs">ND
          <input type="number" min={1} max={40} className="field !mt-0 !w-16 !py-0.5" value={nd} onChange={(e) => setNd(e.target.value)} />
        </label>
      </div>
    ),
    MAJOR: (
      <>
        <ActButton label="Atacar" cost={`3 PA · ${basicSp} SP`} reason={paReason(3) ?? basicSpReason} onClick={() => onAttack('MAJOR')} />
        {(p.preparations ?? []).map((t) => (
          <ActButton key={`${t.techniqueId}:${t.variation ?? ''}`} label={`${t.name} (Preparação)`} cost={`3 PA · ${t.spCost} SP`}
            title="Prepara o corpo até o início do próximo turno"
            reason={paReason(3) ?? (sp != null && sp < t.spCost ? `SP insuficiente (${sp}/${t.spCost})` : exhausted ? 'Exausto: sem técnicas' : null)}
            onClick={() => onAct({ participantId: p.id, action: 'PREPARED', techniqueId: t.techniqueId, variation: t.variation })} />
        ))}
        <ActButton label={ACTION_STANCH.label} cost={`${ACTION_STANCH.pa} PA`} reason={stanchReason}
          title="Escolha quem estancar" onClick={() => setMenu('STANCH')} />
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
      </>
    ),
    MINOR: (
      <>
        <ActButton label="Golpe rápido" cost={`2 PA · ${basicSp} SP`} title="Ataque como Ação Menor ofensiva (Técnica Rápida, Soco, Mordida ou Empurrão)"
          reason={paReason(2) ?? (dissociated ? 'Dissociado: sem Ações Menores' : null) ?? basicSpReason}
          onClick={() => onAttack('MINOR_OFFENSIVE')} />
        <ActButton label="Trocar guarda" cost="1 PA" title="Abre a lista de guardas (reinicia o Timing; ambidestro: 1ª troca do turno custa 0)"
          reason={paReason(1)} onClick={() => setMenu('GUARD')} />
        <ActButton label={p.activeBaseId ? 'Trocar / sair da base' : 'Entrar na base'} cost="1–2 PA"
          title="Entrar ou sair: 1 PA · trocar de base: 2 PA"
          reason={paReason(1) ?? (!p.bases?.length ? 'Sem bases na ficha' : null)} onClick={() => setMenu('BASE')} />
      </>
    ),
  };

  const color = (key) => ACTION_CATEGORIES.find((c) => c.key === key)?.color;
  const menuColor = { STANCH: color('MAJOR'), MAJOR: color('MAJOR'), PREPARED: color('MAJOR'), MINOR: color('MINOR'),
    GUARD: color('MINOR'), BASE: color('MINOR'), FREE: color('FREE') };

  return (
    <div className={bare ? '' : 'panel p-3'}>
      <span className="panel-title block text-base">Ações de {p.name}</span>
      {/* bare (pop-up): duas colunas CSS — o próximo grupo começa logo abaixo do anterior, sem linhas altas de grid */}
      <div className={bare ? 'mt-3 columns-1 gap-4 sm:columns-2 [&>section]:mb-4' : 'mt-3 space-y-4'}>
        {onGrapple && <GrappleGroup p={p} participants={participants} paReason={paReason} onGrapple={onGrapple} />}
        <GearGroup p={p} paReason={paReason} onAct={onAct} />
        <ConsumableGroup p={p} participants={participants} paReason={paReason} onAct={onAct} />
        {ACTION_CATEGORIES.map((c) => (
          <section key={c.key} className="act-group break-inside-avoid space-y-1.5" style={{ '--act': c.color }}>
            <h4 className="act-group-title text-sm">{c.label}</h4>
            {extras[c.key]}
            {actionButtons(c.key)}
          </section>
        ))}
      </div>
      {active && menu === 'STANCH' && (
        <StanchMenu ctx={ctx} reason={stanchReason} color={menuColor.STANCH} onCancel={() => setMenu(null)} onSubmit={send} />
      )}
      {active && CONFIG_KINDS.includes(menu) && (
        <ActionConfigMenu ctx={ctx} kind={menu} color={menuColor[menu]} onCancel={() => setMenu(null)} onSubmit={send} />
      )}
      {active && menu === 'GUARD' && <GuardMenu ctx={ctx} color={menuColor.GUARD} onCancel={() => setMenu(null)} onSubmit={send} />}
      {active && menu === 'BASE' && <BaseMenu ctx={ctx} color={menuColor.BASE} onCancel={() => setMenu(null)} onSubmit={send} />}
    </div>
  );
}
