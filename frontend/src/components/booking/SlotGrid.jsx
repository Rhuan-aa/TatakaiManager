import { useCallback, useEffect, useState } from 'react';
import { listBookings, createBooking, cancelBooking } from '../../api/bookings';
import { listTimeSkipActivities } from '../../api/timeSkipActivities';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { useWebSocket } from '../../hooks/useWebSocket';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';

const SLOTS = [1, 2, 3, 4];

const SOLO_TYPES = [
  { value: 'TREINO', label: 'Treino' },
  { value: 'ESTUDO', label: 'Estudo' },
  { value: 'ACAO_GERAL', label: 'Ação geral' },
];

const soloLabel = (type) => SOLO_TYPES.find((t) => t.value === type)?.label ?? type;

// Rótulo de um agendamento solo: atividade customizada do TimeSkip (se houver) ou
// um dos tipos fixos (Treino/Estudo/Ação geral).
const bookingSoloLabel = (b) => b.activityName ?? soloLabel(b.soloActivityType);

const FIXED_OPTION_PREFIX = 'fixed:';
const CUSTOM_OPTION_PREFIX = 'custom:';

function SlotGridSkeleton() {
  return (
    <div className="mt-4 space-y-3">
      {Array.from({ length: 3 }).map((_, r) => (
        <div key={r} className="flex items-center gap-3">
          <div className="skeleton h-7 w-7 shrink-0 rounded-md" />
          <div className="skeleton h-4 w-24 shrink-0" />
          <div className="ml-auto flex gap-2">
            {SLOTS.map((s) => (
              <div key={s} className="skeleton h-9 w-24 rounded-lg" />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

const slotKey = (day, npcId, slot) => `${day}:${npcId}:${slot}`;

export default function SlotGrid({ campaignId, timeSkip, npcs, activitiesVersion }) {
  const { user } = useAuth();
  const toast = useToast();
  const [bookings, setBookings] = useState({});
  const [soloBookings, setSoloBookings] = useState({});
  const [customActivities, setCustomActivities] = useState([]);
  const [day, setDay] = useState(timeSkip.currentDay);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [activeCell, setActiveCell] = useState(null);
  const [soloForm, setSoloForm] = useState({ option: `${FIXED_OPTION_PREFIX}TREINO`, description: '' });

  const isActive = timeSkip.status === 'ACTIVE';
  const isPastDay = day < timeSkip.currentDay;
  const canBook = isActive && !isPastDay;

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
        const solo = {};
        for (const b of list) {
          if (b.npcId) keyed[slotKey(b.dayNumber, b.npcId, b.slotNumber)] = b;
          else solo[b.id] = b;
        }
        setBookings(keyed);
        setSoloBookings(solo);
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

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const list = await listTimeSkipActivities(campaignId, timeSkip.id);
        if (active) setCustomActivities(list);
      } catch {
        // Catálogo de atividades customizadas é um complemento; falha aqui não
        // deve travar a grade de agendamento (os tipos fixos continuam disponíveis).
      }
    })();
    return () => {
      active = false;
    };
  }, [campaignId, timeSkip.id, activitiesVersion]);

  const handleSlotUpdate = useCallback(
    (msg) => {
      if (msg.timeSkipId !== timeSkip.id) return;

      if (!msg.npcId) {
        // Atividade solo — não há conflito de slot entre NPCs, então indexa por bookingId
        setSoloBookings((prev) => {
          if (msg.event === 'CANCELLED') {
            if (!prev[msg.bookingId]) return prev;
            const next = { ...prev };
            delete next[msg.bookingId];
            return next;
          }
          if (prev[msg.bookingId]) return prev;
          return {
            ...prev,
            [msg.bookingId]: {
              id: msg.bookingId,
              dayNumber: msg.dayNumber,
              slotNumber: msg.slotNumber,
              userId: msg.userId,
              userName: msg.userName,
              soloActivityType: msg.soloActivityType,
              activityId: msg.activityId,
              activityName: msg.activityName,
              description: msg.description,
            },
          };
        });
        return;
      }

      const key = slotKey(msg.dayNumber, msg.npcId, msg.slotNumber);
      setBookings((prev) => {
        if (msg.event === 'CANCELLED') {
          if (!prev[key]) return prev;
          const next = { ...prev };
          delete next[key];
          return next;
        }
        if (prev[key]) return prev;
        return {
          ...prev,
          [key]: {
            npcId: msg.npcId,
            dayNumber: msg.dayNumber,
            slotNumber: msg.slotNumber,
            userId: msg.userId,
            userName: msg.userName,
            interactionName: msg.interactionName,
            idlePointCost: msg.idlePointCost,
          },
        };
      });
    },
    [timeSkip.id]
  );

  useWebSocket(campaignId, handleSlotUpdate);

  async function handleBook(npc, slot, interactionName) {
    setActionError('');
    setActiveCell(null);
    try {
      const booking = await createBooking(campaignId, timeSkip.id, {
        npcId: npc.id,
        dayNumber: day,
        slotNumber: slot,
        interactionName,
      });
      setBookings((prev) => ({
        ...prev,
        [slotKey(booking.dayNumber, booking.npcId, booking.slotNumber)]: booking,
      }));
      toast(`Agendado: ${npc.name} · ${interactionName}.`);
    } catch (err) {
      setActionError(parseApiError(err).message);
    }
  }

  function resetSoloForm() {
    setSoloForm({ option: `${FIXED_OPTION_PREFIX}TREINO`, description: '' });
  }

  async function handleBookSolo(slot) {
    setActionError('');
    const isCustom = soloForm.option.startsWith(CUSTOM_OPTION_PREFIX);

    if (!isCustom && !soloForm.description.trim()) {
      setActionError('A descrição da atividade solo é obrigatória');
      return;
    }

    try {
      const booking = isCustom
        ? await createBooking(campaignId, timeSkip.id, {
            dayNumber: day,
            slotNumber: slot,
            activityId: soloForm.option.slice(CUSTOM_OPTION_PREFIX.length),
          })
        : await createBooking(campaignId, timeSkip.id, {
            dayNumber: day,
            slotNumber: slot,
            soloActivityType: soloForm.option.slice(FIXED_OPTION_PREFIX.length),
            description: soloForm.description.trim(),
          });
      setSoloBookings((prev) => ({ ...prev, [booking.id]: booking }));
      setActiveCell(null);
      resetSoloForm();
      toast(`Ação solo agendada: ${bookingSoloLabel(booking)}.`);
    } catch (err) {
      setActionError(parseApiError(err).message);
    }
  }

  async function handleCancel(booking) {
    setActionError('');
    try {
      await cancelBooking(booking.id);
      if (booking.npcId) {
        setBookings((prev) => {
          const next = { ...prev };
          delete next[slotKey(booking.dayNumber, booking.npcId, booking.slotNumber)];
          return next;
        });
      } else {
        setSoloBookings((prev) => {
          const next = { ...prev };
          delete next[booking.id];
          return next;
        });
      }
      toast('Agendamento cancelado.');
    } catch (err) {
      setActionError(parseApiError(err).message);
    }
  }

  if (loading) return <SlotGridSkeleton />;
  if (error) return <p className="text-sm text-red-400">{error}</p>;

  // Conteúdo de uma célula de slot de NPC — reutilizado na tabela (desktop) e nos
  // cards empilhados (mobile).
  function renderSlot(npc, slot) {
    const key = slotKey(day, npc.id, slot);
    const booking = bookings[key];
    const mine = booking && booking.userId === user?.userId;
    const cellId = `${npc.id}:${slot}`;
    const interactions = npc.interactions ?? [];

    if (booking) {
      return (
        <div
          className={`rounded-lg border-l-2 px-2.5 py-1.5 text-left shadow-sm shadow-black/20 ${
            mine ? 'border-red-500 bg-red-950/25' : 'border-zinc-600 bg-zinc-800/80'
          }`}
        >
          <p className="text-xs font-semibold text-zinc-100">{booking.interactionName}</p>
          {booking.idlePointCost != null && (
            <p className="text-[11px] font-medium text-red-400">
              {booking.idlePointCost} pts de ócio
            </p>
          )}
          <p className="mt-0.5 text-[11px] text-zinc-500">{booking.userName}</p>
          {mine && canBook && (
            <button
              type="button"
              onClick={() => handleCancel(booking)}
              className="mt-1 text-[11px] font-medium text-red-400 hover:underline"
            >
              Cancelar
            </button>
          )}
        </div>
      );
    }

    if (canBook) {
      return activeCell === cellId ? (
        <div className="flex flex-col gap-1">
          {interactions.map((it, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => handleBook(npc, slot, it.name)}
              title={it.description || undefined}
              className="rounded-md bg-gradient-to-b from-red-500 to-red-600 px-2 py-1 text-xs font-medium text-white shadow-sm shadow-red-950/40 transition hover:to-red-700"
            >
              {it.type ? `[${it.type}] ` : ''}
              {it.name} · {it.idlePointCost} pts de ócio
            </button>
          ))}
          <button
            type="button"
            onClick={() => setActiveCell(null)}
            className="text-xs text-zinc-500 hover:text-zinc-300"
          >
            cancelar
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setActiveCell(cellId)}
          className="w-full rounded-lg border border-dashed border-zinc-700/70 px-2 py-2 text-xs text-zinc-600 transition hover:border-red-600/70 hover:bg-red-950/10 hover:text-red-400"
        >
          + Agendar
        </button>
      );
    }

    return <span className="text-xs text-zinc-700">—</span>;
  }

  // Célula da linha "Ação solo": ao contrário dos NPCs, vários jogadores podem
  // ocupar o mesmo dia+slot solo (cada um treina/estuda por conta própria).
  function renderSoloSlot(slot) {
    const entries = Object.values(soloBookings).filter(
      (b) => b.dayNumber === day && b.slotNumber === slot
    );
    const mine = entries.find((b) => b.userId === user?.userId);
    const others = entries.filter((b) => b.userId !== user?.userId);
    const cellId = `solo:${slot}`;

    return (
      <div className="flex flex-col gap-1">
        {others.map((o) => (
          <div
            key={o.id}
            className="rounded-md border-l-2 border-zinc-600 bg-zinc-800/80 px-2.5 py-1.5 text-left"
          >
            <p className="text-xs font-semibold text-zinc-100">{bookingSoloLabel(o)}</p>
            <p className="mt-0.5 text-[11px] text-zinc-500">{o.userName}</p>
          </div>
        ))}

        {mine ? (
          <div className="rounded-lg border-l-2 border-red-500 bg-red-950/25 px-2.5 py-1.5 text-left shadow-sm shadow-black/20">
            <p className="text-xs font-semibold text-zinc-100">{bookingSoloLabel(mine)}</p>
            <p className="mt-0.5 line-clamp-2 text-[11px] text-zinc-500">{mine.description}</p>
            {mine.idlePointCost != null && (
              <p className="mt-0.5 text-[11px] font-medium text-red-400">
                {mine.idlePointCost} pts de ócio
              </p>
            )}
            {canBook && (
              <button
                type="button"
                onClick={() => handleCancel(mine)}
                className="mt-1 text-[11px] font-medium text-red-400 hover:underline"
              >
                Cancelar
              </button>
            )}
          </div>
        ) : canBook ? (
          activeCell === cellId ? (
            <div className="flex flex-col gap-1.5 rounded-lg border border-zinc-700/70 bg-zinc-900/60 p-2">
              <select
                value={soloForm.option}
                onChange={(e) => setSoloForm((f) => ({ ...f, option: e.target.value }))}
                className="field text-xs"
              >
                {SOLO_TYPES.map((t) => (
                  <option key={t.value} value={`${FIXED_OPTION_PREFIX}${t.value}`}>
                    {t.label}
                  </option>
                ))}
                {customActivities.map((a) => (
                  <option key={a.id} value={`${CUSTOM_OPTION_PREFIX}${a.id}`}>
                    {a.name} · {a.idlePointCost} pts de ócio
                  </option>
                ))}
              </select>
              {soloForm.option.startsWith(CUSTOM_OPTION_PREFIX) ? (
                <p className="rounded-md border border-zinc-800 bg-zinc-950/50 p-2 text-[11px] text-zinc-400">
                  {customActivities.find((a) => a.id === soloForm.option.slice(CUSTOM_OPTION_PREFIX.length))
                    ?.description}
                </p>
              ) : (
                <textarea
                  value={soloForm.description}
                  onChange={(e) => setSoloForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="O que seu personagem faz nesse período?"
                  rows={2}
                  className="field text-xs"
                />
              )}
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => handleBookSolo(slot)}
                  className="rounded-md bg-gradient-to-b from-red-500 to-red-600 px-2 py-1 text-xs font-medium text-white shadow-sm shadow-red-950/40 transition hover:to-red-700"
                >
                  Confirmar
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setActiveCell(null);
                    resetSoloForm();
                  }}
                  className="text-xs text-zinc-500 hover:text-zinc-300"
                >
                  cancelar
                </button>
              </div>
            </div>
          ) : (
            <button
              type="button"
              onClick={() => setActiveCell(cellId)}
              className="w-full rounded-lg border border-dashed border-zinc-700/70 px-2 py-2 text-xs text-zinc-600 transition hover:border-red-600/70 hover:bg-red-950/10 hover:text-red-400"
            >
              + Ação solo
            </button>
          )
        ) : (
          others.length === 0 && <span className="text-xs text-zinc-700">—</span>
        )}
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setDay((d) => Math.max(1, d - 1))}
            disabled={day <= 1}
            className="rounded-md border border-zinc-700 px-2 py-1 text-sm text-zinc-400 disabled:opacity-30 hover:bg-zinc-700"
          >
            ←
          </button>
          <span className="text-sm font-medium text-zinc-300">
            Dia {day} de {timeSkip.totalDays}
            {day === timeSkip.currentDay && (
              <span className="ml-2 rounded-full bg-red-950 border border-red-900 px-2 py-0.5 text-xs text-red-400">
                hoje
              </span>
            )}
          </span>
          <button
            type="button"
            onClick={() => setDay((d) => Math.min(timeSkip.totalDays, d + 1))}
            disabled={day >= timeSkip.totalDays}
            className="rounded-md border border-zinc-700 px-2 py-1 text-sm text-zinc-400 disabled:opacity-30 hover:bg-zinc-700"
          >
            →
          </button>
        </div>
      </div>

      {!isActive && (
        <p className="mt-2 text-sm text-zinc-500">TimeSkip encerrado — somente leitura.</p>
      )}
      {isActive && isPastDay && (
        <p className="mt-2 text-sm text-zinc-500">Dia já passado — somente leitura.</p>
      )}
      {actionError && <p className="mt-2 text-sm text-red-400">{actionError}</p>}

      {/* Desktop: tabela (NPCs + linha de ação solo) */}
      <div className="mt-4 hidden overflow-x-auto md:block">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr>
              <th className="border-b border-zinc-700 p-2 text-left text-xs font-medium text-zinc-500">
                NPC
              </th>
              {SLOTS.map((slot) => (
                <th
                  key={slot}
                  className="border-b border-zinc-700 p-2 text-center text-xs font-medium text-zinc-500"
                >
                  Slot {slot}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr className="border-b border-zinc-800 bg-white/[0.01]">
              <td className="p-2">
                <div className="flex items-center gap-2">
                  <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-zinc-800 text-xs font-bold text-red-400 ring-1 ring-white/5">
                    🧘
                  </span>
                  <span className="text-sm font-medium text-zinc-200">Ação solo</span>
                </div>
              </td>
              {SLOTS.map((slot) => (
                <td key={slot} className="p-2 text-center align-top">
                  {renderSoloSlot(slot)}
                </td>
              ))}
            </tr>
            {npcs.map((npc) => (
              <tr
                key={npc.id}
                className="border-b border-zinc-800 transition-colors last:border-0 hover:bg-white/[0.015]"
              >
                <td className="p-2">
                  <div className="flex items-center gap-2">
                    <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-zinc-800 text-xs font-bold text-zinc-400 ring-1 ring-white/5">
                      {npc.name?.charAt(0)?.toUpperCase() ?? '?'}
                    </span>
                    <span className="text-sm font-medium text-zinc-200">{npc.name}</span>
                  </div>
                </td>
                {SLOTS.map((slot) => (
                  <td key={slot} className="p-2 text-center align-top">
                    {renderSlot(npc, slot)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {npcs.length === 0 && (
          <p className="mt-3 text-center text-xs text-zinc-600">
            Nenhum NPC associado à campanha ainda — só a ação solo está disponível.
          </p>
        )}
      </div>

      {/* Mobile: cards empilhados (evita rolagem horizontal) */}
      <div className="mt-4 space-y-3 md:hidden">
        <div className="rounded-xl border border-zinc-800 bg-zinc-950/40 p-3">
          <div className="mb-2.5 flex items-center gap-2">
            <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-zinc-800 text-xs font-bold text-red-400 ring-1 ring-white/5">
              🧘
            </span>
            <span className="text-sm font-semibold text-zinc-100">Ação solo</span>
          </div>
          <div className="grid grid-cols-2 gap-2">
            {SLOTS.map((slot) => (
              <div key={slot} className="rounded-lg border border-zinc-800/70 bg-zinc-900/40 p-2">
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-zinc-500">
                  Slot {slot}
                </p>
                {renderSoloSlot(slot)}
              </div>
            ))}
          </div>
        </div>

        {npcs.length === 0 ? (
          <EmptyState
            icon="🧑‍🤝‍🧑"
            title="Nenhum NPC para agendar"
            description="Adicione NPCs à campanha para agendar interações — a ação solo acima já está disponível."
          />
        ) : (
          npcs.map((npc) => (
            <div key={npc.id} className="rounded-xl border border-zinc-800 bg-zinc-950/40 p-3">
              <div className="mb-2.5 flex items-center gap-2">
                <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-zinc-800 text-xs font-bold text-zinc-400 ring-1 ring-white/5">
                  {npc.name?.charAt(0)?.toUpperCase() ?? '?'}
                </span>
                <span className="text-sm font-semibold text-zinc-100">{npc.name}</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                {SLOTS.map((slot) => (
                  <div
                    key={slot}
                    className="rounded-lg border border-zinc-800/70 bg-zinc-900/40 p-2"
                  >
                    <p className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-zinc-500">
                      Slot {slot}
                    </p>
                    {renderSlot(npc, slot)}
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
