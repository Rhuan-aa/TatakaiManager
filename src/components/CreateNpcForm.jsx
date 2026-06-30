import { useState } from 'react';
import { createNpc, associateNpc } from '../api/npcs';
import { parseApiError } from '../api/parseApiError';

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

const EMPTY_ATTRS = { forca: '', destreza: '', constituicao: '', mental: '', inteligencia: '', talento: '' };

export default function CreateNpcForm({ campaignId, onCreated, onCancel }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [types, setTypes] = useState([]);
  const [attrs, setAttrs] = useState(EMPTY_ATTRS);
  const [traits, setTraits] = useState('');
  const [specs, setSpecs] = useState([]);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  function toggleType(value) {
    setTypes((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  }

  function updateSpec(index, key, value) {
    setSpecs((prev) => prev.map((s, i) => (i === index ? { ...s, [key]: value } : s)));
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
      traits: traits
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean),
      specs: specs.filter((s) => s.name.trim()),
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
      const npc = await createNpc(buildBody());
      await associateNpc(campaignId, npc.id);
      onCreated({
        id: npc.id,
        name: npc.name,
        visible: true,
        interactionTypes: npc.interactionTypes,
      });
    } catch (err) {
      const parsed = parseApiError(err);
      setError(parsed.message);
      setFieldErrors(parsed.fields);
    } finally {
      setSubmitting(false);
    }
  }

  const inputClass =
    'mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500';

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm"
      noValidate
    >
      <h3 className="text-base font-semibold text-slate-900">Novo NPC</h3>

      <div className="mt-4 space-y-4">
        <div>
          <label htmlFor="npc-name" className="block text-sm font-medium text-slate-700">
            Nome
          </label>
          <input
            id="npc-name"
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className={inputClass}
          />
          {fieldErrors.name && <p className="mt-1 text-xs text-red-600">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="npc-desc" className="block text-sm font-medium text-slate-700">
            Descrição <span className="text-slate-400">(opcional)</span>
          </label>
          <textarea
            id="npc-desc"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={inputClass}
          />
          {fieldErrors.description && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.description}</p>
          )}
        </div>

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Tipos de interação</legend>
          <div className="mt-2 flex flex-wrap gap-3">
            {INTERACTION_TYPES.map((t) => (
              <label key={t.value} className="flex items-center gap-1.5 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={types.includes(t.value)}
                  onChange={() => toggleType(t.value)}
                />
                {t.label}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">
            Atributos <span className="text-slate-400">(opcional, 0–999)</span>
          </legend>
          <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-3">
            {ATTRIBUTES.map((a) => (
              <div key={a.key}>
                <label htmlFor={`attr-${a.key}`} className="block text-xs text-slate-500">
                  {a.label}
                </label>
                <input
                  id={`attr-${a.key}`}
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

        <div>
          <label htmlFor="npc-traits" className="block text-sm font-medium text-slate-700">
            Traços <span className="text-slate-400">(separados por vírgula)</span>
          </label>
          <input
            id="npc-traits"
            type="text"
            value={traits}
            onChange={(e) => setTraits(e.target.value)}
            placeholder="corajoso, leal, impulsivo"
            className={inputClass}
          />
        </div>

        <div>
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-slate-700">
              Especializações <span className="text-slate-400">(opcional)</span>
            </span>
            <button
              type="button"
              onClick={() => setSpecs((prev) => [...prev, { name: '', description: '' }])}
              className="text-sm text-purple-600 hover:underline"
            >
              + Adicionar
            </button>
          </div>
          {specs.map((spec, i) => (
            <div key={i} className="mt-2 flex gap-2">
              <input
                type="text"
                value={spec.name}
                onChange={(e) => updateSpec(i, 'name', e.target.value)}
                placeholder="Nome"
                className="w-1/3 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
              />
              <input
                type="text"
                value={spec.description}
                onChange={(e) => updateSpec(i, 'description', e.target.value)}
                placeholder="Descrição"
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
              />
              <button
                type="button"
                onClick={() => setSpecs((prev) => prev.filter((_, idx) => idx !== i))}
                className="text-sm text-red-600 hover:underline"
              >
                remover
              </button>
            </div>
          ))}
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}
      </div>

      <div className="mt-5 flex justify-end gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          Cancelar
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-60"
        >
          {submitting ? 'Criando...' : 'Criar e adicionar'}
        </button>
      </div>
    </form>
  );
}
