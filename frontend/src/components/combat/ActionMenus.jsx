import { useState } from 'react';
import ActionMenu, { OptionButton } from './ActionMenu';
import { GUARD_LABEL } from './combatLabels';

// Menus de configuração das ações do ActionBar. Cada um monta do zero ao abrir (estado local descartado ao fechar).
// `ctx` = { p, allies, paReason, sp, flow, dissociated, exhausted } calculado no ActionBar.

/** Estancar: escolher si mesmo ou um aliado do mesmo time (o servidor rejeita outro time). */
export function StanchMenu({ ctx, reason, color, onSubmit, onCancel }) {
  const { p, allies } = ctx;
  const [target, setTarget] = useState(p.id);
  return (
    <ActionMenu title="Estancar" color={color} reason={reason} onCancel={onCancel}
      onConfirm={() => onSubmit({ action: 'STANCH', ...(target !== p.id ? { targetParticipantId: target } : {}) })}>
      <p className="text-xs">Custa 3 PA. Quem você vai estancar?</p>
      <OptionButton title="Si mesmo" tag="Você" selected={target === p.id} onClick={() => setTarget(p.id)} />
      {allies.map((x) => (
        <OptionButton key={x.id} title={x.name} tag="Aliado" selected={target === x.id} onClick={() => setTarget(x.id)} />
      ))}
    </ActionMenu>
  );
}

const KIND = {
  FREE: { title: 'Ação Livre', pa: 0 },
  MAJOR: { title: 'Ação Maior', pa: 3 },
  PREPARED: { title: 'Ação Preparada', pa: 3 },
  BREATHE: { title: 'Respirar', pa: 4 },
  MINOR: { title: 'Ação Menor', pa: 1 },
  MINOR_OFFENSIVE: { title: 'Menor ofensiva', pa: 2 },
  SHOVE: { title: 'Atravessar', pa: 1 },
  MOVE: { title: 'Movimento', pa: 1 },
  DISENGAGE: { title: 'Desengajar', pa: 2 },
  STAND_UP: { title: 'Levantar-se', pa: 2 },
};
// Ação Livre que vale por outra ação (spec, passiva, traço): escolhe o grupo e depois o tipo de ação
const FREE_GROUPS = [
  ['FREE', 'Ação livre simples', 'Só registra no histórico (0 PA por padrão).', ['FREE']],
  ['MAJOR', 'Grupo: Ação Maior', 'Por causa de spec, passiva ou traço.', ['MAJOR', 'PREPARED', 'BREATHE']],
  ['MINOR', 'Grupo: Ação Menor', 'Por causa de spec, passiva ou traço.', ['MINOR', 'MINOR_OFFENSIVE', 'SHOVE']],
  ['MOVE', 'Grupo: Movimento', 'Por causa de spec, passiva ou traço.', ['MOVE', 'DISENGAGE', 'STAND_UP']],
];
const PA_CUSTOM = ['MAJOR', 'MINOR', 'FREE', 'MOVE', 'STAND_UP', 'DISENGAGE', 'SHOVE', 'MINOR_OFFENSIVE', 'BREATHE']; // = CombatService.PA_CUSTOM
const SP_KINDS = ['MAJOR', 'PREPARED', 'MINOR', 'FREE', 'MINOR_OFFENSIVE'];
const DICE = /^\d{1,2}d\d{1,3}([+-]\d+)?$/i; // NdM+K, como o servidor (RollService.parse)
const defaultSp = (k) => (k === 'MAJOR' || k === 'PREPARED' || k === 'MINOR_OFFENSIVE' ? 1 : 0);

/**
 * Ação genérica configurável (Maior, Preparada, Menor ou Livre): descrição, custo de PA (Menor 1–2; demais 0–9),
 * custo de SP, Lenta e uma rolagem opcional (expressão NdM+K, rolada e registrada no servidor). Na Ação Livre,
 * escolhe-se o grupo (Maior/Menor/Movimento) e depois a ação desse grupo.
 */
