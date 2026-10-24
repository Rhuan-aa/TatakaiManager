import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  getCatalogPermissions, listTraits, createTrait, updateTrait, deleteTrait,
  listClans, createClan, updateClan, deleteClan, importCatalog,
} from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { ATTRS } from '../sheet/sheetRules';
import ProgressionCatalog from './ProgressionCatalog';
import ArtsPanel from './ArtsPanel';
import RichText from '../ui/RichText';
import { rarityTagStyle, compareTraits, TRAIT_CATEGORIES } from '../ui/rarity';

const RARITIES = [
  { value: 'COMMON', label: 'Comum' }, { value: 'RARE', label: 'Raro' }, { value: 'EPIC', label: 'Épico' },
  { value: 'LEGENDARY', label: 'Lendário' }, { value: 'DIVINE', label: 'Divino' },
];
const CATEGORIES = TRAIT_CATEGORIES;
const ACTIVATIONS = [{ value: 'PASSIVE', label: 'Passivo' }, { value: 'ACTIVE', label: 'Ativo' }];
const ATTR_NAMES = { STR: 'Força', DEX: 'Destreza', CON: 'Constituição', INT: 'Inteligência', CHA: 'Carisma', MEN: 'Mental', TAL: 'Talento' };
const RESOURCES = ['HP', 'SP', 'KP'];
const LIMITER_KEYS = [...ATTRS.filter((a) => a.enumName !== 'TAL').map((a) => a.enumName), ...RESOURCES];
const lab = (list, v) => list.find((i) => i.value === v)?.label ?? v ?? '—';

const EXAMPLE = `{
  "traits": [
    { "name": "Reflexos Afiados", "rarity": "COMMON", "category": "PHYSICAL", "rollNumber": 3,
      "description": "...", "effects": [{ "text": "+1 em esquiva" }],
      "buildGains": { "attributeFlat": { "DEX": 1 }, "attributePercent": {}, "maxHp": 0, "maxSp": 0, "maxKp": 0,
                      "limiters": {}, "freePoints": 0 },
      "developable": false, "upgradable": false }
  ],
  "clans": [
    { "name": "Clã Exemplo", "rollNumber": 5, "description": "...", "buildGains": null, "heritage": ["Katana antiga"] }
  ]
}`;

const num = (v) => (v === '' || v == null ? 0 : Number(v));
const compact = (m) => Object.fromEntries(Object.entries(m).map(([k, v]) => [k, num(v)]).filter(([, v]) => v !== 0));

function emptyForm(kind) {
  return {
    name: '', rarity: 'COMMON', category: 'PHYSICAL', rollNumber: '', description: '', effects: [], variations: [],
    flat: {}, percent: {}, limiters: {}, maxHp: '', maxSp: '', maxKp: '', freePoints: '',
    developable: false, upgradable: false, heritage: [], kind,
    activation: 'PASSIVE', requirements: '', developmentRequirements: '',
  };
}
function fromItem(kind, it) {
  const g = it.buildGains ?? {};
  return {
    ...emptyForm(kind), name: it.name, rarity: it.rarity ?? 'COMMON', category: it.category ?? 'PHYSICAL',
    rollNumber: it.rollNumber ?? '', description: it.description ?? '',
    effects: (it.effects ?? []).map((e) => ({ name: e.name ?? '', text: e.text ?? '' })),
    variations: (it.variations ?? []).map((v) => ({
      name: v.name ?? '', requirements: v.requirements ?? '', description: v.description ?? '',
      effects: (v.effects ?? []).map((e) => ({ name: e.name ?? '', text: e.text ?? '' })),
    })), flat: g.attributeFlat ?? {}, percent: g.attributePercent ?? {},
    limiters: g.limiters ?? {}, maxHp: g.maxHp || '', maxSp: g.maxSp || '', maxKp: g.maxKp || '', freePoints: g.freePoints || '',
    developable: !!it.developable, upgradable: !!it.upgradable, heritage: it.heritage ?? [],
    activation: it.activation ?? 'PASSIVE', requirements: it.requirements ?? '', developmentRequirements: it.developmentRequirements ?? '',
  };
}
const cleanEffects = (list) => list.filter((e) => e.text.trim()).map((e) => ({ name: e.name.trim(), text: e.text }));

