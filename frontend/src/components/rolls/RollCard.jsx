import { useState } from 'react';

const partText = (p) => (p.dice?.length ? `${p.source} [${p.dice.join(', ')}] = ${p.value}` : `${p.source}: ${p.value >= 0 ? '+' : ''}${p.value}`);

/** Cartão de uma rolagem (RollMessage). `onReveal` só para GM_PRIVATE não revelada. */
export default function RollCard({ roll, onReveal }) {
  const [open, setOpen] = useState(false);
  const parts = roll.breakdown ?? [];
  const who = roll.characterName || roll.actorName;
  return (
    <article className={`panel relative p-3${roll.critical ? ' roll-critical' : roll.fumble ? ' roll-fumble' : ''}`}>
      {(roll.critical || roll.fumble) && (
        <span className="onomatopoeia roll-onomatopoeia" aria-hidden="true">{roll.critical ? 'CRÍTICO!' : 'FALHA CRÍTICA!'}</span>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <span data-roll-total className="panel-title text-3xl" aria-label={`Total ${roll.total}`}>{roll.total}</span>
        {roll.damageTotal != null && (
          <span className="tag-accent tag" title="Dano">Dano {roll.damageTotal}</span>
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
        {roll.advantage ? ` · vantagem ${roll.advantage > 0 ? '+' : ''}${roll.advantage}` : ''}
        {roll.createdAt && ` · ${new Date(roll.createdAt).toLocaleTimeString('pt-BR')}`}
      </div>
      {roll.edited && roll.editReason && <div className="caption">Motivo: {roll.editReason}</div>}
      {parts.length > 0 && (
        <>
          <button type="button" className="mt-1 text-xs underline" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? 'Ocultar parcelas' : 'Ver parcelas'}
          </button>
          {open && (
            <ul className="mt-1 flex flex-col gap-0.5 text-xs">
              {parts.map((p, i) => (
                <li key={i}>
                  <span className="font-bold">{p.part === 'DAMAGE' ? 'Dano' : 'Acerto'}</span> · {partText(p)}
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
  return (
    <li>
      <button type="button" className="flex w-full flex-wrap items-center gap-2 border-b border-[var(--line)] py-1 text-left text-sm"
        aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <span className="panel-title w-10 text-xl">{roll.total}</span>
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