export function ActionConfigMenu({ ctx, kind: initial, color, onSubmit, onCancel }) {
  const { paReason, sp, flow, dissociated, exhausted } = ctx;
  const free = initial === 'FREE';
  const [kind, setKindState] = useState(initial);
  const [group, setGroup] = useState('FREE');
  const [label, setLabel] = useState('');
  const [spCost, setSpCost] = useState(defaultSp(initial));
  const [slow, setSlow] = useState(false);
  const [paInput, setPaInput] = useState(null); // null = custo padrão do tipo
  const [dice, setDice] = useState('');
  const setKind = (k) => { setKindState(k); setSpCost(defaultSp(k)); setPaInput(null); setSlow(false); };
  const groupKinds = FREE_GROUPS.find(([g]) => g === group)[3];
  const custom = PA_CUSTOM.includes(kind);
  const base = kind === 'MAJOR' && slow ? 4 : KIND[kind].pa;
  const pa = custom && paInput != null ? paInput : base;
  const hasSp = SP_KINDS.includes(kind);
  // Fluxo/Ápice -1 SP (mín. 0); Dissociado dobra técnicas
  const effSp = hasSp ? Math.max(0, spCost - (flow ? 1 : 0)) * (dissociated ? 2 : 1) : 0;
  const other = free && kind !== 'FREE';
  const diceTrim = dice.trim();
  const reason = paReason(pa)
    ?? (dissociated && (kind === 'MINOR' || kind === 'MINOR_OFFENSIVE') ? 'Dissociado: sem Ações Menores' : null)
    ?? (effSp > 0 && exhausted ? 'Exausto: sem ações com custo de SP' : null)
    ?? (effSp > 0 && sp != null && sp < effSp ? `SP insuficiente (${sp}/${effSp})` : null)
    ?? (other && !label.trim() ? 'Descreva a ação (spec, passiva ou traço)' : null)
    ?? (diceTrim && !DICE.test(diceTrim) ? 'Expressão inválida: use NdM+K, ex.: 1d20+3' : null);
  const confirm = () => onSubmit({
    action: kind,
    ...(hasSp && spCost > 0 ? { spCost } : {}),
    ...(kind === 'MAJOR' && slow ? { slow: true } : {}),
    ...(custom && paInput != null && paInput !== base ? { paCost: paInput } : {}),
    ...(diceTrim ? { rollExpression: diceTrim } : {}),
    ...(label.trim() ? { label: label.trim() } : {}),
  });

  return (
    <ActionMenu title={KIND[kind].title} color={color} reason={reason} onCancel={onCancel} onConfirm={confirm}>
      {free && FREE_GROUPS.map(([g, t, d, kinds]) => (
        <OptionButton key={g} title={t} detail={d} selected={group === g} onClick={() => { setGroup(g); setKind(kinds[0]); }} />
      ))}
      {free && group !== 'FREE' && (
        <div className="flex flex-wrap gap-2" role="group" aria-label="Tipo de ação do grupo">
          {groupKinds.map((k) => (
            <button key={k} type="button" aria-pressed={kind === k}
              className={kind === k ? 'btn-primary px-2 text-xs' : 'btn-secondary px-2 text-xs'} onClick={() => setKind(k)}>
              {KIND[k].title}
            </button>
          ))}
        </div>
      )}
      <label className="block text-xs">{other ? 'Spec, passiva ou traço' : 'Descrição (opcional)'}
        <input className="field !mt-0.5 !py-0.5" maxLength={100} value={label} placeholder="Técnica X"
          onChange={(e) => setLabel(e.target.value)} />
      </label>
      <div className="flex flex-wrap items-center gap-4 text-xs">
        {custom && (
          <label className="flex items-center gap-2">Custo PA
            {kind === 'MINOR' ? (
              <select className="field !mt-0 !w-auto !py-0.5" value={pa} onChange={(e) => setPaInput(Number(e.target.value))}>
                <option value={1}>1</option><option value={2}>2</option>
              </select>
            ) : (
              <input type="number" min={0} max={9} className="field !mt-0 !w-16 !py-0.5" value={pa}
                onChange={(e) => setPaInput(Math.min(9, Math.max(0, Number(e.target.value))))} />
            )}
          </label>
        )}
        {hasSp && (
          <label className="flex items-center gap-2">Custo SP
            <input type="number" min={0} max={99} className="field !mt-0 !w-16 !py-0.5" value={spCost}
              onChange={(e) => setSpCost(Math.min(99, Math.max(0, Number(e.target.value))))} />
          </label>
        )}
        {kind === 'MAJOR' && (
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={slow} onChange={(e) => { setSlow(e.target.checked); setPaInput(null); }} /> Lenta (4 PA)
          </label>
        )}
      </div>
      <label className="block text-xs">Rolagem (opcional)
        <input className="field !mt-0.5 !py-0.5" maxLength={40} value={dice} placeholder="1d20+3"
          aria-invalid={Boolean(diceTrim && !DICE.test(diceTrim))} onChange={(e) => setDice(e.target.value)} />
      </label>
      <p className="text-xs font-bold">
        Custo: {pa} PA{hasSp ? ` · ${effSp} SP` : ''}{diceTrim && DICE.test(diceTrim) ? ` · rola ${diceTrim}` : ''}
      </p>
    </ActionMenu>
  );
}

