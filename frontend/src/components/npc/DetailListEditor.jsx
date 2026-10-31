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
    <div className="panel p-4">
      <div className="flex items-center justify-between">
        <h3 className="panel-title">
          {label} <span className="text-xs font-normal opacity-70">(opcional)</span>
        </h3>
        <button
          type="button"
          onClick={add}
          className="btn-ghost text-sm"
        >
          + Adicionar
        </button>
      </div>
      {items.map((item, i) => (
        <div key={i} className="bubble mt-2 flex gap-2 p-2">
          <input
            type="text"
            value={item.name}
            onChange={(e) => update(i, 'name', e.target.value)}
            placeholder="Nome"
            className="w-1/3 field text-sm"
          />
          <input
            type="text"
            value={item.description}
            onChange={(e) => update(i, 'description', e.target.value)}
            placeholder="Descrição"
            className="flex-1 field text-sm"
          />
          <button
            type="button"
            onClick={() => remove(i)}
            className="btn-ghost text-sm"
          >
            remover
          </button>
        </div>
      ))}
    </div>
  );
}
