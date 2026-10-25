import { useState } from 'react';
import { createGmSheet } from '../../api/characterSheets';
import { listCampaignNpcs, setNpcVisibility } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import CharacterSheetForm from './CharacterSheetForm';
import NpcSheetPanel from './NpcSheetPanel';

/**
 * Fichas do Mestre (§26.11): NPCs da campanha que têm ficha. O Mestre cria uma na hora (vira NPC só com ficha,
 * fora da Agenda) e escolhe se os jogadores a veem; jogadores só recebem as visíveis.
 */
export default function GmSheets({ campaignId, isMaster, npcs, setNpcs }) {
  const { user } = useAuth();
  const toast = useToast();
  const [creating, setCreating] = useState(false);
  const [visible, setVisible] = useState(false);
  const [openId, setOpenId] = useState(null);
  const list = npcs.filter((n) => n.sheetId);

  const refresh = async () => setNpcs?.(await listCampaignNpcs(campaignId));

  async function toggle(n) {
    try {
      await setNpcVisibility(campaignId, n.id, !n.visible);
      setNpcs?.((prev) => prev.map((x) => (x.id === n.id ? { ...x, visible: !n.visible } : x)));
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  const open = list.find((n) => n.id === openId);
  if (open) {
    return (
      <section className="panel flex flex-col gap-3 p-4">
        <button type="button" className="btn-ghost self-start" onClick={() => setOpenId(null)}>← Fichas do Mestre</button>
        {/* só o dono associa NPC à campanha, então NPC listado ao Mestre é dele */}
        <NpcSheetPanel campaignId={campaignId} isMaster={isMaster}
          npc={{ ...open, ownerId: isMaster ? user?.userId : null }} onChanged={() => refresh()} />
      </section>
    );
  }

  if (!isMaster && list.length === 0) return null;
  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="panel-title">Fichas do Mestre</h2>
        {isMaster && !creating && (
          <button type="button" className="btn-primary" onClick={() => setCreating(true)}>Ficha rápida</button>
        )}
      </div>
      {creating && (
        <div className="panel p-4">
          <h3 className="panel-title mb-1">Nova ficha rápida</h3>
          <p className="caption mb-3">Sem construção nem aprovação. Vira um NPC só com ficha, fora da Agenda.</p>
          <CharacterSheetForm
            campaignId={campaignId}
            create={(payload) => createGmSheet(campaignId, payload, visible)}
            onSaved={async (s) => { setCreating(false); setVisible(false); await refresh(); setOpenId(s.npcId); }}
            onCancel={() => setCreating(false)}>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={visible} onChange={(e) => setVisible(e.target.checked)} />
              Visível para os jogadores (ficha inteira; dá para ajustar por seção depois)
            </label>
          </CharacterSheetForm>
        </div>
      )}
      {list.length === 0 && !creating && (
        <p className="caption">Nenhuma ficha do Mestre. NPCs com ficha também aparecem aqui.</p>
      )}
      {list.length > 0 && (
        <div className="page-gutter grid sm:grid-cols-2 lg:grid-cols-3">
          {list.map((n) => (
            <div key={n.id} className="panel flex flex-col gap-2 p-4">
              <button type="button" className="panel-title text-left hover:underline" onClick={() => setOpenId(n.id)}>{n.name}</button>
              <div className="flex flex-wrap items-center gap-2">
                <span className="tag">NPC</span>
                {isMaster && (
                  <button type="button" className={n.visible ? 'tag tag-accent' : 'tag'} aria-pressed={n.visible}
                    title="Alternar visibilidade para os jogadores" onClick={() => toggle(n)}>
                    {n.visible ? 'Visível' : 'Oculta'}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
