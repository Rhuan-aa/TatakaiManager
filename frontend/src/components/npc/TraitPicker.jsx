import { useState } from 'react';
import { createTrait } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { RARITY, TRAIT_CATEGORIES, compareTraits, rarityTagStyle } from '../ui/rarity';

const EMPTY = { name: '', rarity: 'COMMON', category: 'PHYSICAL', description: '' };

/** Escolhe traços do catálogo; "Novo traço de campanha" cria um no catálogo da campanha e já seleciona. */
export default function TraitPicker({ campaignId, catalog, onCatalogChange, selected, onChange }) {
  const toast = useToast();
  const [choice, setChoice] = useState('');
  const [draft, setDraft] = useState(null);
  const [busy, setBusy] = useState(false);
  const available = catalog.filter((t) => !selected.some((s) => s.id === t.id)).sort(compareTraits);

  function add() {
    const t = catalog.find((c) => c.id === choice);
    if (t) onChange([...selected, t]);
    setChoice('');
  }

  async function create() {
    if (!draft.name.trim()) return;
    setBusy(true);
    try {
      const t = await createTrait(campaignId, { ...draft, name: draft.name.trim(), description: draft.description.trim() || null });
      onCatalogChange([...catalog, t]);
      onChange([...selected, t]);
      setDraft(null);
      toast('Traço de campanha criado.');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const up = (k) => (e) => setDraft((d) => ({ ...d, [k]: e.target.value }));

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="panel-title text-sm">Traços</legend>
      {selected.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {selected.map((t) => (
            <li key={t.id} className="bubble flex items-center gap-2 px-2 py-1 text-sm">
              <strong>{t.name}</strong>
              <span className="tag" style={rarityTagStyle(t.rarity)}>{RARITY[t.rarity]?.label ?? t.rarity}</span>
              <button type="button" className="btn-ghost px-1" aria-label={`Remover ${t.name}`}
                onClick={() => onChange(selected.filter((s) => s.id !== t.id))}>×</button>
            </li>
          ))}
        </ul>
      )}
      <div className="flex gap-2">
        <select className="field m-0 flex-1" aria-label="Traço do catálogo" value={choice} onChange={(e) => setChoice(e.target.value)}>
          <option value="">Escolha um traço do catálogo…</option>
          {available.map((t) => <option key={t.id} value={t.id}>{t.name} ({RARITY[t.rarity]?.label ?? t.rarity})</option>)}
        </select>
        <button type="button" className="btn-secondary" disabled={!choice} onClick={add}>Adicionar</button>
      </div>
      {draft == null ? (
        <button type="button" className="btn-ghost self-start text-sm" onClick={() => setDraft(EMPTY)}>+ Novo traço de campanha</button>
      ) : (
        <div className="panel flex flex-col gap-2 p-3">
          <label className="text-xs uppercase">Nome
            <input className="field" maxLength={100} value={draft.name} onChange={up('name')} />
          </label>
          <div className="grid grid-cols-2 gap-2">
            <label className="text-xs uppercase">Raridade
              <select className="field" value={draft.rarity} onChange={up('rarity')}>
                {Object.entries(RARITY).map(([k, r]) => <option key={k} value={k}>{r.label}</option>)}
              </select>
            </label>
            <label className="text-xs uppercase">Categoria
              <select className="field" value={draft.category} onChange={up('category')}>
                {TRAIT_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
            </label>
          </div>
          <label className="text-xs uppercase">Descrição
            <textarea className="field" rows={2} maxLength={4000} value={draft.description} onChange={up('description')} />
          </label>
          <div className="flex gap-2">
            <button type="button" className="btn-primary text-sm" disabled={busy || !draft.name.trim()} onClick={create}>Criar traço</button>
            <button type="button" className="btn-ghost text-sm" onClick={() => setDraft(null)}>Cancelar</button>
          </div>
        </div>
      )}
    </fieldset>
  );
}
