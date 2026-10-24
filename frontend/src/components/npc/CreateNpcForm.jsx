import { useState } from 'react';
import { createNpc, associateNpc, uploadNpcImage } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import { createNpcSheet, gmAdjust } from '../../api/characterSheets';
import { listTraits } from '../../api/catalog';
import { SheetFields, initialSheetFields, sheetPayload } from '../sheet/CharacterSheetForm';
import TraitPicker from './TraitPicker';
import { randomNpc } from './randomNpc';
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
  // 'simple' = só o NPC (como antes); 'full' = NPC + ficha + traços do catálogo.
  const [mode, setMode] = useState('simple');
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

  function chooseMode(m) {
    setMode(m);
    if (m === 'full') loadCatalog();
  }

  async function randomize() {
    const r = randomNpc(await loadCatalog());
    setName(r.name);
    setSheetF(r.sheet);
    setPickedTraits(r.traits);
    setMode('full');
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
    const attributes = {};
    for (const { key } of ATTRIBUTES) {
      attributes[key] = attrs[key] === '' ? null : Number(attrs[key]);
    }
    const full = mode === 'full';
    return {
      name,
      description: description || null,
      attributes: full ? null : attributes,
      traits: full ? [] : traits.filter((t) => t.name.trim()),
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
      let sheetId = null;
      if (mode === 'full') {
        // Não atômico (como imagem/associação): se falhar aqui, o NPC fica sem ficha e dá para criar depois.
        const sheet = await createNpcSheet(npc.id, sheetPayload({ ...sheetF, name }));
        sheetId = sheet.id;
        for (const t of pickedTraits) {
          await gmAdjust(sheet.id, { action: 'ADD_TRAIT', reason: 'Criação do NPC', targetId: t.id });
        }
      }
      onCreated({
        sheetId,
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
        <div role="group" aria-label="Tipo de criação" className="flex">
          {[['simple', 'Simples'], ['full', 'Completa']].map(([m, lbl]) => (
            <button key={m} type="button" aria-pressed={mode === m}
              className={`${mode === m ? 'btn-primary' : 'btn-secondary'} text-sm`} onClick={() => chooseMode(m)}>
              {lbl}
            </button>
          ))}
        </div>
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

        {mode === 'full' ? (
          <>
            <SheetFields f={sheetF} set={setSheet} errors={fieldErrors} />
            {catalog == null ? <div className="skeleton h-12 w-full" /> : (
              <TraitPicker campaignId={campaignId} catalog={catalog} onCatalogChange={setCatalog}
                selected={pickedTraits} onChange={setPickedTraits} />
            )}
          </>
        ) : (
          <>
            <fieldset>
              <legend className="text-sm font-medium text-[var(--text)] opacity-70">
                Atributos <span className="text-[var(--text)] opacity-70">(opcional, 0–999)</span>
              </legend>
              <div className="mt-2 grid grid-cols-2 gap-3 sm:grid-cols-3">
                {ATTRIBUTES.map((a) => (
                  <div key={a.key}>
                    <label htmlFor={`attr-${a.key}`} className="block text-xs text-[var(--text)] opacity-70">
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
          </>
        )}

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
