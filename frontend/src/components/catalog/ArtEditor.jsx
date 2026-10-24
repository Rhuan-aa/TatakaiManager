import { useEffect, useState } from 'react';
import { checkAssembly, createArt, listModules, submitArt, updateArt } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { ART_CATEGORIES, BASE_FUNCTIONS, CORES, GRADS, emptyBase, emptyTechnique } from './artLabels';

const emptyItem = () => ({ module: null, label: '', qty: 1, cost: 1, kind: 'UNIQUE', nonAffine: false, onDie: false, note: null });
const splitList = (s) => s.split(',').map((x) => x.trim()).filter(Boolean);

/** Custo do item pelo módulo: unidade × qtd, +1 não afim, efeito no dado = ceil(custo/2) mín. 1. */
function moduleCost(mod, it) {
  let c = mod.perUnit ? mod.cost * Math.max(1, it.qty) : mod.cost;
  if (it.nonAffine) c += 1;
  if (it.onDie) c = Math.max(1, Math.ceil(c / 2));
  return c;
}

function ItemsEditor({ items, modules, base, onChange }) {
  const byName = Object.fromEntries(modules.map((m) => [m.name, m]));
  const groups = [...new Set(modules.map((m) => m.group))];
  const set = (i, patch) => onChange(items.map((it, j) => {
    if (j !== i) return it;
    const next = { ...it, ...patch };
    const mod = byName[next.module];
    if (mod && !('cost' in patch)) next.cost = moduleCost(mod, next);
    next.kind = mod ? (mod.kind === 'EFFECT' ? 'EFFECT' : next.cost < 0 ? 'LIMITATION' : 'MODULE')
      : next.cost < 0 ? 'LIMITATION' : 'UNIQUE';
    return next;
  }));

  return (
    <div className="flex flex-col gap-2">
      {items.map((it, i) => {
        const mod = byName[it.module];
        return (
          <div key={i} className="grid grid-cols-2 items-end gap-2 border-t-2 border-[var(--line)] pt-2 sm:grid-cols-[2fr_2fr_4rem_4rem_auto]">
            <label className="text-xs uppercase">Módulo
              <select className="field" value={it.module ?? ''} onChange={(e) => set(i, {
                module: e.target.value || null, label: e.target.value || it.label, qty: 1,
              })}>
                <option value="">— Item único / livre —</option>
                {groups.map((g) => (
                  <optgroup key={g} label={g}>
                    {modules.filter((m) => m.group === g).map((m) => (
                      <option key={m.name} value={m.name}>{m.name} ({m.cost > 0 ? '+' : ''}{m.cost}{m.perUnit ? '/un.' : ''})</option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </label>
            <label className="text-xs uppercase">Rótulo
              <input className="field" maxLength={200} value={it.label} onChange={(e) => set(i, { label: e.target.value })}
                placeholder={mod ? 'Ex.: Ponto Vital na Têmpora' : 'Nome do item único'} />
            </label>
            <label className="text-xs uppercase">Qtd
              <input type="number" min={1} className="field" disabled={!mod?.perUnit} value={it.qty}
                onChange={(e) => set(i, { qty: Number(e.target.value) || 1 })} />
            </label>
            <label className="text-xs uppercase">Custo
              <input type="number" className="field" value={it.cost} onChange={(e) => set(i, { cost: Number(e.target.value) })} />
            </label>
            <div className="col-span-2 flex flex-wrap items-center gap-3 text-xs sm:col-span-1">
              {base && (
                <label className="flex items-center gap-1"><input type="checkbox" checked={it.nonAffine}
                  onChange={(e) => set(i, { nonAffine: e.target.checked })} /> Não afim</label>
              )}
              {it.kind === 'EFFECT' && (
                <label className="flex items-center gap-1"><input type="checkbox" checked={it.onDie}
                  onChange={(e) => set(i, { onDie: e.target.checked })} /> No dado</label>
              )}
              <button type="button" className="btn-ghost px-2" aria-label="Remover item"
                onClick={() => onChange(items.filter((_, j) => j !== i))}>×</button>
            </div>
            {mod?.note && <p className="col-span-full text-xs opacity-70">{mod.note}</p>}
          </div>
        );
      })}
      <button type="button" className="btn-secondary self-start text-sm" onClick={() => onChange([...items, emptyItem()])}>+ Item</button>
    </div>
  );
}

/** Complexidade, gasto, PT, teto e problemas calculados pelo servidor (debounce de 400 ms). */
function CheckBar({ category, technique, base }) {
  const [check, setCheck] = useState(null);
  const body = JSON.stringify(technique ? { category, technique } : { category, base });
  useEffect(() => {
    let active = true;
    const id = setTimeout(() => {
      checkAssembly(JSON.parse(body)).then((c) => active && setCheck(c)).catch(() => active && setCheck(null));
    }, 400);
    return () => { active = false; clearTimeout(id); };
  }, [body]);
  if (!check) return null;
  const over = check.finalComplexity > check.ceiling;
  return (
    <div className="flex flex-col gap-1" aria-live="polite">
      <div className="flex flex-wrap gap-2 text-xs">
        <span className="tag" style={{ borderColor: 'var(--stat-complexity)', color: 'var(--stat-complexity)' }}>
          Complexidade {check.rawComplexity} / {check.finalComplexity}
        </span>
        <span className="tag" style={over ? { borderColor: 'var(--accent)', color: 'var(--accent)' } : undefined}>Teto {check.ceiling}</span>
        {check.spCosts.length > 0 && (
          <span className="tag" style={{ borderColor: 'var(--stat-cost)', color: 'var(--stat-cost)' }}>SP {check.spCosts.join(' / ')}</span>
        )}
        <span className="tag" style={{ borderColor: 'var(--stat-training)', color: 'var(--stat-training)' }}>PT {check.trainingPoints}</span>
      </div>
      {check.issues.length > 0 && (
        <ul className="list-disc pl-5 text-xs" style={{ color: 'var(--accent)' }}>
          {check.issues.map((s) => <li key={s}>{s}</li>)}
        </ul>
      )}
    </div>
  );
}

function MoveFields({ m, onChange, technique }) {
  const up = (k) => (e) => onChange({ ...m, [k]: e.target.value });
  return (
    <div className="grid gap-2 sm:grid-cols-3">
      <label className="text-xs uppercase sm:col-span-2">Nome
        <input className="field" required maxLength={100} value={m.name} onChange={up('name')} />
      </label>
      <label className="text-xs uppercase">Graduação
        <select className="field" value={m.graduation} onChange={up('graduation')}>
          {GRADS.map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
        </select>
      </label>
      {technique ? (
        <label className="text-xs uppercase">Alcance
          <input className="field" maxLength={50} value={m.range ?? ''} onChange={up('range')} />
        </label>
      ) : (
        <label className="text-xs uppercase">Função
          <select className="field" value={m.function ?? ''} onChange={up('function')}>
            {BASE_FUNCTIONS.map((f) => <option key={f} value={f}>{f}</option>)}
          </select>
        </label>
      )}
      <label className="text-xs uppercase sm:col-span-3">Descrição
        <textarea className="field" rows={2} value={m.description ?? ''} onChange={up('description')} />
      </label>
      <label className="text-xs uppercase sm:col-span-3">Efeito
        <textarea className="field" rows={2} value={m.effect ?? ''} onChange={up('effect')} />
      </label>
    </div>
  );
}

export function TechniqueEditor({ t, category, modules, onChange, onRemove }) {
  const setVar = (i, patch) => onChange({ ...t, variations: t.variations.map((v, j) => (j === i ? { ...v, ...patch } : v)) });
  const two = t.variations.length > 1;
  return (
    <details className="panel p-3" open={!t.name}>
      <summary className="cursor-pointer panel-title">{t.name || 'Nova técnica'}</summary>
      <div className="mt-3 flex flex-col gap-3">
        <MoveFields m={t} onChange={onChange} technique />
        {t.variations.map((v, i) => (
          <fieldset key={i} className="flex flex-col gap-2 border-2 border-[var(--line)] p-2">
            <legend className="text-xs font-bold uppercase">{two ? `Variação ${i + 1}` : 'Montagem'}</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {two && (
                <label className="text-xs uppercase">Nome da variação
                  <input className="field" maxLength={100} value={v.name ?? ''} onChange={(e) => setVar(i, { name: e.target.value })} />
                </label>
              )}
              <label className="text-xs uppercase">Núcleo
                <select className="field" value={v.core ?? ''} onChange={(e) => setVar(i, { core: e.target.value || null })}>
                  {CORES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
                </select>
              </label>
            </div>
            <ItemsEditor items={v.items} modules={modules} onChange={(items) => setVar(i, { items })} />
            {two && (
              <button type="button" className="btn-ghost self-start text-sm"
                onClick={() => onChange({ ...t, variations: t.variations.filter((_, j) => j !== i), extras: [] })}>Remover variação</button>
            )}
          </fieldset>
        ))}
        {!two && (
          <button type="button" className="btn-ghost self-start text-sm"
            onClick={() => onChange({ ...t, variations: [...t.variations, { name: '', core: t.variations[0]?.core ?? 'STRIKE', items: [] }] })}>
            + Segunda variação
          </button>
        )}
        {two && (
          <fieldset className="flex flex-col gap-2 border-2 border-[var(--line)] p-2">
            <legend className="text-xs font-bold uppercase">Sobre a escolha da variação (soma direto)</legend>
            <ItemsEditor items={t.extras} modules={modules} onChange={(extras) => onChange({ ...t, extras })} />
          </fieldset>
        )}
        <CheckBar category={category} technique={t} />
        <button type="button" className="btn-ghost self-start text-sm" onClick={onRemove}>Remover técnica</button>
      </div>
    </details>
  );
}

export function BaseEditor({ b, category, modules, onChange, onRemove }) {
  return (
    <details className="panel p-3" open={!b.name}>
      <summary className="cursor-pointer panel-title">{b.name || 'Nova base'}</summary>
      <div className="mt-3 flex flex-col gap-3">
        <MoveFields m={b} onChange={onChange} />
        <ItemsEditor items={b.items} modules={modules} base onChange={(items) => onChange({ ...b, items })} />
        <CheckBar category={category} base={b} />
        <button type="button" className="btn-ghost self-start text-sm" onClick={onRemove}>Remover base</button>
      </div>
    </details>
  );
}

/** Criação Marcial: cria (sem `art`) ou edita uma arte. Jogador só cria Arte Criada (o servidor força). */
export default function ArtEditor({ campaignId, art, canChooseCategory, onSaved, onCancel }) {
  const toast = useToast();
  const [modules, setModules] = useState([]);
  const [busy, setBusy] = useState(false);
  const [f, setF] = useState(() => ({
    ...art,
    name: art?.name ?? '',
    category: art?.category ?? 'CREATED',
    martialType: art?.martialType ?? '',
    description: art?.description ?? '',
    attack: (art?.attackSpecializations ?? []).join(', '),
    reaction: (art?.reactionSpecializations ?? []).join(', '),
    techniques: (art?.techniques ?? []).map((t) => ({ ...t, variations: t.variations?.length ? t.variations : emptyTechnique().variations, extras: t.extras ?? [] })),
    bases: (art?.bases ?? []).map((b) => ({ ...b, items: b.items ?? [] })),
  }));

  useEffect(() => {
    let active = true;
    listModules().then((m) => active && setModules(m)).catch((err) => toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [toast]);

  const up = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.value }));
  const techModules = modules.filter((m) => m.appliesTo === 'TECHNIQUE');
  const baseModules = modules.filter((m) => m.appliesTo === 'BASE');
  const setList = (key, i, v) => setF((p) => ({ ...p, [key]: p[key].map((x, j) => (j === i ? v : x)) }));
  const dropList = (key, i) => setF((p) => ({ ...p, [key]: p[key].filter((_, j) => j !== i) }));

  function payload() {
    return {
      name: f.name.trim(), category: f.category, rank: f.rank ?? null, martialType: f.martialType || null,
      attackSpecializations: splitList(f.attack), reactionSpecializations: splitList(f.reaction),
      requirement: f.requirement ?? null, description: f.description || null,
      sections: f.sections ?? [], passives: f.passives ?? [], bases: f.bases, techniques: f.techniques,
      borrowed: f.borrowed ?? [],
    };
  }

  async function save(andSubmit) {
    setBusy(true);
    try {
      let saved = art?.id ? await updateArt(art.id, payload()) : await createArt(campaignId, payload());
      if (andSubmit) saved = await submitArt(saved.id);
      toast(andSubmit ? 'Arte enviada para aprovação.' : 'Arte salva.', 'success');
      onSaved(saved);
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const draft = !art?.id || art.status === 'DRAFT';
  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); save(false); }}>
      <header className="panel flex flex-col gap-3 p-4">
        <h3 className="panel-title text-2xl">{art?.id ? `Editar ${art.name}` : 'Criação Marcial'}</h3>
        {art?.reviewNote && <p className="caption self-start">Mestre: {art.reviewNote}</p>}
        <div className="grid gap-2 sm:grid-cols-3">
          <label className="text-xs uppercase sm:col-span-2">Nome da arte
            <input className="field" required maxLength={100} value={f.name} onChange={up('name')} />
          </label>
          <label className="text-xs uppercase">Categoria
            <select className="field" value={f.category} disabled={!canChooseCategory} onChange={up('category')}>
              {ART_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </label>
          <label className="text-xs uppercase">Tipo marcial
            <input className="field" maxLength={200} value={f.martialType} onChange={up('martialType')} placeholder="Ex.: Trocação" />
          </label>
          <label className="text-xs uppercase">Especializações de ataque
            <input className="field" value={f.attack} onChange={up('attack')} placeholder="Separadas por vírgula" />
          </label>
          <label className="text-xs uppercase">Especializações de reação
            <input className="field" value={f.reaction} onChange={up('reaction')} placeholder="Separadas por vírgula" />
          </label>
          <label className="text-xs uppercase sm:col-span-3">Descrição
            <textarea className="field" rows={3} maxLength={10000} value={f.description} onChange={up('description')} />
          </label>
        </div>
      </header>

      <section className="flex flex-col gap-2" aria-label="Bases">
        <h4 className="panel-title">Bases</h4>
        {f.bases.map((b, i) => (
          <BaseEditor key={i} b={b} category={f.category} modules={baseModules}
            onChange={(v) => setList('bases', i, v)} onRemove={() => dropList('bases', i)} />
        ))}
        <button type="button" className="btn-secondary self-start" onClick={() => setF((p) => ({ ...p, bases: [...p.bases, emptyBase()] }))}>+ Base</button>
      </section>

      <section className="flex flex-col gap-2" aria-label="Técnicas">
        <h4 className="panel-title">Técnicas</h4>
        {f.techniques.map((t, i) => (
          <TechniqueEditor key={i} t={t} category={f.category} modules={techModules}
            onChange={(v) => setList('techniques', i, v)} onRemove={() => dropList('techniques', i)} />
        ))}
        <button type="button" className="btn-secondary self-start" onClick={() => setF((p) => ({ ...p, techniques: [...p.techniques, emptyTechnique()] }))}>+ Técnica</button>
      </section>

      <div className="flex flex-wrap gap-2">
        <button type="submit" className="btn-secondary" disabled={busy}>Salvar</button>
        {draft && !canChooseCategory && (
          <button type="button" className="btn-primary" disabled={busy} onClick={() => save(true)}>Salvar e enviar para aprovação</button>
        )}
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}
