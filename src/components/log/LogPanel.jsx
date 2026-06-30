import { useEffect, useState } from 'react';
import { listLogs, createLog } from '../../api/logs';
import { listTimeSkips } from '../../api/timeskips';
import { listBookings } from '../../api/bookings';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';

const INTERACTION_LABELS = {
  TREINO: 'Treino',
  TRABALHO: 'Trabalho',
  DESCANSO: 'Descanso',
  OUTRO: 'Outro',
};

function formatDate(iso) {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function bookingLabel(booking, npcs) {
  const npc = npcs.find((n) => n.id === booking.npcId);
  const npcName = npc?.name ?? `NPC ${booking.npcId}`;
  const type = INTERACTION_LABELS[booking.interactionType] ?? booking.interactionType;
  return `${npcName} · Dia ${booking.dayNumber} · Slot ${booking.slotNumber} · ${type}`;
}

export default function LogPanel({ campaignId, isMaster, npcs = [] }) {
  const { user } = useAuth();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Player booking state
  const [myBookings, setMyBookings] = useState([]);
  const [hasActiveTs, setHasActiveTs] = useState(false);
  const [bookingsLoading, setBookingsLoading] = useState(false);

  // Form state
  const [narrative, setNarrative] = useState('');
  const [selectedBookingId, setSelectedBookingId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const data = await listLogs(campaignId);
        if (active) setLogs(data);
      } catch (err) {
        if (active) setError(parseApiError(err).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [campaignId]);

  useEffect(() => {
    if (isMaster) return;
    let active = true;
    setBookingsLoading(true);
    (async () => {
      try {
        const timeskips = await listTimeSkips(campaignId);
        const activeTs = timeskips.find((t) => t.status === 'ACTIVE');
        if (!active) return;
        if (!activeTs) {
          setHasActiveTs(false);
          return;
        }
        setHasActiveTs(true);
        const all = await listBookings(campaignId, activeTs.id);
        if (active) setMyBookings(all.filter((b) => b.userId === user?.userId));
      } catch {
        // ignore — player just sees no form
      } finally {
        if (active) setBookingsLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [campaignId, isMaster, user?.userId]);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitError('');
    setSubmitting(true);
    try {
      const payload = { narrative };
      if (!isMaster && selectedBookingId) payload.bookingId = Number(selectedBookingId);
      const created = await createLog(campaignId, payload);
      setLogs((prev) => [created, ...prev]);
      setNarrative('');
      setSelectedBookingId('');
    } catch (err) {
      setSubmitError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit = narrative.trim() && (isMaster || selectedBookingId);

  function renderForm() {
    if (isMaster) {
      return (
        <form
          onSubmit={handleSubmit}
          className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
          noValidate
        >
          <label htmlFor="narrative" className="block text-sm font-medium text-slate-700">
            Novo log narrativo
          </label>
          <textarea
            id="narrative"
            rows={3}
            required
            value={narrative}
            onChange={(e) => setNarrative(e.target.value)}
            placeholder="Descreva o que aconteceu..."
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
          />
          {submitError && <p className="mt-1 text-sm text-red-600">{submitError}</p>}
          <div className="mt-3 flex justify-end">
            <button
              type="submit"
              disabled={submitting || !canSubmit}
              className="rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-60"
            >
              {submitting ? 'Publicando...' : 'Publicar log'}
            </button>
          </div>
        </form>
      );
    }

    if (bookingsLoading) {
      return <p className="text-sm text-slate-500">Carregando agendamentos...</p>;
    }

    if (!hasActiveTs) {
      return (
        <p className="text-sm text-slate-500">
          Não há TimeSkip ativo no momento para registrar um log.
        </p>
      );
    }

    if (myBookings.length === 0) {
      return (
        <p className="text-sm text-slate-500">
          Você não tem agendamentos no TimeSkip ativo para registrar um log.
        </p>
      );
    }

    return (
      <form
        onSubmit={handleSubmit}
        className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
        noValidate
      >
        <div>
          <label htmlFor="booking-select" className="block text-sm font-medium text-slate-700">
            Agendamento
          </label>
          <select
            id="booking-select"
            required
            value={selectedBookingId}
            onChange={(e) => setSelectedBookingId(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
          >
            <option value="">Selecione um agendamento...</option>
            {myBookings.map((b) => (
              <option key={b.id} value={b.id}>
                {bookingLabel(b, npcs)}
              </option>
            ))}
          </select>
        </div>
        <div className="mt-3">
          <label htmlFor="narrative" className="block text-sm font-medium text-slate-700">
            O que aconteceu?
          </label>
          <textarea
            id="narrative"
            rows={3}
            required
            value={narrative}
            onChange={(e) => setNarrative(e.target.value)}
            placeholder="Descreva o que aconteceu..."
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
          />
        </div>
        {submitError && <p className="mt-1 text-sm text-red-600">{submitError}</p>}
        <div className="mt-3 flex justify-end">
          <button
            type="submit"
            disabled={submitting || !canSubmit}
            className="rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-60"
          >
            {submitting ? 'Publicando...' : 'Publicar log'}
          </button>
        </div>
      </form>
    );
  }

  return (
    <div>
      {renderForm()}

      <div className="mt-4">
        {loading && <p className="text-sm text-slate-500">Carregando logs...</p>}
        {!loading && error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && logs.length === 0 && (
          <p className="text-sm text-slate-500">Nenhum log narrativo ainda.</p>
        )}
        {!loading && !error && logs.length > 0 && (
          <ul className="space-y-3">
            {logs.map((log) => (
              <li key={log.id} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm font-medium text-slate-900">{log.authorName}</span>
                  <span className="text-xs text-slate-400">{formatDate(log.createdAt)}</span>
                </div>
                {log.bookingId && (
                  <p className="mt-1 text-xs text-purple-700">
                    {log.npcName} · Dia {log.dayNumber} · Slot {log.slotNumber} ·{' '}
                    {INTERACTION_LABELS[log.interactionType] ?? log.interactionType}
                  </p>
                )}
                <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{log.narrative}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
