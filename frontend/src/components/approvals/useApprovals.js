import { useCallback, useEffect, useState } from 'react';
import { listApprovals } from '../../api/approvals';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

/** Pendências de aprovação do Mestre; recarrega ao montar e a cada evento de campanha/ficha. */
export function useApprovals(campaignId, enabled) {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(!!enabled);
  const [tick, setTick] = useState(0);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  // useCampaignTopic reconecta se o callback mudar: precisa ser estável. Sem `enabled` o canal fica sem handler útil.
  const onMessage = useCallback(() => { if (enabled) setTick((t) => t + 1); }, [enabled]);
  useCampaignTopic(enabled ? campaignId : null, 'campaign', onMessage);
  useCampaignTopic(enabled ? campaignId : null, 'sheets', onMessage);

  useEffect(() => {
    if (!enabled) return undefined;
    let active = true;
    (async () => {
      try {
        const data = await listApprovals(campaignId);
        if (active) setItems(data);
      } catch (err) {
        if (active) toast(parseApiError(err).message, 'error');
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => { active = false; };
    // toast é estável o bastante; não deve refazer o GET
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [campaignId, enabled, tick]);

  return { items, loading, reload };
}
