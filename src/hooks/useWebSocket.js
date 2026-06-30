import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from '../config';

export function useWebSocket(campaignId, onSlotUpdate) {
  const clientRef = useRef(null);

  useEffect(() => {
    if (!campaignId) return;

    const token = localStorage.getItem('token');

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      onConnect: () => {
        client.subscribe(`/topic/campaign/${campaignId}/slots`, (message) => {
          onSlotUpdate(JSON.parse(message.body));
        });
      },
      reconnectDelay: 3000,
    });

    client.activate();
    clientRef.current = client;

    return () => client.deactivate();
  }, [campaignId, onSlotUpdate]);

  return clientRef;
}
