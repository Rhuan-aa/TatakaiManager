import { useCallback, useEffect, useState } from 'react';
import { createArt, listArts } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { ART_CATEGORIES, GRADS, lab } from '../catalog/artLabels';

/**
 * Arte marcial opcional da ficha simples do NPC (§51.8): escolhe uma do catálogo + graduação, ou cria uma "arte
 * rápida" vazia (qualquer categoria, oculta ou não do catálogo) para preencher bases/técnicas/passivas depois
 * na aba Catálogo. `value = { artId, graduation }`.
 */
export default function NpcMartialArt({ campaignId, value, onChange }) {
  const [arts, setArts] = useState(null);
  const [quick, setQuick] = useState(null); // null | { name, category, hidden }
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => listArts(campaignId).then(setArts).catch((e) => setError(parseApiError(e).message)), [campaignId]);
  useEffect(() => { load(); }, [load]);

  async function createQuick(e) {
    e.preventDefault();
    if (!quick.name.trim()) { setError('Dê um nome à arte.'); return; }
    setBusy(true);
    setError('');
    try {
      const art = await createArt(campaignId, {
        name: quick.name.trim(), category: quick.category, hidden: quick.hidden,
        bases: [], techniques: [], passives: [], sections: [],
      });
      await load();
      onChange({ artId: art.id, graduation: value.graduation || 'BEGINNER' });
      setQuick(null);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <fieldset className="panel p-3">
      <legend className="caption">Arte marcial <span className="opacity-70">(opcional)</span></legend>
      <div className="mt-2 flex flex-wrap items-end gap-3">
        <label className="label min-w-48 flex-1">Arte do catálogo
          <select className="field mt-1 w-full" value={value.artId ?? ''} disabled={!arts}
            onChange={(e) => onChange({ artId: e.target.value || null, graduation: value.graduation || 'BEGINNER' })}>
            <option value="">— nenhuma —</option>
            {(arts ?? []).map((a) => (
              <option key={a.id} value={a.id}>{a.name} ({lab(ART_CATEGORIES, a.category)}{a.hidden ? ', oculta' : ''})</option>
            ))}
          </select>
        </label>
        {value.artId && (
          <label className="label">Graduação
            <select className="field mt-1 w-full" value={value.graduation || 'BEGINNER'}
              onChange={(e) => onChange({ ...value, graduation: e.target.value })}>
              {GRADS.filter((g) => g.value !== 'GENERIC').map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
            </select>
          </label>
        )}
        {!quick && (
          <button type="button" className="btn-secondary text-sm"
            onClick={() => setQuick({ name: '', category: 'CREATED', hidden: true })}>+ Arte rápida</button>
        )}
      </div>

      {quick && (
        <div className="mt-3 grid gap-2 border-t-2 border-[var(--line)] pt-3 sm:grid-cols-[1fr_auto_auto] sm:items-end">
          <label className="label">Nome da arte
            <input className="field mt-1 w-full" value={quick.name} maxLength={100}
              onChange={(e) => setQuick((q) => ({ ...q, name: e.target.value }))} />
          </label>
          <label className="label">Regras de
            <select className="field mt-1 w-full" value={quick.category}
              onChange={(e) => setQuick((q) => ({ ...q, category: e.target.value }))}>
              {ART_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </label>
          <label className="label flex items-center gap-2">
            <input type="checkbox" checked={quick.hidden} onChange={(e) => setQuick((q) => ({ ...q, hidden: e.target.checked }))} />
            Oculta no catálogo
          </label>
          <div className="flex gap-2 sm:col-span-3">
            <button type="button" className="btn-primary text-sm" disabled={busy} onClick={createQuick}>
              {busy ? 'Criando…' : 'Criar arte vazia'}
            </button>
            <button type="button" className="btn-ghost text-sm" onClick={() => setQuick(null)}>Cancelar</button>
          </div>
          <p className="hint sm:col-span-3">Nasce sem bases, técnicas ou passivas: adicione depois na aba Catálogo → Artes.</p>
        </div>
      )}
      {error && <p className="mt-2 text-sm text-[var(--accent)]">{error}</p>}
    </fieldset>
  );
}
