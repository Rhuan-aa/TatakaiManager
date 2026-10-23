import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from '../config';

/**
 * Assina /topic/campaign/{campaignId}/{channel}. O callback deve ser estável
 * (useCallback), senão o socket reconecta a cada render.
 */
export function useCampaignTopic(campaignId, channel, onMessage) {
  const clientRef = useRef(null);

  useEffect(() => {
    if (!campaignId) return;

    const token = localStorage.getItem('token');

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        client.subscribe(`/topic/campaign/${campaignId}/${channel}`, (message) => {
          onMessage(JSON.parse(message.body));
        });
      },
      reconnectDelay: 3000,
    });

    client.activate();
    clientRef.current = client;

    return () => client.deactivate();
  }, [campaignId, channel, onMessage]);

  return clientRef;
}

export function useWebSocket(campaignId, onSlotUpdate) {
  return useCampaignTopic(campaignId, 'slots', onSlotUpdate);
}
