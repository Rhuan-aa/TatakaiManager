import { useEffect, useState } from 'react';
import { createRandomGmSheet } from '../../api/characterSheets';
import { listArts } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { ART_CATEGORIES, GRADS, lab } from '../catalog/artLabels';
import { randomName } from '../npc/randomNpc';
import Icon from '../ui/Icon';
import { RARITY, TRAIT_CATEGORIES } from '../ui/rarity';

const PROFILES = [
  { value: 'NOVICE', label: 'Novato', hint: 'atributos baixos, EL 0, 1 traço' },
  { value: 'EXPERIENCED', label: 'Experiente', hint: 'EL 8, 2 traços' },
  { value: 'MASTER', label: 'Mestre', hint: 'EL 20, 3 traços, arte Intermediária' },
  { value: 'RIVAL', label: 'Rival', hint: 'EL 32, 3 traços, arte Intermediária' },
  { value: 'BOSS', label: 'Chefe', hint: 'EL 58, 4 traços, arte de Mestre' },
];

const TRAITS_BY_PROFILE = { NOVICE: 1, EXPERIENCED: 2, MASTER: 3, RIVAL: 3, BOSS: 4 };

/** Menu do gerador de ficha aleatória (§51.9): perfil, arte marcial e traços. */
export default function RandomSheetForm({ campaignId, onCreated, onCancel }) {
  const [f, setF] = useState({
    profile: 'NOVICE', name: randomName(), visible: false, artMode: 'RANDOM', artId: '', graduation: '',
    traits: [], // linhas de sorteio; vazio = quantidade do perfil
  });
  const [arts, setArts] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const setLine = (i, k, v) => setF((p) => ({ ...p, traits: p.traits.map((l, j) => (j === i ? { ...l, [k]: v } : l)) }));
  const addLine = () => setF((p) => ({ ...p, traits: [...p.traits, { count: 1, rarity: '', category: '', developable: '' }] }));
  const delLine = (i) => setF((p) => ({ ...p, traits: p.traits.filter((_, j) => j !== i) }));
  const set = (k) => (e) => setF((p) => ({ ...p, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));

  useEffect(() => {
    listArts(campaignId).then((l) => setArts(l.filter((a) => !a.status || a.status === 'APPROVED'))).catch(() => {});
  }, [campaignId]);

  async function submit(e) {
    e.preventDefault();
    if (f.artMode === 'SPECIFIC' && !f.artId) { setError('Escolha a arte.'); return; }
    setBusy(true);
    setError('');
    try {
      const sheet = await createRandomGmSheet(campaignId, {
        profile: f.profile, name: f.name.trim() || null, visible: f.visible, artMode: f.artMode,
        artId: f.artMode === 'SPECIFIC' ? f.artId : null, graduation: f.graduation || null,
        traits: f.traits.map((l) => ({
          count: Number(l.count) || 1, rarity: l.rarity || null, category: l.category || null,
          developable: l.developable === '' ? null : l.developable === 'yes',
        })),
      });
      onCreated(sheet);
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setBusy(false);
    }
  }

  const prof = PROFILES.find((p) => p.value === f.profile);
  return (
    <form onSubmit={submit} className="panel flex flex-col gap-4 p-4" noValidate>
      <h3 className="panel-title">Ficha aleatória</h3>
      <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
        <label className="label">Nome
          <div className="mt-1 flex gap-2">
            <input className="field w-full" value={f.name} maxLength={100} onChange={set('name')} />
            <button type="button" className="btn-ghost text-sm" title="Outro nome" aria-label="Outro nome" onClick={() => setF((p) => ({ ...p, name: randomName() }))}><Icon name="dice" size={18} /></button>
          </div>
        </label>
        <label className="label">Perfil
          <select className="field mt-1 w-full" value={f.profile} onChange={set('profile')}>
            {PROFILES.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
          </select>
        </label>
      </div>
      <p className="hint">{prof.hint}. Atributos = 1d12 por atributo + bônus do perfil; recursos pelas regras da ficha.</p>

      <div className="well">
        <h3 className="panel-title text-lg">Arte marcial</h3>
        <div className="mt-2 flex flex-wrap items-end gap-3">
          <label className="label">Arte
            <select className="field mt-1 w-full" value={f.artMode} onChange={set('artMode')}>
              <option value="NONE">Nenhuma</option>
              <option value="RANDOM">Aleatória</option>
              <option value="SPECIFIC">Específica</option>
            </select>
          </label>
          {f.artMode === 'SPECIFIC' && (
            <label className="label min-w-48 flex-1">Qual
              <select className="field mt-1 w-full" value={f.artId} onChange={set('artId')}>
                <option value="">— escolha —</option>
                {arts.map((a) => <option key={a.id} value={a.id}>{a.name} ({lab(ART_CATEGORIES, a.category)})</option>)}
              </select>
            </label>
          )}
          {f.artMode !== 'NONE' && (
            <label className="label">Graduação
              <select className="field mt-1 w-full" value={f.graduation} onChange={set('graduation')}>
                <option value="">Do perfil</option>
                {GRADS.filter((g) => g.value !== 'GENERIC').map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
              </select>
            </label>
          )}
        </div>
        {f.artMode !== 'NONE' && <p className="hint mt-2">Os atributos ganham +3 nos dois que a arte privilegia.</p>}
      </div>

      <div className="well">
        <h3 className="panel-title text-lg">Traços</h3>
        {f.traits.length === 0 && (
          <p className="hint mt-1">Sem linhas: {prof.label} recebe {TRAITS_BY_PROFILE[f.profile]} traço(s) sem filtro.</p>
        )}
        {f.traits.map((l, i) => (
          <div key={i} className="mt-3 grid gap-3 sm:grid-cols-[5rem_1fr_1fr_1fr_auto] sm:items-end">
            <label className="label">Qtd
              <input type="number" min={1} max={8} className="field w-full" value={l.count} onChange={(e) => setLine(i, 'count', e.target.value)} />
            </label>
            <label className="label">Raridade
              <select className="field w-full" value={l.rarity} onChange={(e) => setLine(i, 'rarity', e.target.value)}>
                <option value="">Qualquer</option>
                {Object.entries(RARITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            </label>
            <label className="label">Categoria
              <select className="field w-full" value={l.category} onChange={(e) => setLine(i, 'category', e.target.value)}>
                <option value="">Qualquer</option>
                {TRAIT_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
            </label>
            <label className="label">Desenvolvível
              <select className="field w-full" value={l.developable} onChange={(e) => setLine(i, 'developable', e.target.value)}>
                <option value="">Tanto faz</option>
                <option value="yes">Sim</option>
                <option value="no">Não</option>
              </select>
            </label>
            <button type="button" className="btn-ghost text-sm" aria-label="Remover linha" onClick={() => delLine(i)}>Remover</button>
          </div>
        ))}
        <button type="button" className="btn-secondary mt-3 text-sm" onClick={addLine}>
          <Icon name="plus" size={16} /> Linha de traços
        </button>
        <p className="hint mt-2">Cada linha sorteia sua quantidade com os próprios filtros; tudo é rolado ao gerar, sem repetir traço.</p>
      </div>

      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={f.visible} onChange={set('visible')} />
        Visível para os jogadores
      </label>
      {error && <p className="text-sm text-[var(--accent)]">{error}</p>}
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-secondary text-sm" onClick={onCancel}>Cancelar</button>
        <button type="submit" className="btn-primary text-sm" disabled={busy}>{busy ? 'Gerando…' : 'Gerar ficha'}</button>
      </div>
    </form>
  );
}