function toPayload(kind, f) {
  const base = {
    name: f.name.trim(), rollNumber: f.rollNumber === '' ? null : Number(f.rollNumber), description: f.description,
    buildGains: {
      attributeFlat: compact(f.flat), attributePercent: compact(f.percent), maxHp: num(f.maxHp), maxSp: num(f.maxSp),
      maxKp: num(f.maxKp), limiters: compact(f.limiters), freePoints: num(f.freePoints),
    },
  };
  if (kind === 'clan') return { ...base, heritage: f.heritage.map((s) => s.trim()).filter(Boolean) };
  return {
    ...base, rarity: f.rarity, category: f.category, developable: f.developable, upgradable: f.upgradable,
    activation: f.activation, requirements: f.requirements, developmentRequirements: f.developmentRequirements,
    effects: cleanEffects(f.effects),
    variations: f.variations.filter((v) => v.name.trim()).map((v) => ({
      name: v.name.trim(), requirements: v.requirements, description: v.description, effects: cleanEffects(v.effects),
    })),
  };
}

function TextList({ legend, items, onChange, addLabel }) {
  const set = (i, v) => onChange(items.map((x, j) => (j === i ? v : x)));
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="panel-title text-sm">{legend}</legend>
      {items.map((t, i) => (
        <div key={i} className="flex gap-2">
          <input className="field flex-1" aria-label={`${legend} ${i + 1}`} value={t} onChange={(e) => set(i, e.target.value)} />
          <button type="button" className="btn-ghost" aria-label={`Remover ${legend} ${i + 1}`}
            onClick={() => onChange(items.filter((_, j) => j !== i))}>Remover</button>
        </div>
      ))}
      <button type="button" className="btn-secondary self-start" onClick={() => onChange([...items, ''])}>{addLabel}</button>
    </fieldset>
  );
}

function EffectsEditor({ legend, items, onChange }) {
  const set = (i, patch) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="panel-title text-sm">{legend}</legend>
      {items.map((e, i) => (
        <div key={i} className="flex flex-col gap-1 border-2 border-[var(--line)] p-2">
          <input className="field" aria-label={`Nome do efeito ${i + 1} (${legend})`} placeholder="Nome (opcional)" maxLength={100}
            value={e.name} onChange={(ev) => set(i, { name: ev.target.value })} />
          <textarea className="field" rows={2} aria-label={`Texto do efeito ${i + 1} (${legend})`} placeholder="Texto" maxLength={4000}
            value={e.text} onChange={(ev) => set(i, { text: ev.target.value })} />
          <button type="button" className="btn-ghost self-end" aria-label={`Remover efeito ${i + 1} (${legend})`}
            onClick={() => onChange(items.filter((_, j) => j !== i))}>Remover</button>
        </div>
      ))}
      <button type="button" className="btn-secondary self-start" onClick={() => onChange([...items, { name: '', text: '' }])}>Adicionar efeito</button>
    </fieldset>
  );
}

function VariationsEditor({ items, onChange }) {
  const set = (i, patch) => onChange(items.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="panel-title text-sm">Variações</legend>
      {items.map((v, i) => (
        <div key={i} className="panel p-3 flex flex-col gap-2">
          <label className="flex flex-col gap-1 text-sm">Nome da variação {i + 1}
            <input className="field" maxLength={100} value={v.name} onChange={(e) => set(i, { name: e.target.value })} />
          </label>
          <label className="flex flex-col gap-1 text-sm">Requisitos
            <textarea className="field" rows={2} maxLength={2000} value={v.requirements} onChange={(e) => set(i, { requirements: e.target.value })} />
          </label>
          <label className="flex flex-col gap-1 text-sm">Descrição
            <textarea className="field" rows={3} maxLength={4000} value={v.description} onChange={(e) => set(i, { description: e.target.value })} />
          </label>
          <EffectsEditor legend={`Efeitos da variação ${i + 1}`} items={v.effects} onChange={(effects) => set(i, { effects })} />
          <button type="button" className="btn-ghost self-end" aria-label={`Remover variação ${i + 1}`}
            onClick={() => onChange(items.filter((_, j) => j !== i))}>Remover variação</button>
        </div>
      ))}
      <button type="button" className="btn-secondary self-start"
        onClick={() => onChange([...items, { name: '', requirements: '', description: '', effects: [] }])}>Adicionar variação</button>
    </fieldset>
  );
}

