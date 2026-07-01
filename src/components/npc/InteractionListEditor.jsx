/**
 * Editor dos tipos de interação de um NPC: cada entrada tem nome, descrição
 * (opcional) e custo em pontos de treino. Ao menos uma é obrigatória.
 */
export default function InteractionListEditor({ items, onChange }) {
  function update(index, key, value) {
    onChange(items.map((it, i) => (i === index ? { ...it, [key]: value } : it)));
  }

  function add() {
    onChange([...items, { name: '', description: '', trainPointCost: '' }]);
  }

  function remove(index) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-zinc-400">
          Tipos de interação <span className="text-zinc-600">(ao menos um)</span>
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
          Adicione ao menos um tipo de interação (ex.: Treino, Trabalho).
        </p>
      )}
      {items.map((item, i) => (
        <div key={i} className="mt-2 rounded-md border border-zinc-700 p-2">
          <div className="flex gap-2">
            <input
              type="text"
              value={item.name}
              onChange={(e) => update(i, 'name', e.target.value)}
              placeholder="Nome (ex.: Treino)"
              className="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
            />
            <div className="flex items-center gap-1">
              <input
                type="number"
                min={0}
                max={9999}
                value={item.trainPointCost}
                onChange={(e) => update(i, 'trainPointCost', e.target.value)}
                placeholder="0"
                title="Custo em pontos de treino"
                className="w-20 rounded-md border border-zinc-700 bg-zinc-800 px-2 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
              />
              <span className="text-xs text-zinc-500">pts</span>
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
