// Tipos de interação predefinidos. "Outro" libera um campo para tipo personalizado.
const PREDEFINED_TYPES = ['Treino', 'Sparring', 'Negociação', 'Aprendizado'];

/**
 * Editor das interações de um NPC: cada entrada tem tipo (dropdown, com "Outro"
 * para tipo livre), título, descrição (opcional) e custo em pontos de ócio.
 * Podem existir várias do mesmo tipo. Ao menos uma é obrigatória.
 */
export default function InteractionListEditor({ items, onChange }) {
  function update(index, key, value) {
    onChange(items.map((it, i) => (i === index ? { ...it, [key]: value } : it)));
  }

  function changeType(index, selected) {
    // "Outro" limpa o tipo para o usuário digitar um valor livre
    update(index, 'type', selected === 'Outro' ? '' : selected);
  }

  function add() {
    onChange([...items, { type: 'Treino', name: '', description: '', idlePointCost: '' }]);
  }

  function remove(index) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-[var(--text)] opacity-70">
          Interações <span className="text-[var(--text)] opacity-70">(ao menos uma)</span>
        </span>
        <button
          type="button"
          onClick={add}
          className="btn-ghost text-sm"
        >
          + Adicionar
        </button>
      </div>
      {items.length === 0 && (
        <p className="mt-1 text-xs text-[var(--text)] opacity-70">
          Adicione ao menos uma interação (podem existir várias do mesmo tipo).
        </p>
      )}
      {items.map((item, i) => {
        // Um tipo fora da lista predefinida (ou vazio) é tratado como "Outro"
        const isOther = !PREDEFINED_TYPES.includes(item.type);
        const selectValue = isOther ? 'Outro' : item.type;
        return (
          <div key={i} className="mt-2 panel p-2">
            <div className="flex gap-2">
              <select
                value={selectValue}
                onChange={(e) => changeType(i, e.target.value)}
                title="Tipo da interação"
                className="w-36 field text-sm"
              >
                {PREDEFINED_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
                <option value="Outro">Outro</option>
              </select>
              <input
                type="text"
                value={item.name}
                onChange={(e) => update(i, 'name', e.target.value)}
                placeholder="Título (ex.: Esgrima avançada)"
                className="flex-1 field text-sm"
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
                  className="w-20 field text-sm"
                />
                <span className="text-xs text-[var(--text)] opacity-70" title="pontos de ócio">
                  ócio
                </span>
              </div>
              <button
                type="button"
                onClick={() => remove(i)}
                className="btn-ghost text-sm"
              >
                remover
              </button>
            </div>
            {isOther && (
              <input
                type="text"
                value={item.type}
                onChange={(e) => update(i, 'type', e.target.value)}
                placeholder="Tipo personalizado (ex.: Diplomacia)"
                className="mt-2 w-full field text-sm"
              />
            )}
            <input
              type="text"
              value={item.description}
              onChange={(e) => update(i, 'description', e.target.value)}
              placeholder="Descrição (opcional)"
              className="mt-2 w-full field text-sm"
            />
          </div>
        );
      })}
    </div>
  );
}