export function Effects({ effects }) {
  if (!effects?.length) return null;
  return (
    <ul className="flex flex-col gap-2">
      {effects.map((e, i) => (
        <li key={i} className="flex flex-col gap-1">
          {e.name && <strong className="text-xs uppercase">{e.name}</strong>}
          <RichText text={e.text} className="text-sm" />
        </li>
      ))}
    </ul>
  );
}

function Requirements({ it }) {
  const dev = it.developable && it.developmentRequirements;
  if (!it.requirements && !dev) return null;
  return (
    <details className="border-2 border-[var(--line)] p-2">
      <summary className="cursor-pointer text-sm font-semibold uppercase">Requisitos</summary>
      <div className="mt-2 flex flex-col gap-2 text-sm">
        {it.requirements && <RichText text={it.requirements} />}
        {dev && (
          <div>
            <strong className="text-xs uppercase">Para desenvolver</strong>
            <RichText text={it.developmentRequirements} />
          </div>
        )}
      </div>
    </details>
  );
}

export function Variations({ variations }) {
  if (!variations?.length) return null;
  return (
    <details className="border-2 border-[var(--line)] p-2">
      <summary className="cursor-pointer text-sm font-semibold uppercase">Variações ({variations.length})</summary>
      <ul className="mt-2 flex flex-col gap-3">
        {variations.map((v, i) => (
          <li key={i} className="border-t-2 border-[var(--line)] pt-2 flex flex-col gap-2 first:border-t-0 first:pt-0">
            <strong>{v.name}</strong>
            {v.requirements && (
              <div className="text-sm"><strong className="text-xs uppercase">Requisitos</strong><RichText text={v.requirements} /></div>
            )}
            <RichText text={v.description} className="text-sm" />
            <Effects effects={v.effects} />
          </li>
        ))}
      </ul>
    </details>
  );
}

