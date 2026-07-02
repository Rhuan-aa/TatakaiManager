import { useState } from 'react';
import { createNpc, associateNpc, uploadNpcImage } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import DetailListEditor from './DetailListEditor';
import InteractionListEditor from './InteractionListEditor';
import NpcImagePicker from './NpcImagePicker';

const ATTRIBUTES = [
  { key: 'forca', label: 'Força' },
  { key: 'destreza', label: 'Destreza' },
  { key: 'constituicao', label: 'Constituição' },
  { key: 'mental', label: 'Mental' },
  { key: 'inteligencia', label: 'Inteligência' },
  { key: 'talento', label: 'Talento' },
];

const EMPTY_ATTRS = { forca: '', destreza: '', constituicao: '', mental: '', inteligencia: '', talento: '' };

const inputClass =
  'field';

export default function CreateNpcForm({ campaignId, onCreated, onCancel }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [interactions, setInteractions] = useState([]);
  const [attrs, setAttrs] = useState(EMPTY_ATTRS);
  const [traits, setTraits] = useState([]);
  const [knowledge, setKnowledge] = useState([]);
  const [specs, setSpecs] = useState([]);
  const [imageAction, setImageAction] = useState({ file: null, remove: false });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  function buildInteractions() {
    return interactions
      .filter((i) => i.name.trim())
      .map((i) => ({
        type: i.type?.trim() || null,
        name: i.name.trim(),
        description: i.description?.trim() || null,
        idlePointCost: i.idlePointCost === '' ? 0 : Number(i.idlePointCost),
      }));
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
      specs: specs.filter((s) => s.name.trim()),
      interactions: buildInteractions(),
    };
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    if (buildInteractions().length === 0) {
      setError('Adicione ao menos um tipo de interação (com nome).');
      return;
    }
    setSubmitting(true);
    try {
      const npc = await createNpc(buildBody());
      if (imageAction.file) {
        await uploadNpcImage(npc.id, imageAction.file);
      }
      await associateNpc(campaignId, npc.id);
      onCreated({
        id: npc.id,
        name: npc.name,
        visible: true,
        interactions: npc.interactions,
        hasImage: !!imageAction.file,
      });
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
      className="rounded-xl border border-zinc-700 bg-zinc-800 p-6"
      noValidate
    >
      <h3 className="text-base font-semibold text-zinc-50">Novo NPC</h3>

      <div className="mt-4 space-y-4">
        <div>
          <label htmlFor="npc-name" className="block text-sm font-medium text-zinc-400">
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
          {fieldErrors.name && <p className="mt-1 text-xs text-red-400">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="npc-desc" className="block text-sm font-medium text-zinc-400">
            Descrição <span className="text-zinc-600">(opcional)</span>
          </label>
          <textarea
            id="npc-desc"
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={inputClass}
          />
        </div>

        <NpcImagePicker hasImage={false} onChange={setImageAction} />

        <InteractionListEditor items={interactions} onChange={setInteractions} />

        <fieldset>
          <legend className="text-sm font-medium text-zinc-400">
            Atributos <span className="text-zinc-600">(opcional, 0–999)</span>
          </legend>
          <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-3">
            {ATTRIBUTES.map((a) => (
              <div key={a.key}>
                <label htmlFor={`attr-${a.key}`} className="block text-xs text-zinc-500">
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

        <DetailListEditor label="Traços" items={traits} onChange={setTraits} />

        <DetailListEditor label="Specs (habilidades)" items={specs} onChange={setSpecs} />

        <DetailListEditor label="Conhecimentos" items={knowledge} onChange={setKnowledge} />

        {error && <p className="text-sm text-red-400">{error}</p>}
      </div>

      <div className="mt-5 flex justify-end gap-3">
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
          {submitting ? 'Criando...' : 'Criar e adicionar'}
        </button>
      </div>
    </form>
  );
}
