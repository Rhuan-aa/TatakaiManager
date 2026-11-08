import { useCallback, useEffect, useState } from 'react';
import * as api from '../../api/combat';
import { useCampaignTopic } from '../../hooks/useWebSocket';

/** Condições da ficha no encontro em andamento da campanha (vazio se não estiver em combate). */
export default function useSheetConditions(campaignId, sheetId) {
  const [entries, setEntries] = useState([]);
  const load = useCallback(async () => {
    if (!campaignId || !sheetId) return;
    try {
      const list = await api.listEncounters(campaignId);
      const cur = list.find((e) => e.status !== 'ENDED');
      const enc = cur && (await api.getEncounter(cur.id));
      const p = enc?.participants.find((x) => x.sheetId === sheetId);
      setEntries((p?.conditions ?? []).map((c) => ({ id: `${c.id}:${c.intensity ?? ''}`, anchorId: null, key: c.key,
        name: c.intensity ? `${c.name} ${c.intensity}` : c.name })));
    } catch { setEntries([]); }
  }, [campaignId, sheetId]);
  useEffect(() => { load(); }, [load]);
  useCampaignTopic(campaignId, 'combat', load);
  return entries;
}
