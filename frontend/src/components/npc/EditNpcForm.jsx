import { useState } from 'react';
import { updateNpc, uploadNpcImage, deleteNpcImage } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import DetailListEditor from './DetailListEditor';
import InteractionListEditor from './InteractionListEditor';
import NpcImagePicker from './NpcImagePicker';

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
    return {
      name,
      description: description || null,
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
      className="mt-3 panel p-4"
      noValidate
    >
      <h4 className="panel-title text-xl">Editar NPC</h4>

      <div className="mt-3 space-y-4">
        <div>
          <label htmlFor="edit-npc-name" className="block text-sm font-medium text-[var(--text)] opacity-70">
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
          {fieldErrors.name && <p className="mt-1 text-xs text-[var(--accent)]">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="edit-npc-desc" className="block text-sm font-medium text-[var(--text)] opacity-70">
            Descrição <span className="text-[var(--text)] opacity-70">(opcional)</span>
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

        <p className="caption">Atributos, traços e arte marcial ficam na ficha do NPC.</p>

        <DetailListEditor label="Specs (habilidades)" items={specs} onChange={setSpecs} />

        <DetailListEditor label="Conhecimentos" items={knowledge} onChange={setKnowledge} />

        {error && <p className="text-sm text-[var(--accent)]">{error}</p>}
      </div>

      <div className="mt-4 flex justify-end gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="btn-secondary text-sm"
        >
          Cancelar
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="btn-primary text-sm disabled:opacity-50"
        >
          {submitting ? 'Salvando...' : 'Salvar alterações'}
        </button>
      </div>
    </form>
  );
}
