/**
 * Editor de lista de entradas {name, description} — usado tanto para
 * Conhecimentos quanto para Traços do NPC (ambos opcionais, mesmo formato).
 */
export default function DetailListEditor({ label, items, onChange }) {
  function update(index, key, value) {
    onChange(items.map((it, i) => (i === index ? { ...it, [key]: value } : it)));
  }

  function add() {
    onChange([...items, { name: '', description: '' }]);
  }

  function remove(index) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-zinc-400">
          {label} <span className="text-zinc-600">(opcional)</span>
        </span>
        <button
          type="button"
          onClick={add}
          className="text-sm text-red-400 hover:text-red-300"
        >
          + Adicionar
        </button>
      </div>
      {items.map((item, i) => (
        <div key={i} className="mt-2 flex gap-2">
          <input
            type="text"
            value={item.name}
            onChange={(e) => update(i, 'name', e.target.value)}
            placeholder="Nome"
            className="w-1/3 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
          />
          <input
            type="text"
            value={item.description}
            onChange={(e) => update(i, 'description', e.target.value)}
            placeholder="Descrição"
            className="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none"
          />
          <button
            type="button"
            onClick={() => remove(i)}
            className="text-sm text-zinc-500 hover:text-red-400"
          >
            remover
          </button>
        </div>
      ))}
    </div>
  );
}
