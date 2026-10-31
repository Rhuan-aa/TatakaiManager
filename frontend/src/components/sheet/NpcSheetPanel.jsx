import { useCallback, useEffect, useState } from 'react';
import {
  getNpcSheet, getCampaignNpcSheet, getNpcSheetVisibility, setNpcSheetVisibility,
} from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import CharacterSheetForm from './CharacterSheetForm';
import CharacterSheetView from './CharacterSheetView';
import MartialPanel from './MartialPanel';

const SECTIONS = [
  { section: 'ATTRIBUTES', label: 'Atributos' },
  { section: 'RESOURCES', label: 'Recursos' },
  { section: 'INJURIES', label: 'Ferimentos' },
  { section: 'INVENTORY', label: 'Inventário' },
  { section: 'TRAITS', label: 'Traços e clãs' },
];

// Ficha leve: os atributos crus (se houver) pré-preenchem o formulário de ficha completa.
const PREFILL = (a) => (a ? {
  str: a.STR || 10, dex: a.DEX || 10, con: a.CON || 10, intl: a.INT || 10, men: a.MEN || 10, tal: a.TAL || 10,
  cha: a.CHA || '',
} : { cha: '' });

function VisibilityToggles({ campaignId, npcId }) {
  const toast = useToast();
  const [vis, setVis] = useState(null);

  useEffect(() => {
    let active = true;
    getNpcSheetVisibility(campaignId, npcId)
      .then((list) => active && setVis(Object.fromEntries(list.map((x) => [x.section, x.visible]))))
      .catch((err) => active && toast(parseApiError(err).message, 'error'));
    return () => { active = false; };
  }, [campaignId, npcId, toast]);

  async function toggle(section, visible) {
    try {
      await setNpcSheetVisibility(campaignId, npcId, { section, visible });
      setVis((p) => ({ ...p, [section]: visible }));
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  return (
    <fieldset className="panel p-4">
      <legend className="panel-title">Visível para jogadores nesta campanha</legend>
      {!vis ? <div className="skeleton h-12 w-full" /> : (
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {SECTIONS.map((s) => (
            <label key={s.section} className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={!!vis[s.section]} onChange={(e) => toggle(s.section, e.target.checked)} />
              {s.label}
            </label>
          ))}
        </div>
      )}
    </fieldset>
  );
}

export default function NpcSheetPanel({ campaignId, npc, isMaster, onChanged }) {
  const { user } = useAuth();
  const toast = useToast();
  const isOwner = npc.ownerId === user?.userId;
  const [sheet, setSheet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    (isOwner ? getNpcSheet(npc.id) : getCampaignNpcSheet(campaignId, npc.id))
      .then((s) => active && setSheet(s))
      .catch((err) => {
        if (!active) return;
        setSheet(null);
        if (err?.response?.status !== 404) toast(parseApiError(err).message, 'error');
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [campaignId, npc.id, isOwner, tick, toast]);

  const sheetId = sheet?.id;
  const onSheetEvent = useCallback((msg) => {
    if (sheetId && msg?.sheetId === sheetId) setTick((t) => t + 1);
  }, [sheetId]);
  useCampaignTopic(campaignId, 'sheets', onSheetEvent);

  function changed(s) {
    setSheet(s);
    onChanged?.(s);
  }

  if (loading) return <div className="skeleton h-24 w-full" />;

  if (creating) {
    return (
      <div className="panel p-4">
        <h3 className="panel-title mb-3">Criar ficha</h3>
        <CharacterSheetForm
          npcId={npc.id}
          initialValues={{ name: npc.name, ...PREFILL(sheet?.attributes) }}
          onSaved={(s) => { setCreating(false); changed(s); }}
          onCancel={() => setCreating(false)}
        />
      </div>
    );
  }

  if (sheet && !sheet.combatReady) {
    return (
      <div className="flex flex-col gap-4">
        <div className="panel p-4">
          <p className="caption">Ficha sem atributos e recursos</p>
          <p className="mt-2 text-sm">Este NPC ainda não pode entrar em combate nem rolar. Complete atributos e recursos para liberar.</p>
          {isOwner && <button type="button" className="btn-primary mt-3" onClick={() => setCreating(true)}>Completar ficha</button>}
        </div>
        {sheet.traits?.length > 0 && (
          <div className="panel p-3">
            <p className="caption">Traços</p>
            <ul className="mt-2 space-y-1.5 text-sm">
              {sheet.traits.map((t) => (
                <li key={t.id} className="border-l-4 border-[var(--accent)] pl-2"><strong>{t.name}</strong>{t.description ? ` — ${t.description}` : ''}</li>
              ))}
            </ul>
          </div>
        )}
        <MartialPanel sheetId={sheet.id} campaignId={campaignId} isMaster={isMaster} isOwner={isOwner} refreshKey={tick} />
        {isOwner && isMaster && <VisibilityToggles campaignId={campaignId} npcId={npc.id} />}
      </div>
    );
  }

  if (!sheet) {
    return isOwner ? (
      <button type="button" className="btn-primary" onClick={() => setCreating(true)}>Criar ficha</button>
    ) : (
      <p className="caption inline-block">Nenhuma informação da ficha disponível.</p>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <CharacterSheetView sheet={sheet} isMaster={isMaster} campaignId={campaignId} onChange={changed} />
      {isOwner && isMaster && <VisibilityToggles campaignId={campaignId} npcId={npc.id} />}
    </div>
  );
}
