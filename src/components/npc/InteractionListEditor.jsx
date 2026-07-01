/**
 * Editor das interações de um NPC: cada entrada tem tipo (categoria), título,
 * descrição (opcional) e custo em pontos de ócio. Podem existir várias do mesmo
 * tipo. Ao menos uma é obrigatória.
 */
export default function InteractionListEditor({ items, onChange }) {
  function update(index, key, value) {
    onChange(items.map((it, i) => (i === index ? { ...it, [key]: value } : it)));
  }

  function add() {
    onChange([...items, { type: '', name: '', description: '', idlePointCost: '' }]);
  }

  function remove(index) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-zinc-400">
          Interações <span className="text-zinc-600">(ao menos uma)</span>
        </span>
        <button
          type="button"
          onClick={add}
          className="text-sm text-red-400 hover:text-red-300"
        >
          + Adicionar
        </button>
      </div>
      {items.length === 0 && (
        <p className="mt-1 text-xs text-zinc-600">
          Adicione ao menos uma interação (podem existir várias do mesmo tipo).
        </p>
      )}
      {items.map((item, i) => (
        <div key={i} className="mt-2 rounded-md border border-zinc-700 p-2">
          <div className="flex gap-2">
            <input
              type="text"
              value={item.type}
              onChange={(e) => update(i, 'type', e.target.value)}
              placeholder="Tipo (ex.: Treino)"
              className="w-32 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
            />
            <input
              type="text"
              value={item.name}
              onChange={(e) => update(i, 'name', e.target.value)}
              placeholder="Título (ex.: Esgrima avançada)"
              className="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
            />
            <div className="flex items-center gap-1">
              <input
                type="number"
                min={0}
                max={9999}
                value={item.idlePointCost}
                onChange={(e) => update(i, 'idlePointCost', e.target.value)}
                placeholder="0"
                title="Custo em pontos de ócio"
                className="w-20 rounded-md border border-zinc-700 bg-zinc-800 px-2 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
              />
              <span className="text-xs text-zinc-500" title="pontos de ócio">
                ócio
              </span>
            </div>
            <button
              type="button"
              onClick={() => remove(i)}
              className="text-sm text-zinc-500 hover:text-red-400"
            >
              remover
            </button>
          </div>
          <input
            type="text"
            value={item.description}
            onChange={(e) => update(i, 'description', e.target.value)}
            placeholder="Descrição (opcional)"
            className="mt-2 w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
          />
        </div>
      ))}
    </div>
  );
}
