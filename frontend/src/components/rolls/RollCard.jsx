import { useState } from 'react';
import { rollKind } from './rollKind';

const partText = (p) => (p.dice?.length ? `${p.source} [${p.dice.join(', ')}] = ${p.value}` : `${p.source}: ${p.value >= 0 ? '+' : ''}${p.value}`);

const PART_LABEL = { DAMAGE: 'Dano', HIT: 'Acerto', DICE: 'Dados', MICRO: 'Micro', DISCARDED: 'Descartada' };
const isFree = (roll) => (roll.breakdown ?? []).some((p) => p.part === 'DICE' || p.part === 'MICRO');

/** Resultado de cada dado de uma rolagem livre: microrolagens em fichas, parcelas somadas e a rolagem descartada. */
export function DiceBreakdown({ roll }) {
  const parts = roll.breakdown ?? [];
  const micro = parts.filter((p) => p.part === 'MICRO');
  const rest = parts.filter((p) => p.part !== 'MICRO');
  return (
    <div className="mt-1 flex flex-col gap-1 text-xs" data-dice-breakdown>
      {micro.length > 0 && (
        <ol className="flex flex-wrap gap-1" aria-label="Microrolagens">
          {micro.map((p, i) => {
            const d20 = p.source.startsWith('1d20') ? p.dice?.[0] : null;
            const dm = p.source.match(/descartado (\d+)/);
            const disc = dm ? Number(dm[1]) : null;
            const adv = disc == null ? 0 : p.source.includes('1d20') && d20 != null ? (d20 > disc ? 1 : d20 < disc ? -1 : 0) : 0;
            const kind = d20 === 20 ? 'micro-crit' : d20 === 1 ? 'micro-fumble' : '';
            const note = adv > 0 && d20 === 20 ? 'Vantagem fez o crítico' : adv < 0 && d20 === 1 ? 'Desvantagem causou a falha'
              : adv > 0 && disc === 1 ? 'Vantagem evitou a falha' : adv < 0 && disc === 20 ? 'Desvantagem roubou o crítico' : '';
            return (
            <li key={i} className={`relative border-2 border-[var(--line)] bg-[var(--panel-bg)] px-2 py-0.5 ${kind}`} title={p.source}>
              {kind && <span className="onomatopoeia micro-word" aria-hidden="true">{d20 === 20 ? 'CRÍTICO!' : 'FALHA!'}</span>}
              <span className="opacity-70">{p.source.split(' · ')[0]}</span> <strong className="panel-title text-base">{p.value}</strong>
              {p.source.includes('descartado') && <span className="line-through opacity-60"> {p.source.split('descartado ')[1]}</span>}
              {note && <span className={`adv-tag ${adv > 0 ? 'adv-tag-up' : 'adv-tag-down'}`}>{note}</span>}
            </li>
            );
          })}
        </ol>
      )}
      {rest.some((p) => p.part === 'DISCARDED') && (
        <span className="text-[0.7rem] font-bold uppercase opacity-70">{roll.advantage > 0 ? 'Vantagem: ficou a maior soma' : 'Desvantagem: ficou a menor soma'}</span>
      )}
      {rest.map((p, i) => (
        <span key={i} className={p.part === 'DISCARDED' ? 'line-through opacity-60' : undefined}>
          <span className="font-bold">{p.part === 'DISCARDED' ? 'Descartada' : p.source}</span>
          {p.dice?.length ? <> [{p.dice.map((d, k) => {
            const n = (p.part === 'DICE' || p.part === 'DISCARDED') && p.dice.length > 1 ? Number(p.source.match(/d(\d+)/)?.[1]) : 0;
            return <span key={k}>{k ? ', ' : ''}{n && d === n ? <strong className="die-chip-max">{d}</strong> : n && d === 1 ? <span className="die-chip-min">{d}</span> : d}</span>;
          })}]</> : ''} = {p.value}
        </span>
      ))}
    </div>
  );
}

