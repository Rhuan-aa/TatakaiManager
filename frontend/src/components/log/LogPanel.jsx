import { useEffect, useState } from 'react';
import { listLogs, createLog, updateLog, deleteLog } from '../../api/logs';
import { listTimeSkips } from '../../api/timeskips';
import { listBookings } from '../../api/bookings';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';

function LogSkeleton() {
  return (
    <div className="rounded-xl border border-zinc-800 bg-zinc-950/40 p-4">
      <div className="flex items-center gap-3">
        <div className="skeleton h-8 w-8 rounded-full" />
        <div className="flex-1">
          <div className="skeleton h-4 w-32" />
          <div className="skeleton mt-1.5 h-3 w-48" />
        </div>
      </div>
      <div className="skeleton mt-3 h-3 w-full" />
      <div className="skeleton mt-1.5 h-3 w-4/5" />
    </div>
  );
}

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
  return `${npcName} · Dia ${booking.dayNumber} · Slot ${booking.slotNumber} · ${booking.interactionName} (${booking.idlePointCost} pts de ócio)`;
}

const textareaClass = 'field';

const selectClass = 'field';

export default function LogPanel({ campaignId, isMaster, npcs = [] }) {
  const { user } = useAuth();
  const toast = useToast();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [myBookings, setMyBookings] = useState([]);
  const [hasActiveTs, setHasActiveTs] = useState(false);
  const [bookingsLoading, setBookingsLoading] = useState(false);

  const [narrative, setNarrative] = useState('');
  const [selectedBookingId, setSelectedBookingId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  const [editingLogId, setEditingLogId] = useState(null);
  const [editNarrative, setEditNarrative] = useState('');
  const [editError, setEditError] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);

  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');

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
      toast('Log publicado.');
    } catch (err) {
      setSubmitError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit = narrative.trim() && (isMaster || selectedBookingId);

  function startEdit(log) {
    setConfirmDeleteId(null);
    setDeleteError('');
    setEditingLogId(log.id);
    setEditNarrative(log.narrative);
    setEditError('');
  }

  function cancelEdit() {
    setEditingLogId(null);
    setEditNarrative('');
    setEditError('');
  }

  async function handleSaveEdit(logId) {
    if (!editNarrative.trim()) return;
    setEditError('');
    setSavingEdit(true);
    try {
      const updated = await updateLog(campaignId, logId, { narrative: editNarrative });
      setLogs((prev) => prev.map((l) => (l.id === logId ? updated : l)));
      setEditingLogId(null);
      toast('Log editado.');
    } catch (err) {
      setEditError(parseApiError(err).message);
    } finally {
      setSavingEdit(false);
    }
  }

  async function handleDelete(logId) {
    setDeleteError('');
    setDeleting(true);
    try {
      await deleteLog(campaignId, logId);
      setLogs((prev) => prev.filter((l) => l.id !== logId));
      setConfirmDeleteId(null);
      toast('Log excluído.');
    } catch (err) {
      setDeleteError(parseApiError(err).message);
    } finally {
      setDeleting(false);
    }
  }

  function renderForm() {
    if (isMaster) {
      return (
        <form
          onSubmit={handleSubmit}
          className="rounded-xl border border-zinc-800 bg-zinc-950/40 p-4"
          noValidate
        >
          <label htmlFor="narrative" className="block text-sm font-medium text-zinc-400">
            Novo log narrativo
          </label>
          <textarea
            id="narrative"
            rows={3}
            required
            value={narrative}
            onChange={(e) => setNarrative(e.target.value)}
            placeholder="Descreva o que aconteceu..."
            className={textareaClass}
          />
          {submitError && <p className="mt-1 text-sm text-red-400">{submitError}</p>}
          <div className="mt-3 flex justify-end">
            <button
              type="submit"
              disabled={submitting || !canSubmit}
              className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
            >
              {submitting ? 'Publicando...' : 'Publicar log'}
            </button>
          </div>
        </form>
      );
    }

    if (bookingsLoading) {
      return <p className="text-sm text-zinc-500">Carregando agendamentos...</p>;
    }

    if (!hasActiveTs) {
      return (
        <p className="text-sm text-zinc-500">
          Não há TimeSkip ativo no momento para registrar um log.
        </p>
      );
    }

    if (myBookings.length === 0) {
      return (
        <p className="text-sm text-zinc-500">
          Você não tem agendamentos no TimeSkip ativo para registrar um log.
        </p>
      );
    }

    return (
      <form
        onSubmit={handleSubmit}
        className="rounded-xl border border-zinc-800 bg-zinc-950/40 p-4"
        noValidate
      >
        <div>
          <label htmlFor="booking-select" className="block text-sm font-medium text-zinc-400">
            Agendamento
          </label>
          <select
            id="booking-select"
            required
            value={selectedBookingId}
            onChange={(e) => setSelectedBookingId(e.target.value)}
            className={selectClass}
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
          <label htmlFor="narrative" className="block text-sm font-medium text-zinc-400">
            O que aconteceu?
          </label>
          <textarea
            id="narrative"
            rows={3}
            required
            value={narrative}
            onChange={(e) => setNarrative(e.target.value)}
            placeholder="Descreva o que aconteceu..."
            className={textareaClass}
          />
        </div>
        {submitError && <p className="mt-1 text-sm text-red-400">{submitError}</p>}
        <div className="mt-3 flex justify-end">
          <button
            type="submit"
            disabled={submitting || !canSubmit}
            className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
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

      <div className="mt-4 space-y-3">
        {loading &&
          Array.from({ length: 2 }).map((_, i) => <LogSkeleton key={i} />)}
        {!loading && error && <p className="text-sm text-red-400">{error}</p>}
        {!loading && !error && logs.length === 0 && (
          <EmptyState
            icon="📜"
            title="Nenhum log narrativo ainda"
            description="Os registros da campanha aparecerão aqui conforme forem publicados."
          />
        )}
        {!loading &&
          !error &&
          logs.map((log) => {
            // Logs vinculados a agendamento = ação de jogador; sem vínculo = narração
            // livre (Mestre), que ganha um accent lateral vermelho para se destacar.
            const isNarration = !log.bookingId;
            return (
              <div
                key={log.id}
                className={`rounded-xl border border-zinc-800 p-4 ${
                  isNarration
                    ? 'bg-gradient-to-r from-red-950/15 to-zinc-950/40 shadow-[inset_3px_0_0_0_rgba(239,68,68,0.55)]'
                    : 'bg-zinc-950/40'
                }`}
              >
                <div className="flex items-center gap-3">
                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-zinc-800 text-xs font-bold text-zinc-300 ring-1 ring-white/5">
                    {log.authorName?.charAt(0)?.toUpperCase() ?? '?'}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-3">
                      <span className="truncate text-sm font-semibold text-zinc-50">
                        {log.authorName}
                      </span>
                      <span className="shrink-0 text-xs text-zinc-600">
                        {formatDate(log.createdAt)}
                        {log.updatedAt && ' · editado'}
                      </span>
                    </div>
                    {log.bookingId && (
                      <span className="mt-1 inline-flex flex-wrap items-center gap-x-1 rounded-full border border-red-900/50 bg-red-950/40 px-2 py-0.5 text-[11px] font-medium text-red-300">
                        {log.npcName} · Dia {log.dayNumber} · Slot {log.slotNumber} ·{' '}
                        {log.interactionName}
                        {log.idlePointCost != null && ` · ${log.idlePointCost} pts`}
                      </span>
                    )}
                  </div>
                  {isMaster && editingLogId !== log.id && (
                    <div className="flex shrink-0 items-center gap-2">
                      <button
                        type="button"
                        onClick={() => startEdit(log)}
                        className="text-xs font-medium text-zinc-500 hover:text-zinc-300"
                      >
                        Editar
                      </button>
                      {confirmDeleteId === log.id ? (
                        <span className="flex items-center gap-2">
                          <span className="text-xs text-red-400">Excluir?</span>
                          <button
                            type="button"
                            onClick={() => handleDelete(log.id)}
                            disabled={deleting}
                            className="rounded-md bg-red-600 px-2 py-1 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                          >
                            {deleting ? 'Excluindo...' : 'Confirmar'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmDeleteId(null)}
                            className="text-xs text-zinc-500 hover:text-zinc-300"
                          >
                            Cancelar
                          </button>
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => {
                            setConfirmDeleteId(log.id);
                            setDeleteError('');
                          }}
                          className="text-xs font-medium text-red-400 hover:text-red-300"
                        >
                          Excluir
                        </button>
                      )}
                    </div>
                  )}
                </div>
                {confirmDeleteId === log.id && deleteError && (
                  <p className="mt-1.5 text-xs text-red-400">{deleteError}</p>
                )}
                {editingLogId === log.id ? (
                  <div className="mt-2.5">
                    <textarea
                      rows={3}
                      value={editNarrative}
                      onChange={(e) => setEditNarrative(e.target.value)}
                      className={textareaClass}
                    />
                    {editError && <p className="mt-1 text-xs text-red-400">{editError}</p>}
                    <div className="mt-2 flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={cancelEdit}
                        className="rounded-md px-3 py-1.5 text-sm text-zinc-500 hover:text-zinc-300"
                      >
                        Cancelar
                      </button>
                      <button
                        type="button"
                        onClick={() => handleSaveEdit(log.id)}
                        disabled={savingEdit || !editNarrative.trim()}
                        className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                      >
                        {savingEdit ? 'Salvando...' : 'Salvar'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="mt-2.5 whitespace-pre-wrap text-sm leading-relaxed text-zinc-300">
                    {log.narrative}
                  </p>
                )}
              </div>
            );
          })}
      </div>
    </div>
  );
}
