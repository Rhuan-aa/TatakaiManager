import { useEffect, useMemo, useState } from 'react';
import { listLogs, createLog, updateLog, deleteLog } from '../../api/logs';
import { listTimeSkips } from '../../api/timeskips';
import { listBookings } from '../../api/bookings';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';

function LogSkeleton() {
  return (
    <div className="panel p-4">
      <div className="flex items-center gap-3">
        <div className="skeleton h-8 w-8" />
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

function formatDay(iso) {
  return new Date(iso).toLocaleDateString('pt-BR', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

function formatTime(iso) {
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

const EMPTY_FILTERS = { text: '', author: '', npc: '', type: '', day: '' };

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

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const setFilter = (key) => (e) => setFilters((f) => ({ ...f, [key]: e.target.value }));

  const authors = useMemo(
    () => [...new Set(logs.map((l) => l.authorName).filter(Boolean))].sort(),
    [logs],
  );
  const npcNames = useMemo(
    () => [...new Set(logs.map((l) => l.npcName).filter(Boolean))].sort(),
    [logs],
  );
  const days = useMemo(
    () => [...new Set(logs.map((l) => l.dayNumber).filter((d) => d != null))].sort((a, b) => a - b),
    [logs],
  );
  const filtersActive = Object.values(filters).some(Boolean);

  const groups = useMemo(() => {
    const q = filters.text.trim().toLowerCase();
    const out = [];
    for (const log of logs) {
      if (q && !`${log.narrative} ${log.authorName ?? ''} ${log.npcName ?? ''}`.toLowerCase().includes(q)) continue;
      if (filters.author && log.authorName !== filters.author) continue;
      if (filters.npc && log.npcName !== filters.npc) continue;
      if (filters.type === 'livre' && log.bookingId) continue;
      if (filters.type === 'agendamento' && !log.bookingId) continue;
      if (filters.day && String(log.dayNumber) !== filters.day) continue;
      const label = formatDay(log.createdAt);
      const last = out[out.length - 1];
      if (last && last[0] === label) last[1].push(log);
      else out.push([label, [log]]);
    }
    return out;
  }, [logs, filters]);

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
      if (!isMaster && selectedBookingId) payload.bookingId = selectedBookingId;
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
          className="panel p-4"
          noValidate
        >
          <label htmlFor="narrative" className="block text-xs font-bold uppercase tracking-wide">
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
          {submitError && <p className="mt-1 text-sm font-bold text-[color:var(--accent)]">{submitError}</p>}
          <div className="mt-3 flex justify-end">
            <button
              type="submit"
              disabled={submitting || !canSubmit}
              className="btn-primary px-3 py-2 text-sm disabled:opacity-50"
            >
              {submitting ? 'Publicando...' : 'Publicar log'}
            </button>
          </div>
        </form>
      );
    }

    if (bookingsLoading) {
      return <p className="text-sm opacity-70">Carregando agendamentos...</p>;
    }

    if (!hasActiveTs) {
      return (
        <p className="text-sm opacity-70">
          Não há TimeSkip ativo no momento para registrar um log.
        </p>
      );
    }

    if (myBookings.length === 0) {
      return (
        <p className="text-sm opacity-70">
          Você não tem agendamentos no TimeSkip ativo para registrar um log.
        </p>
      );
    }

    return (
      <form
        onSubmit={handleSubmit}
        className="panel p-4"
        noValidate
      >
        <div>
          <label htmlFor="booking-select" className="block text-xs font-bold uppercase tracking-wide">
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
          <label htmlFor="narrative" className="block text-xs font-bold uppercase tracking-wide">
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
        {submitError && <p className="mt-1 text-sm font-bold text-[color:var(--accent)]">{submitError}</p>}
        <div className="mt-3 flex justify-end">
          <button
            type="submit"
            disabled={submitting || !canSubmit}
            className="btn-primary px-3 py-2 text-sm disabled:opacity-50"
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

      {!loading && !error && logs.length > 0 && (
        <div className="panel mt-4 grid gap-2 p-3 sm:grid-cols-2 lg:grid-cols-5">
          <input
            type="search"
            aria-label="Buscar nos logs"
            placeholder="Buscar texto..."
            value={filters.text}
            onChange={setFilter('text')}
            className="field sm:col-span-2 lg:col-span-1"
          />
          <select aria-label="Autor" value={filters.author} onChange={setFilter('author')} className="field">
            <option value="">Todos os autores</option>
            {authors.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
          <select aria-label="NPC" value={filters.npc} onChange={setFilter('npc')} className="field">
            <option value="">Todos os NPCs</option>
            {npcNames.map((n) => <option key={n} value={n}>{n}</option>)}
          </select>
          <select aria-label="Tipo" value={filters.type} onChange={setFilter('type')} className="field">
            <option value="">Todos os tipos</option>
            <option value="livre">Log livre do Mestre</option>
            <option value="agendamento">Log de agendamento</option>
          </select>
          <select aria-label="Dia" value={filters.day} onChange={setFilter('day')} className="field">
            <option value="">Todos os dias</option>
            {days.map((d) => <option key={d} value={String(d)}>Dia {d}</option>)}
          </select>
          {filtersActive && (
            <button
              type="button"
              onClick={() => setFilters(EMPTY_FILTERS)}
              className="btn-ghost text-xs sm:col-span-2 lg:col-span-5"
            >
              Limpar filtros
            </button>
          )}
        </div>
      )}

      <div className="mt-4 space-y-3">
        {loading &&
          Array.from({ length: 2 }).map((_, i) => <LogSkeleton key={i} />)}
        {!loading && error && <p className="text-sm font-bold text-[color:var(--accent)]">{error}</p>}
        {!loading && !error && logs.length === 0 && (
          <EmptyState
            icon="📜"
            title="Nenhum log narrativo ainda"
            description="Os registros da campanha aparecerão aqui conforme forem publicados."
          />
        )}
        {!loading && !error && logs.length > 0 && groups.length === 0 && (
          <p className="text-sm opacity-70">Nenhum log corresponde aos filtros.</p>
        )}
        {!loading &&
          !error &&
          groups.map(([dayLabel, items]) => (
          <section key={dayLabel} className="space-y-5">
            <h3 className="border-b-2 border-[color:var(--line)] pb-1 text-xs font-bold uppercase tracking-wide capitalize">
              {dayLabel}
            </h3>
          {items.map((log) => {
            // Logs vinculados a agendamento = ação de jogador; sem vínculo = narração
            // livre (Mestre), que ganha um accent lateral vermelho para se destacar.
            const isNarration = !log.bookingId;
            return (
              <div key={log.id}>
                <div className="flex items-center gap-3">
                  <span className="grid h-8 w-8 shrink-0 place-items-center border-2 border-[color:var(--line)] bg-[color:var(--highlight)] text-xs font-bold text-[color:var(--ink)]">
                    {log.authorName?.charAt(0)?.toUpperCase() ?? '?'}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-3">
                      <span className="min-w-0 truncate text-xs">
                        <b className="uppercase tracking-wide">{log.authorName}</b>
                        {log.bookingId &&
                          ` · ${log.npcName} · Dia ${log.dayNumber} · Slot ${log.slotNumber} · ${log.interactionName}${
                            log.idlePointCost != null ? ` (${log.idlePointCost} pts)` : ''
                          }`}
                      </span>
                      <span className="shrink-0 text-[11px] uppercase opacity-70">
                        {formatTime(log.createdAt)}
                        {log.updatedAt && ' · editado'}
                      </span>
                    </div>
                  </div>
                  {isMaster && editingLogId !== log.id && (
                    <div className="flex shrink-0 items-center gap-2">
                      <button type="button" onClick={() => startEdit(log)} className="btn-ghost text-xs">
                        Editar
                      </button>
                      {confirmDeleteId === log.id ? (
                        <span className="flex items-center gap-2">
                          <span className="text-xs font-bold text-[color:var(--accent)]">Excluir?</span>
                          <button
                            type="button"
                            onClick={() => handleDelete(log.id)}
                            disabled={deleting}
                            className="btn-primary px-2 py-1 text-xs disabled:opacity-50"
                          >
                            {deleting ? 'Excluindo...' : 'Confirmar'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmDeleteId(null)}
                            className="btn-ghost text-xs"
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
                          className="btn-ghost text-xs text-[color:var(--accent)]"
                        >
                          Excluir
                        </button>
                      )}
                    </div>
                  )}
                </div>
                {confirmDeleteId === log.id && deleteError && (
                  <p className="mt-1.5 text-xs font-bold text-[color:var(--accent)]">{deleteError}</p>
                )}
                {editingLogId === log.id ? (
                  <div className="panel mt-2.5 p-3">
                    <textarea
                      rows={3}
                      value={editNarrative}
                      onChange={(e) => setEditNarrative(e.target.value)}
                      className={textareaClass}
                    />
                    {editError && (
                      <p className="mt-1 text-xs font-bold text-[color:var(--accent)]">{editError}</p>
                    )}
                    <div className="mt-2 flex justify-end gap-2">
                      <button type="button" onClick={cancelEdit} className="btn-ghost px-3 py-1.5 text-sm">
                        Cancelar
                      </button>
                      <button
                        type="button"
                        onClick={() => handleSaveEdit(log.id)}
                        disabled={savingEdit || !editNarrative.trim()}
                        className="btn-primary px-3 py-1.5 text-sm disabled:opacity-50"
                      >
                        {savingEdit ? 'Salvando...' : 'Salvar'}
                      </button>
                    </div>
                  </div>
                ) : (
                  <p
                    className={`${
                      isNarration ? 'caption' : 'bubble'
                    } mt-3 max-w-prose whitespace-pre-wrap text-sm leading-7`}
                  >
                    {log.narrative}
                  </p>
                )}
              </div>
            );
          })}
          </section>
          ))}
      </div>
    </div>
  );
}
