/** Vantagem/desvantagem (rola em dobro) e modo de vários dados: somar (padrão) ou separar em microrolagens. */
export default function DiceOptions({ adv, onAdv, separate, onSeparate }) {
  return (
    <div className="flex flex-wrap items-center gap-3 text-xs">
      <span className="flex" role="group" aria-label="Vantagem">
        {[[0, 'Normal'], [1, 'Vantagem'], [-1, 'Desvantagem']].map(([v, l]) => (
          <button key={v} type="button" aria-pressed={adv === v} onClick={() => onAdv(v)}
            className={`border-2 border-[var(--line)] px-2 py-0.5 font-bold uppercase ${adv === v ? 'bg-[var(--accent)] text-white' : 'bg-[var(--panel-bg)]'}`}>{l}</button>
        ))}
      </span>
      <label className="flex items-center gap-1">
        <input type="checkbox" checked={separate} onChange={(e) => onSeparate(e.target.checked)} />
        Separar dados (microrolagens)
      </label>
    </div>
  );
}
