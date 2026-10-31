import { useEffect, useState } from 'react';
import { createRandomGmSheet } from '../../api/characterSheets';
import { listArts } from '../../api/catalog';
import { parseApiError } from '../../api/parseApiError';
import { ART_CATEGORIES, GRADS, lab } from '../catalog/artLabels';
import { randomName } from '../npc/randomNpc';
import { RARITY, TRAIT_CATEGORIES } from '../ui/rarity';

const PROFILES = [
  { value: 'NOVICE', label: 'Novato', hint: 'atributos baixos, EL 0, 1 traço' },
  { value: 'EXPERIENCED', label: 'Experiente', hint: 'EL 8, 2 traços' },
  { value: 'MASTER', label: 'Mestre', hint: 'EL 20, 3 traços, arte Intermediária' },
  { value: 'RIVAL', label: 'Rival', hint: 'EL 32, 3 traços, arte Intermediária' },
  { value: 'BOSS', label: 'Chefe', hint: 'EL 58, 4 traços, arte de Mestre' },
];

/** Menu do gerador de ficha aleatória (§51.9): perfil, arte marcial e traços. */
export default function RandomSheetForm({ campaignId, onCreated, onCancel }) {
  const [f, setF] = useState({
    profile: 'NOVICE', name: randomName(), visible: false, artMode: 'RANDOM', artId: '', graduation: '',
    traitCount: '', traitRarity: '', traitCategory: '', developableOnly: '',
  });
  const [arts, setArts] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
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
        traitCount: f.traitCount === '' ? null : Number(f.traitCount), traitRarity: f.traitRarity || null,
        traitCategory: f.traitCategory || null,
        developableOnly: f.developableOnly === '' ? null : f.developableOnly === 'yes',
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
        <label className="text-xs uppercase">Nome
          <div className="mt-1 flex gap-2">
            <input className="field w-full" value={f.name} maxLength={100} onChange={set('name')} />
            <button type="button" className="btn-ghost text-sm" title="Outro nome" onClick={() => setF((p) => ({ ...p, name: randomName() }))}>🎲</button>
          </div>
        </label>
        <label className="text-xs uppercase">Perfil
          <select className="field mt-1 w-full" value={f.profile} onChange={set('profile')}>
            {PROFILES.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
          </select>
        </label>
      </div>
      <p className="caption">{prof.hint}. Atributos = 1d12 por atributo + bônus do perfil; recursos pelas regras da ficha.</p>

      <fieldset className="bubble p-3">
        <legend className="caption">Arte marcial</legend>
        <div className="mt-2 flex flex-wrap items-end gap-3">
          <label className="text-xs uppercase">Arte
            <select className="field mt-1 w-full" value={f.artMode} onChange={set('artMode')}>
              <option value="NONE">Nenhuma</option>
              <option value="RANDOM">Aleatória</option>
              <option value="SPECIFIC">Específica</option>
            </select>
          </label>
          {f.artMode === 'SPECIFIC' && (
            <label className="min-w-48 flex-1 text-xs uppercase">Qual
              <select className="field mt-1 w-full" value={f.artId} onChange={set('artId')}>
                <option value="">— escolha —</option>
                {arts.map((a) => <option key={a.id} value={a.id}>{a.name} ({lab(ART_CATEGORIES, a.category)})</option>)}
              </select>
            </label>
          )}
          {f.artMode !== 'NONE' && (
            <label className="text-xs uppercase">Graduação
              <select className="field mt-1 w-full" value={f.graduation} onChange={set('graduation')}>
                <option value="">Do perfil</option>
                {GRADS.filter((g) => g.value !== 'GENERIC').map((g) => <option key={g.value} value={g.value}>{g.label}</option>)}
              </select>
            </label>
          )}
        </div>
        {f.artMode !== 'NONE' && <p className="caption mt-2">Os atributos ganham +3 nos dois que a arte privilegia.</p>}
      </fieldset>

      <fieldset className="bubble p-3">
        <legend className="caption">Traços</legend>
        <div className="mt-2 grid gap-3 sm:grid-cols-4">
          <label className="text-xs uppercase">Quantidade
            <input type="number" min={0} max={8} className="field mt-1 w-full" placeholder="do perfil" value={f.traitCount} onChange={set('traitCount')} />
          </label>
          <label className="text-xs uppercase">Raridade
            <select className="field mt-1 w-full" value={f.traitRarity} onChange={set('traitRarity')}>
              <option value="">Qualquer</option>
              {Object.entries(RARITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
            </select>
          </label>
          <label className="text-xs uppercase">Categoria
            <select className="field mt-1 w-full" value={f.traitCategory} onChange={set('traitCategory')}>
              <option value="">Qualquer</option>
              {TRAIT_CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </label>
          <label className="text-xs uppercase">Desenvolvível
            <select className="field mt-1 w-full" value={f.developableOnly} onChange={set('developableOnly')}>
              <option value="">Tanto faz</option>
              <option value="yes">Só desenvolvíveis</option>
              <option value="no">Só não desenvolvíveis</option>
            </select>
          </label>
        </div>
      </fieldset>

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
