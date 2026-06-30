import { useCallback, useEffect, useState } from 'react';
import { listBookings, createBooking, cancelBooking } from '../api/bookings';
import { parseApiError } from '../api/parseApiError';
import { useAuth } from '../contexts/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';

const SLOTS = [1, 2, 3, 4];

const INTERACTION_LABELS = {
  TREINO: 'Treino',
  TRABALHO: 'Trabalho',
  DESCANSO: 'Descanso',
  OUTRO: 'Outro',
};

const slotKey = (day, npcId, slot) => `${day}:${npcId}:${slot}`;

export default function SlotGrid({ campaignId, timeSkip, npcs }) {
  const { user } = useAuth();
  const [bookings, setBookings] = useState({});
  const [day, setDay] = useState(timeSkip.currentDay);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [activeCell, setActiveCell] = useState(null);

  const isActive = timeSkip.status === 'ACTIVE';
  const isPastDay = day < timeSkip.currentDay;
  const canBook = isActive && !isPastDay;

  // Reposiciona no dia atual sempre que troca de TimeSkip.
  useEffect(() => {
    setDay(timeSkip.currentDay);
  }, [timeSkip.id, timeSkip.currentDay]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    (async () => {
      try {
        const list = await listBookings(campaignId, timeSkip.id);
        if (!active) return;
        const keyed = {};
        for (const b of list) keyed[slotKey(b.dayNumber, b.npcId, b.slotNumber)] = b;
        setBookings(keyed);
      } catch (err) {
        if (active) setError(parseApiError(err).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [campaignId, timeSkip.id]);

  const handleSlotUpdate = useCallback(
    (msg) => {
      if (msg.timeSkipId !== timeSkip.id) return;
      const key = slotKey(msg.dayNumber, msg.npcId, msg.slotNumber);
      setBookings((prev) => {
        if (msg.event === 'CANCELLED') {
          if (!prev[key]) return prev;
          const next = { ...prev };
          delete next[key];
          return next;
        }
        // BOOKED: não sobrescreve um agendamento já conhecido (preserva o id do próprio usuário).
        if (prev[key]) return prev;
        return {
          ...prev,
          [key]: {
            npcId: msg.npcId,
            dayNumber: msg.dayNumber,
            slotNumber: msg.slotNumber,
            userId: msg.userId,
            userName: msg.userName,
            interactionType: msg.interactionType,
          },
        };
      });
    },
    [timeSkip.id]
  );

  useWebSocket(campaignId, handleSlotUpdate);

  async function handleBook(npc, slot, interactionType) {
    setActionError('');
    setActiveCell(null);
    try {
      const booking = await createBooking(campaignId, timeSkip.id, {
        npcId: npc.id,
        dayNumber: day,
        slotNumber: slot,
        interactionType,
      });
      setBookings((prev) => ({
        ...prev,
        [slotKey(booking.dayNumber, booking.npcId, booking.slotNumber)]: booking,
      }));
    } catch (err) {
      setActionError(parseApiError(err).message);
    }
  }

  async function handleCancel(booking) {
    setActionError('');
    try {
      await cancelBooking(booking.id);
      setBookings((prev) => {
        const next = { ...prev };
        delete next[slotKey(booking.dayNumber, booking.npcId, booking.slotNumber)];
        return next;
      });
    } catch (err) {
      setActionError(parseApiError(err).message);
    }
  }

  if (loading) return <p className="text-sm text-slate-500">Carregando agenda...</p>;
  if (error) return <p className="text-sm text-red-600">{error}</p>;

  return (
    <div>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setDay((d) => Math.max(1, d - 1))}
            disabled={day <= 1}
            className="rounded-md border border-slate-300 px-2 py-1 text-sm disabled:opacity-40"
          >
            ←
          </button>
          <span className="text-sm font-medium text-slate-700">
            Dia {day} de {timeSkip.totalDays}
            {day === timeSkip.currentDay && (
              <span className="ml-2 rounded-full bg-purple-100 px-2 py-0.5 text-xs text-purple-700">
                dia atual
              </span>
            )}
          </span>
          <button
            type="button"
            onClick={() => setDay((d) => Math.min(timeSkip.totalDays, d + 1))}
            disabled={day >= timeSkip.totalDays}
            className="rounded-md border border-slate-300 px-2 py-1 text-sm disabled:opacity-40"
          >
            →
          </button>
        </div>
      </div>

      {!isActive && (
        <p className="mt-2 text-sm text-amber-600">Este TimeSkip está encerrado (somente leitura).</p>
      )}
      {isActive && isPastDay && (
        <p className="mt-2 text-sm text-slate-500">Dia já passado — agenda em modo leitura.</p>
      )}
      {actionError && <p className="mt-2 text-sm text-red-600">{actionError}</p>}

      {npcs.length === 0 ? (
        <p className="mt-3 text-sm text-slate-500">Nenhum NPC disponível para agendar.</p>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th className="border-b border-slate-200 p-2 text-left font-medium text-slate-500">
                  NPC
                </th>
                {SLOTS.map((slot) => (
                  <th
                    key={slot}
                    className="border-b border-slate-200 p-2 text-center font-medium text-slate-500"
                  >
                    Slot {slot}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {npcs.map((npc) => (
                <tr key={npc.id}>
                  <td className="border-b border-slate-100 p-2 font-medium text-slate-900">
                    {npc.name}
                  </td>
                  {SLOTS.map((slot) => {
                    const key = slotKey(day, npc.id, slot);
                    const booking = bookings[key];
                    const mine = booking && booking.userId === user?.userId;
                    const cellId = `${npc.id}:${slot}`;
                    const types = npc.interactionTypes ?? [];

                    return (
                      <td
                        key={slot}
                        className="border-b border-slate-100 p-2 text-center align-top"
                      >
                        {booking ? (
                          <div className="rounded-md bg-purple-50 px-2 py-1 text-left">
                            <p className="text-xs font-medium text-purple-700">
                              {INTERACTION_LABELS[booking.interactionType] ?? booking.interactionType}
                            </p>
                            <p className="text-xs text-slate-500">{booking.userName}</p>
                            {mine && canBook && (
                              <button
                                type="button"
                                onClick={() => handleCancel(booking)}
                                className="mt-1 text-xs text-red-600 hover:underline"
                              >
                                Cancelar
                              </button>
                            )}
                          </div>
                        ) : canBook ? (
                          activeCell === cellId ? (
                            <div className="flex flex-col gap-1">
                              {types.map((t) => (
                                <button
                                  key={t}
                                  type="button"
                                  onClick={() => handleBook(npc, slot, t)}
                                  className="rounded-md bg-purple-600 px-2 py-1 text-xs text-white hover:bg-purple-700"
                                >
                                  {INTERACTION_LABELS[t] ?? t}
                                </button>
                              ))}
                              <button
                                type="button"
                                onClick={() => setActiveCell(null)}
                                className="text-xs text-slate-400 hover:underline"
                              >
                                cancelar
                              </button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setActiveCell(cellId)}
                              className="rounded-md border border-dashed border-slate-300 px-2 py-1 text-xs text-slate-400 hover:border-purple-400 hover:text-purple-600"
                            >
                              + Agendar
                            </button>
                          )
                        ) : (
                          <span className="text-xs text-slate-300">—</span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
