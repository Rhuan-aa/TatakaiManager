import { useEffect, useState } from 'react';
import { createNpc, associateNpc, uploadNpcImage } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import { createNpcSheet, gmAdjust } from '../../api/characterSheets';
import { listTraits } from '../../api/catalog';
import { requestArt, setArtGraduation } from '../../api/characterTechniques';
import { SheetFields, initialSheetFields, sheetPayload } from '../sheet/CharacterSheetForm';
import TraitPicker from './TraitPicker';
import { randomNpc } from './randomNpc';
import DetailListEditor from './DetailListEditor';
import InteractionListEditor from './InteractionListEditor';
import NpcImagePicker from './NpcImagePicker';
import NpcMartialArt from './NpcMartialArt';

const inputClass =
  'field';

export default function CreateNpcForm({ campaignId, onCreated, onCancel }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [interactions, setInteractions] = useState([]);
  const [art, setArt] = useState({ artId: null, graduation: 'BEGINNER' });
  const [knowledge, setKnowledge] = useState([]);
  const [specs, setSpecs] = useState([]);
  const [imageAction, setImageAction] = useState({ file: null, remove: false });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  // Ficha única (§51.8): atributos/recursos são opcionais, mas necessários para usar o NPC em combate.
  const [withStats, setWithStats] = useState(false);
  const [sheetF, setSheetF] = useState(() => initialSheetFields(null, {}));
  const [catalog, setCatalog] = useState(null);
  const [pickedTraits, setPickedTraits] = useState([]);
  const setSheet = (k) => (e) => setSheetF((p) => ({ ...p, [k]: e.target.value }));

  async function loadCatalog() {
    if (catalog) return catalog;
    try {
      const list = await listTraits(campaignId);
      setCatalog(list);
      return list;
    } catch (err) {
      setError(parseApiError(err).message);
      return [];
    }
  }

  useEffect(() => { loadCatalog(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function randomize() {
    const r = randomNpc(await loadCatalog());
    setName(r.name);
    setSheetF(r.sheet);
    setPickedTraits(r.traits);
    setWithStats(true);
  }

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
    setSubmitting(true);
    try {
      const npc = await createNpc(buildBody());
      if (imageAction.file) {
        await uploadNpcImage(npc.id, imageAction.file);
      }
      await associateNpc(campaignId, npc.id);
      // A ficha (leve) já nasce com o NPC; aqui só a completa e liga traços e arte. Não atômico, como imagem/associação.
      let sheetId = npc.sheetId;
      let combatReady = false;
      if (withStats) {
        const sheet = await createNpcSheet(npc.id, sheetPayload({ ...sheetF, name }));
        sheetId = sheet.id;
        combatReady = sheet.combatReady;
      }
      for (const t of pickedTraits) {
        await gmAdjust(sheetId, { action: 'ADD_TRAIT', reason: 'Criação do NPC', targetId: t.id });
      }
      if (art.artId) {
        const m = await requestArt(sheetId, art.artId);
        const ca = m.arts.find((x) => x.artId === art.artId);
        if (ca && art.graduation !== 'BEGINNER') await setArtGraduation(sheetId, ca.id, art.graduation);
      }
      onCreated({
        sheetId,
        combatReady,
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
      className="panel p-6"
      noValidate
    >
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="panel-title flex-1 text-xl">Novo NPC</h3>
        <button type="button" className="btn-ghost text-sm" onClick={randomize}>🎲 NPC aleatório</button>
      </div>

      <div className="mt-4 space-y-4">
        <div>
          <label htmlFor="npc-name" className="block text-sm font-medium text-[var(--text)] opacity-70">
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
          {fieldErrors.name && <p className="mt-1 text-xs text-[var(--accent)]">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="npc-desc" className="block text-sm font-medium text-[var(--text)] opacity-70">
            Descrição <span className="text-[var(--text)] opacity-70">(opcional)</span>
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

        {catalog == null ? <div className="skeleton h-12 w-full" /> : (
          <TraitPicker campaignId={campaignId} catalog={catalog} onCatalogChange={setCatalog}
            selected={pickedTraits} onChange={setPickedTraits} />
        )}

        <NpcMartialArt campaignId={campaignId} value={art} onChange={setArt} />

        <div className="panel p-3">
          <label className="flex items-center gap-2 text-sm font-bold uppercase">
            <input type="checkbox" checked={withStats} onChange={(e) => setWithStats(e.target.checked)} />
            Preencher atributos e recursos agora
          </label>
          <p className="caption mt-1">Opcional, mas obrigatório para usar o NPC em combate e nas rolagens.</p>
          {withStats && <div className="mt-3"><SheetFields f={sheetF} set={setSheet} errors={fieldErrors} /></div>}
        </div>

        <DetailListEditor label="Specs (habilidades)" items={specs} onChange={setSpecs} />

        <DetailListEditor label="Conhecimentos" items={knowledge} onChange={setKnowledge} />

        {error && <p className="text-sm text-[var(--accent)]">{error}</p>}
      </div>

      <div className="mt-5 flex justify-end gap-3">
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
          {submitting ? 'Criando...' : 'Criar e adicionar'}
        </button>
      </div>
    </form>
  );
}