function NumGrid({ legend, keys, values, onChange, labelOf = (k) => k }) {
  return (
    <fieldset>
      <legend className="panel-title text-sm mb-1">{legend}</legend>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        {keys.map((k) => (
          <label key={k} className="flex flex-col text-xs uppercase gap-1">
            {labelOf(k)}
            <input type="number" className="field" value={values[k] ?? ''}
              onChange={(e) => onChange({ ...values, [k]: e.target.value })} />
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function ItemForm({ kind, initial, canGlobal, canCampaign, editing, onSaved, onCancel, campaignId }) {
  const toast = useToast();
  const [f, setF] = useState(initial);
  const [scope, setScope] = useState(canCampaign ? 'campaign' : 'global');
  const [busy, setBusy] = useState(false);
  const [fields, setFields] = useState({});
  const up = (patch) => setF((p) => ({ ...p, ...patch }));
  const isTrait = kind === 'trait';
  const attrKeys = ATTRS.map((a) => a.enumName);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setFields({});
    const data = toPayload(kind, f);
    const cid = scope === 'campaign' ? campaignId : null;
    try {
      if (editing) await (isTrait ? updateTrait : updateClan)(editing.id, data);
      else await (isTrait ? createTrait : createClan)(cid, data);
      toast(editing ? 'Item atualizado.' : 'Item criado.', 'success');
      onSaved();
    } catch (err) {
      const { message, fields: fe } = parseApiError(err);
      setFields(fe);
      toast(message, 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="panel p-4 flex flex-col gap-4" aria-label={editing ? 'Editar item' : 'Novo item'}>
      <h3 className="panel-title">{editing ? 'Editar' : 'Novo'} {isTrait ? 'traço' : 'clã'}</h3>
      {!editing && (
        <label className="flex flex-col gap-1 text-sm">Escopo
          <select className="field" value={scope} onChange={(e) => setScope(e.target.value)}>
            {canCampaign && <option value="campaign">Esta campanha</option>}
            {canGlobal && <option value="global">Sistema</option>}
          </select>
        </label>
      )}
      <label className="flex flex-col gap-1 text-sm">Nome
        <input className="field" required maxLength={100} value={f.name} onChange={(e) => up({ name: e.target.value })} />
        {fields.name && <span role="alert" className="text-xs">{fields.name}</span>}
      </label>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {isTrait && (
          <>
            <label className="flex flex-col gap-1 text-sm">Raridade
              <select className="field" value={f.rarity} onChange={(e) => up({ rarity: e.target.value })}>
                {RARITIES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">Ativação
              <select className="field" value={f.activation} onChange={(e) => up({ activation: e.target.value })}>
                {ACTIVATIONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm">Categoria
              <select className="field" value={f.category} onChange={(e) => up({ category: e.target.value })}>
                {CATEGORIES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </label>
          </>
        )}
        <label className="flex flex-col gap-1 text-sm">Nº de sorteio ({isTrait && f.rarity === 'DIVINE' ? '1–10' : '1–20'}, vazio = reposição)
          <input type="number" min={1} max={isTrait && f.rarity === 'DIVINE' ? 10 : 20} className="field"
            value={f.rollNumber} onChange={(e) => up({ rollNumber: e.target.value })} />
          {fields.rollNumber && <span role="alert" className="text-xs">{fields.rollNumber}</span>}
        </label>
      </div>
      <label className="flex flex-col gap-1 text-sm">Descrição
        <textarea className="field" rows={3} maxLength={4000} value={f.description} onChange={(e) => up({ description: e.target.value })} />
      </label>
      {isTrait && (
        <>
          <label className="flex flex-col gap-1 text-sm">Requisitos
            <textarea className="field" rows={2} maxLength={2000} value={f.requirements} onChange={(e) => up({ requirements: e.target.value })} />
          </label>
          <label className="flex flex-col gap-1 text-sm">Requisitos para desenvolver
            <textarea className="field" rows={2} maxLength={2000} value={f.developmentRequirements} onChange={(e) => up({ developmentRequirements: e.target.value })} />
          </label>
        </>
      )}
      {isTrait && <EffectsEditor legend="Efeitos" items={f.effects} onChange={(effects) => up({ effects })} />}
      {isTrait && <VariationsEditor items={f.variations} onChange={(variations) => up({ variations })} />}
      {!isTrait && <TextList legend="Herança" items={f.heritage} onChange={(heritage) => up({ heritage })} addLabel="Adicionar herança" />}

      <fieldset className="flex flex-col gap-3">
        <legend className="panel-title">Ganhos da construção</legend>
        <NumGrid legend="Atributos (fixo)" keys={attrKeys} values={f.flat} onChange={(flat) => up({ flat })} labelOf={(k) => ATTR_NAMES[k]} />
        <NumGrid legend="Atributos (%)" keys={attrKeys} values={f.percent} onChange={(percent) => up({ percent })} labelOf={(k) => ATTR_NAMES[k]} />
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {[['maxHp', 'PV máx.'], ['maxSp', 'PE máx.'], ['maxKp', 'PK máx.'], ['freePoints', 'Pontos livres']].map(([k, l]) => (
            <label key={k} className="flex flex-col text-xs uppercase gap-1">{l}
              <input type="number" className="field" value={f[k]} onChange={(e) => up({ [k]: e.target.value })} />
            </label>
          ))}
        </div>
        <NumGrid legend="Limitadores" keys={LIMITER_KEYS} values={f.limiters} onChange={(limiters) => up({ limiters })}
          labelOf={(k) => ATTR_NAMES[k] ?? k} />
      </fieldset>

      {isTrait && (
        <div className="flex gap-4 text-sm">
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.developable} onChange={(e) => up({ developable: e.target.checked })} />Desenvolvível</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.upgradable} onChange={(e) => up({ upgradable: e.target.checked })} />Evoluível</label>
        </div>
      )}
      <div className="flex gap-2">
        <button className="btn-primary" disabled={busy || !f.name.trim()}>{busy ? 'Salvando…' : 'Salvar'}</button>
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}

function ImportPanel({ campaignId, canGlobal, canCampaign, onDone }) {
  const toast = useToast();
  const [parsed, setParsed] = useState(null);
  const [scope, setScope] = useState(canCampaign ? 'campaign' : 'global');
  const [busy, setBusy] = useState(false);

  async function pick(e) {
    const file = e.target.files?.[0];
    setParsed(null);
    if (!file) return;
    try {
      const json = JSON.parse(await file.text());
      const data = Array.isArray(json) ? { traits: json, clans: [], arts: [], instincts: [], milestones: [] }
        : { traits: json.traits ?? [], clans: json.clans ?? [], arts: json.arts ?? [], instincts: json.instincts ?? [], milestones: json.milestones ?? [] };
      if (!Array.isArray(data.traits) || !Array.isArray(data.clans) || !Array.isArray(data.arts)) throw new Error('formato');
      setParsed(data);
    } catch {
      toast('Arquivo inválido: esperado JSON com { "traits", "clans", "arts", "instincts", "milestones" } ou um array de traços.', 'error');
    }
  }
  async function send() {
    setBusy(true);
    try {
      const r = await importCatalog(scope === 'campaign' ? campaignId : null, parsed);
      toast(`Importado: ${r.traits} traço(s), ${r.clans} clã(s), ${r.arts ?? 0} arte(s), ${r.instincts ?? 0} Instinto(s) e ${r.milestones ?? 0} Marco(s).`, 'success');
      setParsed(null);
      onDone();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel p-4 flex flex-col gap-3" aria-label="Importar catálogo">
      <h3 className="panel-title">Importar JSON</h3>
      <input type="file" accept=".json,application/json" onChange={pick} aria-label="Arquivo JSON do catálogo" />
      {parsed && (
        <>
          <p className="caption">Prévia: {parsed.traits.length} traço(s), {parsed.clans.length} clã(s), {parsed.arts.length} arte(s), {parsed.instincts.length} Instinto(s) e {parsed.milestones.length} Marco(s). Itens com mesmo nome no escopo serão atualizados.</p>
          <label className="flex flex-col gap-1 text-sm">Escopo
            <select className="field" value={scope} onChange={(e) => setScope(e.target.value)}>
              {canCampaign && <option value="campaign">Esta campanha</option>}
              {canGlobal && <option value="global">Sistema</option>}
            </select>
          </label>
          <button className="btn-primary self-start" onClick={send} disabled={busy}>{busy ? 'Importando…' : 'Importar'}</button>
        </>
      )}
      <details>
        <summary className="cursor-pointer text-sm">Exemplo de formato</summary>
        <pre className="text-xs overflow-x-auto mt-2">{EXAMPLE}</pre>
      </details>
    </section>
  );
}

export default function CatalogManager({ campaignId, isMaster }) {
  const toast = useToast();
  const [tab, setTab] = useState('trait');
  const [traits, setTraits] = useState([]);
  const [clans, setClans] = useState([]);
  const [globalEditor, setGlobalEditor] = useState(false);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(null); // { editing?, initial }
  const [showImport, setShowImport] = useState(false);
  const [q, setQ] = useState('');
  const [rarity, setRarity] = useState('');
  const [category, setCategory] = useState('');
  const [scope, setScope] = useState('');
  const [artsKey, setArtsKey] = useState(0);

  const load = useCallback(async () => {
    try {
      const [t, c] = await Promise.all([listTraits(campaignId), listClans(campaignId)]);
      setTraits(t); setClans(c);
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setLoading(false);
    }
  }, [campaignId, toast]);

  useEffect(() => {
    let active = true;
    getCatalogPermissions().then((p) => active && setGlobalEditor(!!p.globalEditor)).catch(() => {});
    load();
    return () => { active = false; };
  }, [load]);

  const canCreate = globalEditor || isMaster;
  const canEdit = (it) => (it.campaignId ? isMaster : globalEditor);
  const isTrait = tab === 'trait';
  const isArt = tab === 'art';
  const isProgression = tab === 'instinct' || tab === 'milestone';

  const items = useMemo(() => {
    const term = q.trim().toLowerCase();
    return (isTrait ? traits : clans)
      .filter((i) => !term || i.name.toLowerCase().includes(term))
      .filter((i) => !isTrait || ((!rarity || i.rarity === rarity) && (!category || i.category === category)))
      .filter((i) => !scope || (scope === 'system' ? !i.campaignId : !!i.campaignId))
      .sort(isTrait ? compareTraits : (a, b) => (a.rollNumber ?? 99) - (b.rollNumber ?? 99) || a.name.localeCompare(b.name));
  }, [isTrait, traits, clans, q, rarity, category, scope]);

  async function remove(it) {
    if (!window.confirm(`Excluir "${it.name}"?`)) return;
    try {
      await (isTrait ? deleteTrait : deleteClan)(it.id);
      toast('Item excluído.', 'success');
      load();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  const saved = () => { setForm(null); load(); };

  return (
    <div className="flex flex-col gap-4">
      <div role="tablist" aria-label="Catálogo" className="flex gap-2">
        {[['trait', 'Traços'], ['clan', 'Clãs'], ['art', 'Artes'], ['instinct', 'Instintos'], ['milestone', 'Marcos']].map(([k, l]) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'btn-primary' : 'btn-secondary'}
            onClick={() => { setTab(k); setForm(null); }}>{l}</button>
        ))}
        {canCreate && (
          <>
            {!isArt && !isProgression && (
              <button className="btn-secondary ml-auto" onClick={() => { setShowImport(false); setForm({ initial: emptyForm(tab) }); }}>
                Novo {isTrait ? 'traço' : 'clã'}
              </button>
            )}
            <button className={isArt || isProgression ? 'btn-secondary ml-auto' : 'btn-secondary'} onClick={() => { setForm(null); setShowImport((s) => !s); }}>Importar</button>
          </>
        )}
      </div>

      {!canCreate && <p className="caption">Somente leitura: apenas o Mestre e editores do sistema alteram o catálogo.</p>}

      {showImport && canCreate && (
        <ImportPanel campaignId={campaignId} canGlobal={globalEditor} canCampaign={isMaster}
          onDone={() => { setShowImport(false); load(); setArtsKey((k) => k + 1); }} />
      )}

      {form && (
        <ItemForm key={form.editing?.id ?? 'new'} kind={tab} initial={form.initial} editing={form.editing}
          campaignId={campaignId} canGlobal={globalEditor} canCampaign={isMaster}
          onSaved={saved} onCancel={() => setForm(null)} />
      )}

      {isProgression && <ProgressionCatalog campaignId={campaignId} kind={tab} refreshKey={artsKey} />}
      {isArt && <ArtsPanel campaignId={campaignId} isMaster={isMaster} globalEditor={globalEditor} refreshKey={artsKey} />}

      {!isArt && !isProgression && <div className="panel p-3 flex flex-wrap gap-3 items-end" role="search">
        <label className="flex flex-col text-xs uppercase gap-1">Buscar
          <input className="field" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Nome…" />
        </label>
        {isTrait && (
          <>
            <label className="flex flex-col text-xs uppercase gap-1">Raridade
              <select className="field" value={rarity} onChange={(e) => setRarity(e.target.value)}>
                <option value="">Todas</option>
                {RARITIES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </label>
            <label className="flex flex-col text-xs uppercase gap-1">Categoria
              <select className="field" value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="">Todas</option>
                {CATEGORIES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </label>
          </>
        )}
        <label className="flex flex-col text-xs uppercase gap-1">Escopo
          <select className="field" value={scope} onChange={(e) => setScope(e.target.value)}>
            <option value="">Todos</option>
            <option value="system">Sistema</option>
            <option value="campaign">Campanha</option>
          </select>
        </label>
      </div>}

      {isArt || isProgression ? null : loading ? (
        <div className="skeleton h-24" aria-busy="true" />
      ) : items.length === 0 ? (
        <p className="caption">Nenhum item encontrado.</p>
      ) : (
        <ul className="page-gutter flex flex-col">
          {items.map((it) => (
            <li key={it.id} className="panel p-3 flex flex-col gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <span className="tag" title="Número de sorteio">{it.rollNumber ?? 'X'}</span>
                <strong className="panel-title">{it.name}</strong>
                {isTrait && <span className="tag" style={rarityTagStyle(it.rarity)}>{lab(RARITIES, it.rarity)}</span>}
                {isTrait && <span className="tag">{lab(CATEGORIES, it.category)}</span>}
                <span className="tag">{it.campaignId ? 'Campanha' : 'Sistema'}</span>
                <span className="tag">v{it.version}</span>
                {canEdit(it) && (
                  <span className="ml-auto flex gap-2">
                    <button className="btn-secondary" onClick={() => { setShowImport(false); setForm({ editing: it, initial: fromItem(tab, it) }); }}
                      aria-label={`Editar ${it.name}`}>Editar</button>
                    <button className="btn-ghost" onClick={() => remove(it)} aria-label={`Excluir ${it.name}`}>Excluir</button>
                  </span>
                )}
              </div>
              {isTrait && <Requirements it={it} />}
              {isTrait && (
                <div className="flex flex-wrap gap-2">
                  <span className="tag">{lab(ACTIVATIONS, it.activation ?? 'PASSIVE')}</span>
                  {it.developable && <span className="tag-accent">Desenvolvível</span>}
                </div>
              )}
              {isTrait ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <RichText text={it.description} className="text-sm" />
                  <Effects effects={it.effects} />
                </div>
              ) : <RichText text={it.description} className="text-sm" />}
              {isTrait && <Variations variations={it.variations} />}
              {!isTrait && it.heritage?.length > 0 && (
                <ul className="text-sm list-disc pl-5">{it.heritage.map((h, i) => <li key={i}>{h}</li>)}</ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
