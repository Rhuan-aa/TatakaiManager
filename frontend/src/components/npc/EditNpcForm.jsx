import { useState } from 'react';
import { updateNpc, uploadNpcImage, deleteNpcImage } from '../../api/npcs';
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

const inputClass =
  'field';

export default function EditNpcForm({ npc, campaignId, onUpdated, onCancel }) {
  const [name, setName] = useState(npc.name);
  const [description, setDescription] = useState(npc.description ?? '');
  const [interactions, setInteractions] = useState(
    npc.interactions?.map((i) => ({
      type: i.type ?? '',
      name: i.name,
      description: i.description ?? '',
      idlePointCost: i.idlePointCost != null ? String(i.idlePointCost) : '',
    })) ?? []
  );
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
  const [specs, setSpecs] = useState(
    npc.specs?.map((s) => ({ name: s.name, description: s.description ?? '' })) ?? []
  );
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
      let updated = await updateNpc(npc.id, buildBody());
      if (imageAction.file) {
        await uploadNpcImage(npc.id, imageAction.file);
        updated = { ...updated, hasImage: true };
      } else if (imageAction.remove) {
        await deleteNpcImage(npc.id);
        updated = { ...updated, hasImage: false };
      }
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
      <h4 className="text-sm font-semibold text-zinc-50">Editar NPC</h4>

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

        <NpcImagePicker
          campaignId={campaignId}
          npcId={npc.id}
          hasImage={npc.hasImage}
          onChange={setImageAction}
        />

        <InteractionListEditor items={interactions} onChange={setInteractions} />

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

        <DetailListEditor label="Specs (habilidades)" items={specs} onChange={setSpecs} />

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
