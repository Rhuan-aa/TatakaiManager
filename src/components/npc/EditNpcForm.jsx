import { useState } from 'react';
import { updateNpc } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import DetailListEditor from './DetailListEditor';

const INTERACTION_TYPES = [
  { value: 'TREINO', label: 'Treino' },
  { value: 'TRABALHO', label: 'Trabalho' },
  { value: 'DESCANSO', label: 'Descanso' },
  { value: 'OUTRO', label: 'Outro' },
];

const ATTRIBUTES = [
  { key: 'forca', label: 'Força' },
  { key: 'destreza', label: 'Destreza' },
  { key: 'constituicao', label: 'Constituição' },
  { key: 'mental', label: 'Mental' },
  { key: 'inteligencia', label: 'Inteligência' },
  { key: 'talento', label: 'Talento' },
];

const inputClass =
  'mt-1 w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500';

export default function EditNpcForm({ npc, onUpdated, onCancel }) {
  const [name, setName] = useState(npc.name);
  const [description, setDescription] = useState(npc.description ?? '');
  const [types, setTypes] = useState(npc.interactionTypes ?? []);
  const [attrs, setAttrs] = useState(() => {
    const base = { forca: '', destreza: '', constituicao: '', mental: '', inteligencia: '', talento: '' };
    if (npc.attributes) {
      for (const key of Object.keys(base)) {
        const v = npc.attributes[key];
        base[key] = v != null ? String(v) : '';
      }
    }
    return base;
  });
  const [traits, setTraits] = useState(
    npc.traits?.map((t) => ({ name: t.name, description: t.description ?? '' })) ?? []
  );
  const [knowledge, setKnowledge] = useState(
    npc.knowledge?.map((k) => ({ name: k.name, description: k.description ?? '' })) ?? []
  );
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  function toggleType(value) {
    setTypes((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  function buildBody() {
    const attributes = {};
    for (const { key } of ATTRIBUTES) {
      attributes[key] = attrs[key] === '' ? null : Number(attrs[key]);
    }
    return {
      name,
      description: description || null,
      attributes,
      traits: traits.filter((t) => t.name.trim()),
      knowledge: knowledge.filter((k) => k.name.trim()),
      interactionTypes: types,
    };
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    if (types.length === 0) {
      setError('Selecione ao menos um tipo de interação.');
      return;
    }
    setSubmitting(true);
    try {
      const updated = await updateNpc(npc.id, buildBody());
      onUpdated(updated);
    } catch (err) {
      const parsed = parseApiError(err);
      setError(parsed.message);
      setFieldErrors(parsed.fields);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mt-3 rounded-lg border border-zinc-700 bg-zinc-800 p-4"
      noValidate
    >
      <h4 className="text-sm font-semibold text-white">Editar NPC</h4>

      <div className="mt-3 space-y-4">
        <div>
          <label htmlFor="edit-npc-name" className="block text-sm font-medium text-zinc-400">
            Nome
          </label>
          <input
            id="edit-npc-name"
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className={inputClass}
          />
          {fieldErrors.name && <p className="mt-1 text-xs text-red-400">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="edit-npc-desc" className="block text-sm font-medium text-zinc-400">
            Descrição <span className="text-zinc-600">(opcional)</span>
          </label>
          <textarea
            id="edit-npc-desc"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={inputClass}
          />
        </div>

        <fieldset>
          <legend className="text-sm font-medium text-zinc-400">Tipos de interação</legend>
          <div className="mt-2 flex flex-wrap gap-3">
            {INTERACTION_TYPES.map((t) => (
              <label key={t.value} className="flex items-center gap-1.5 text-sm text-zinc-300">
                <input
                  type="checkbox"
                  checked={types.includes(t.value)}
                  onChange={() => toggleType(t.value)}
                  className="accent-red-500"
                />
                {t.label}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="text-sm font-medium text-zinc-400">
            Atributos <span className="text-zinc-600">(opcional, 0–999)</span>
          </legend>
          <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-3">
            {ATTRIBUTES.map((a) => (
              <div key={a.key}>
                <label htmlFor={`edit-attr-${a.key}`} className="block text-xs text-zinc-500">
                  {a.label}
                </label>
                <input
                  id={`edit-attr-${a.key}`}
                  type="number"
                  min={0}
                  max={999}
                  value={attrs[a.key]}
                  onChange={(e) => setAttrs((p) => ({ ...p, [a.key]: e.target.value }))}
                  className={inputClass}
                />
              </div>
            ))}
          </div>
        </fieldset>

        <DetailListEditor label="Traços" items={traits} onChange={setTraits} />

        <DetailListEditor label="Conhecimentos" items={knowledge} onChange={setKnowledge} />

        {error && <p className="text-sm text-red-400">{error}</p>}
      </div>

      <div className="mt-4 flex justify-end gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-zinc-700 px-3 py-2 text-sm font-medium text-zinc-400 hover:bg-zinc-700"
        >
          Cancelar
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
        >
          {submitting ? 'Salvando...' : 'Salvar alterações'}
        </button>
      </div>
    </form>
  );
}