/** Trocar guarda: lista visual das guardas, a atual destacada e bloqueada. */
export function GuardMenu({ ctx, color, onSubmit, onCancel }) {
  const { p, paReason } = ctx;
  const [guard, setGuard] = useState('');
  return (
    <ActionMenu title="Trocar guarda" color={color} onCancel={onCancel}
      reason={paReason(1) ?? (!guard ? 'Escolha a guarda' : null)}
      onConfirm={() => onSubmit({ action: 'CHANGE_GUARD', guard })}>
      <p className="text-xs">
        Custa 1 PA (ambidestro ou Taekwondo: a 1ª troca do turno custa 0). Reinicia o Timing: ataques anteriores não contam mais.
      </p>
      {Object.entries(GUARD_LABEL).map(([k, l]) => (
        <OptionButton key={k} title={l} tag={k === p.guard ? 'Atual' : undefined} disabled={k === p.guard}
          selected={guard === k} onClick={() => setGuard(k)} />
      ))}
    </ActionMenu>
  );
}

const NO_BASE = '__none';
/** Trocar base: entrar (1 PA), sair (1 PA) ou trocar de uma para outra (2 PA); 0 PA com Troca Fluida/MMA/Ginga (servidor decide). */
export function BaseMenu({ ctx, color, onSubmit, onCancel }) {
  const { p, paReason } = ctx;
  const [base, setBase] = useState('');
  const pa = p.activeBaseId && base && base !== NO_BASE ? 2 : 1;
  return (
    <ActionMenu title="Trocar base" color={color} onCancel={onCancel}
      reason={paReason(pa) ?? (!base ? 'Escolha a base' : null)}
      onConfirm={() => onSubmit({ action: 'CHANGE_BASE', baseId: base === NO_BASE ? null : base })}>
      <p className="text-xs">
        Base atual: <strong>{p.activeBaseName ?? 'nenhuma'}</strong>. Entrar ou sair: 1 PA · trocar de base: 2 PA
        (Troca Fluida, MMA e Ginga podem reduzir o custo).
      </p>
      {p.activeBaseId && <OptionButton title="Sair da base" detail="Fica sem base ativa" selected={base === NO_BASE} onClick={() => setBase(NO_BASE)} />}
      {(p.bases ?? []).map((b) => (
        <OptionButton key={b.id} title={b.name} tag={b.id === p.activeBaseId ? 'Atual' : undefined}
          disabled={b.id === p.activeBaseId} selected={base === b.id} onClick={() => setBase(b.id)} />
      ))}
    </ActionMenu>
  );
}