/** Cartão de uma rolagem (RollMessage). `onReveal` só para GM_PRIVATE não revelada. */
export default function RollCard({ roll, onReveal }) {
  const [open, setOpen] = useState(false);
  const parts = roll.breakdown ?? [];
  const who = roll.characterName || roll.actorName;
  const kind = rollKind(roll);
  return (
    <article className={`panel relative p-3${roll.critical ? ' roll-critical' : roll.fumble ? ' roll-fumble' : ''}`}>
      {(roll.critical || roll.fumble) && (
        <span className="onomatopoeia roll-onomatopoeia" aria-hidden="true">{roll.critical ? 'CRÍTICO!' : 'FALHA CRÍTICA!'}</span>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <span data-roll-total className="panel-title text-3xl" aria-label={`Total ${roll.total}`}>{roll.total}</span>
        <span className="roll-kind" style={{ '--kind': kind.color }}>{kind.label}</span>
        {roll.damageTotal != null && (
          <span data-roll-damage className="tag-accent tag" title="Dano">Dano {roll.damageTotal}</span>
        )}
        {roll.naturalD20 != null && <span className="tag" title="d20 natural">d20: {roll.naturalD20}</span>}
        {roll.critical && <span className="tag-accent tag">CRÍTICO</span>}
        {roll.fumble && <span className="tag">FALHA</span>}
        {roll.edited && <span className="tag" title={roll.editReason}>editado</span>}
        {roll.revealed && <span className="tag">revelada</span>}
        {roll.channel === 'GM_PRIVATE' && !roll.revealed && <span className="tag">privada</span>}
        {onReveal && (
          <button type="button" className="btn-primary ml-auto text-xs" onClick={() => onReveal(roll)}>Revelar</button>
        )}
      </div>
      <div className="mt-1 text-sm">
        <strong>{who}</strong>
        {roll.sourceName ? ` — ${roll.sourceName}` : ''}
        {roll.purpose && <span className="opacity-80"> · {roll.purpose}</span>}
      </div>
      <div className="caption">
        {roll.formula}
        {roll.advantage ? (isFree(roll) ? (roll.advantage > 0 ? ' · vantagem' : ' · desvantagem') : ` · vantagem ${roll.advantage > 0 ? '+' : ''}${roll.advantage}`) : ''}
        {roll.createdAt && ` · ${new Date(roll.createdAt).toLocaleTimeString('pt-BR')}`}
      </div>
      {roll.edited && roll.editReason && <div className="caption">Motivo: {roll.editReason}</div>}
      {isFree(roll) && <DiceBreakdown roll={roll} />}
      {parts.length > 0 && !isFree(roll) && (
        <>
          <button type="button" className="mt-1 text-xs underline" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? 'Ocultar parcelas' : 'Ver parcelas'}
          </button>
          {open && (
            <ul className="mt-1 flex flex-col gap-0.5 text-xs">
              {parts.map((p, i) => (
                <li key={i}>
                  <span className="font-bold">{PART_LABEL[p.part] ?? p.part}</span> · {partText(p)}
                  {p.edited && ' (editado)'}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </article>
  );
}

/** Linha compacta de histórico; expande para o cartão completo. */
export function RollRow({ roll, onReveal }) {
  const [open, setOpen] = useState(false);
  const who = roll.characterName || roll.actorName;
  const kind = rollKind(roll);
  return (
    <li>
      <button type="button" className="flex w-full flex-wrap items-center gap-2 border-b border-[var(--line)] py-1 text-left text-sm"
        aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <span className="panel-title w-10 text-xl">{roll.total}</span>
        <span className="roll-kind" style={{ '--kind': kind.color }}>{kind.label}</span>
        <span className="flex-1">{who}{roll.purpose ? ` · ${roll.purpose}` : ''}</span>
        {roll.critical && <span className="tag-accent tag">CRÍTICO</span>}
        {roll.fumble && <span className="tag">FALHA</span>}
        {roll.revealed && <span className="tag">revelada</span>}
        <span className="caption">{roll.createdAt && new Date(roll.createdAt).toLocaleTimeString('pt-BR')}</span>
      </button>
      {open && <RollCard roll={roll} onReveal={onReveal} />}
    </li>
  );
}
