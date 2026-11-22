import { useCallback, useEffect, useMemo, useState } from 'react';
import { listLogs, createLog, updateLog, deleteLog, reviewLog } from '../../api/logs';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import { listTimeSkips } from '../../api/timeskips';
import { listBookings } from '../../api/bookings';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';
import Icon from '../ui/Icon';

function LogSkeleton() {
  return (
    <div className="feed-item" aria-hidden="true">
      <span className="feed-node skeleton" />
      <div className="feed-card p-4">
        <div className="skeleton h-4 w-40" />
        <div className="skeleton mt-3 h-3 w-full" />
        <div className="skeleton mt-1.5 h-3 w-4/5" />
      </div>
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

// Logs gravados antes da correção do TextSanitizer vêm com entidades (&#34;): mostra como texto.
const ENTITIES = { '&#34;': '"', '&#39;': "'", '&#43;': '+', '&#61;': '=', '&#64;': '@', '&#96;': '`' };
const plain = (t) => (t ?? '').replace(/&#(34|39|43|61|64|96);/g, (m) => ENTITIES[m]).replaceAll('&amp;', '&');

// valor da opção "sem agendamento" no select do jogador (vira um pedido de aprovação)
const NO_BOOKING = '__none__';

const EMPTY_FILTERS = { text: '', author: '', npc: '', type: '', day: '' };

function bookingLabel(booking, npcs) {
  const npc = npcs.find((n) => n.id === booking.npcId);
  const npcName = npc?.name ?? `NPC ${booking.npcId}`;
  return `${npcName} · Dia ${booking.dayNumber} · Slot ${booking.slotNumber} · ${booking.interactionName} (${booking.idlePointCost} pts de ócio)`;
}

export default function LogPanel({ campaignId, isMaster, npcs = [], focusLogId = null }) {
  const { user } = useAuth();
  const toast = useToast();
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [myBookings, setMyBookings] = useState([]);
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

  const [reviewingId, setReviewingId] = useState(null);
  const [reviewNote, setReviewNote] = useState('');
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewError, setReviewError] = useState('');
  const [highlightId, setHighlightId] = useState(null);
  const [tick, setTick] = useState(0);

  // outro cliente criou/aprovou/recusou/apagou um log: recarrega a lista
  const onCampaign = useCallback((msg) => { if (msg?.type === 'LOGS') setTick((t) => t + 1); }, []);
  useCampaignTopic(campaignId, 'campaign', onCampaign);

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
      if (q && !`${plain(log.narrative)} ${log.authorName ?? ''} ${log.npcName ?? ''}`.toLowerCase().includes(q)) continue;
      if (filters.author && log.authorName !== filters.author) continue;
      if (filters.npc && log.npcName !== filters.npc) continue;
      if (filters.type === 'livre' && log.bookingId) continue;
      if (filters.type === 'agendamento' && !log.bookingId) continue;
      if (filters.type === 'pendentes' && log.status !== 'PENDING') continue;
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
  }, [campaignId, tick]);

  // rola até o log pedido (vindo da aba Aprovações) e o destaca por 2,5 s
  useEffect(() => {
    if (!focusLogId || loading) return undefined;
    const el = document.getElementById(`log-${focusLogId}`);
    if (!el) return undefined;
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setHighlightId(focusLogId);
    const t = setTimeout(() => setHighlightId(null), 2500);
    return () => clearTimeout(t);
  }, [focusLogId, loading, logs.length]);

  useEffect(() => {
    if (isMaster) return;
    let active = true;
    setBookingsLoading(true);
    (async () => {
      try {
        const timeskips = await listTimeSkips(campaignId);
        const activeTs = timeskips.find((t) => t.status === 'ACTIVE');
        if (!active) return;
        if (!activeTs) return;
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
      const isRequest = !isMaster && selectedBookingId === NO_BOOKING;
      if (!isMaster && selectedBookingId && !isRequest) payload.bookingId = selectedBookingId;
      const created = await createLog(campaignId, payload);
      setLogs((prev) => [created, ...prev]);
      setNarrative('');
      setSelectedBookingId('');
      toast(isRequest ? 'Pedido enviado ao Mestre.' : 'Log publicado.');
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
    setEditNarrative(plain(log.narrative));
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

  async function handleReview(logId, approved) {
    setReviewError('');
    setReviewBusy(true);
    try {
      const updated = await reviewLog(campaignId, logId, { approved, note: reviewNote.trim() || null });
      setLogs((prev) => prev.map((l) => (l.id === logId ? updated : l)));
      setReviewingId(null);
      setReviewNote('');
      toast(approved ? 'Log aprovado.' : 'Log recusado.');
    } catch (err) {
      setReviewError(parseApiError(err).message);
    } finally {
      setReviewBusy(false);
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

  const initial = (name) => name?.charAt(0)?.toUpperCase() ?? '?';
  const err = (msg) => msg && <p className="mt-1 text-xs font-bold text-[color:var(--accent)]">{msg}</p>;

  function renderForm() {
    if (!isMaster && bookingsLoading) return <div className="skeleton h-40 w-full" />;
    const isRequest = !isMaster && selectedBookingId === NO_BOOKING;
    return (
      <form onSubmit={handleSubmit} className="panel p-4" noValidate>
        <div className="feed-compose">
          <span className="feed-node-static" aria-hidden="true">{initial(user?.name)}</span>
          <div className="flex min-w-0 flex-col gap-3">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h3 className="panel-title text-xl">{isMaster ? 'Narrar a cena' : 'Registrar o que aconteceu'}</h3>
              <span className="text-fine">{isMaster ? 'Publica direto no registro da campanha.' : 'Ligado a um agendamento seu, ou como pedido ao Mestre.'}</span>
            </div>
            {!isMaster && (
              <select id="booking-select" aria-label="Agendamento" required value={selectedBookingId}
                onChange={(e) => setSelectedBookingId(e.target.value)} className="field m-0">
                <option value="">Escolha o agendamento…</option>
                <option value={NO_BOOKING}>Sem agendamento — pedir aprovação ao Mestre</option>
                {myBookings.map((b) => <option key={b.id} value={b.id}>{bookingLabel(b, npcs)}</option>)}
              </select>
            )}
            <textarea id="narrative" aria-label="Texto do log" required maxLength={5000} value={narrative}
              onChange={(e) => setNarrative(e.target.value)}
              placeholder={isMaster ? 'A chuva cai sobre o porto quando…' : 'Descreva o que aconteceu…'} className="field" />
            {err(submitError)}
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="feed-count">{narrative.length}/5000</span>
              <button type="submit" disabled={submitting || !canSubmit} className="btn-primary">
                {submitting ? 'Enviando…' : isRequest ? 'Pedir aprovação' : 'Publicar log'}
              </button>
            </div>
          </div>
        </div>
      </form>
    );
  }

  function kindOf(log) {
    if (log.status === 'PENDING') return { label: 'Pedido', rail: 'var(--highlight)' };
    if (log.status === 'REJECTED') return { label: 'Recusado', rail: 'var(--line)' };
    if (!log.bookingId) return { label: 'Narração', rail: 'var(--accent)', fg: '#fff' };
    return { label: 'Agendamento', rail: 'var(--highlight)' };
  }

  function renderLog(log) {
    const pending = log.status === 'PENDING';
    const rejected = log.status === 'REJECTED';
    const mine = log.authorId === user?.userId;
    const k = kindOf(log);
    const editing = editingLogId === log.id;
    return (
      <article key={log.id} id={`log-${log.id}`} className="feed-item">
        <span className="feed-node" aria-hidden="true"
          style={!log.bookingId && !pending ? { '--node-bg': 'var(--accent)', '--node-fg': '#fff' } : undefined}>
          {initial(log.authorName)}
        </span>
        <div className={`feed-card${highlightId === String(log.id) ? ' is-focus' : ''}`} style={{ '--rail': k.rail, '--kind-fg': k.fg }}>
          <header className="feed-head">
            <span className="feed-author">{log.authorName}</span>
            <span className="feed-kind">{k.label}</span>
            <time className="feed-time" dateTime={log.createdAt}>
              <Icon name="clock" size={12} />{formatTime(log.createdAt)}{log.updatedAt && ' · editado'}
            </time>
          </header>
          {log.bookingId && (
            <div className="feed-meta">
              {log.npcName && <span>{log.npcName}</span>}
              {log.dayNumber != null && <span>Dia {log.dayNumber}</span>}
              {log.slotNumber != null && <span>Slot {log.slotNumber}</span>}
              {log.interactionName && <span>{log.interactionName}</span>}
              {log.idlePointCost != null && <span>{log.idlePointCost} pts de ócio</span>}
            </div>
          )}
          {(pending || rejected) && (
            <p className="feed-status">{pending ? 'Aguardando o Mestre' : `Recusado${log.reviewNote ? `: ${log.reviewNote}` : ''}`}</p>
          )}
          {editing ? (
            <div className="p-3">
              <textarea rows={4} value={editNarrative} onChange={(e) => setEditNarrative(e.target.value)} className="field" aria-label="Editar log" />
              {err(editError)}
              <div className="mt-2 flex justify-end gap-2">
                <button type="button" onClick={cancelEdit} className="btn-ghost">Cancelar</button>
                <button type="button" onClick={() => handleSaveEdit(log.id)} disabled={savingEdit || !editNarrative.trim()} className="btn-primary">
                  {savingEdit ? 'Salvando…' : 'Salvar'}
                </button>
              </div>
            </div>
          ) : (
            <p className={`feed-body${!log.bookingId ? ' feed-body--narration' : ''}`}>{plain(log.narrative)}</p>
          )}
          {reviewingId === log.id && (
            <div className="well mx-3 mb-3 p-3">
              <label htmlFor={`review-note-${log.id}`} className="label">Motivo da recusa (opcional)</label>
              <input id={`review-note-${log.id}`} type="text" value={reviewNote} onChange={(e) => setReviewNote(e.target.value)} className="field" />
              {err(reviewError)}
              <div className="mt-2 flex justify-end gap-2">
                <button type="button" onClick={() => setReviewingId(null)} className="btn-ghost">Cancelar</button>
                <button type="button" onClick={() => handleReview(log.id, false)} disabled={reviewBusy} className="btn-primary">
                  {reviewBusy ? 'Recusando…' : 'Confirmar recusa'}
                </button>
              </div>
            </div>
          )}
          {!editing && (
            <footer className="feed-actions">
              {pending && isMaster && reviewingId !== log.id && (
                <>
                  <button type="button" onClick={() => handleReview(log.id, true)} disabled={reviewBusy} className="btn-primary px-3 py-1 text-xs">Aprovar</button>
                  <button type="button" onClick={() => { setReviewingId(log.id); setReviewNote(''); setReviewError(''); }}
                    className="btn-ghost text-xs text-[color:var(--accent)]">Recusar</button>
                </>
              )}
              {!isMaster && mine && log.status && log.status !== 'PUBLISHED' && (
                <button type="button" onClick={() => handleDelete(log.id)} disabled={deleting} className="btn-ghost text-xs text-[color:var(--accent)]">
                  Cancelar pedido
                </button>
              )}
              {isMaster && (confirmDeleteId === log.id ? (
                <span className="flex items-center gap-2">
                  <span className="text-xs font-bold text-[color:var(--accent)]">Excluir este log?</span>
                  <button type="button" onClick={() => handleDelete(log.id)} disabled={deleting} className="btn-primary px-3 py-1 text-xs">
                    {deleting ? 'Excluindo…' : 'Confirmar'}
                  </button>
                  <button type="button" onClick={() => setConfirmDeleteId(null)} className="btn-ghost text-xs">Cancelar</button>
                </span>
              ) : (
                <>
                  <button type="button" onClick={() => startEdit(log)} className="feed-icon-btn" aria-label="Editar log" title="Editar">
                    <Icon name="pencil" size={16} />
                  </button>
                  <button type="button" onClick={() => { setConfirmDeleteId(log.id); setDeleteError(''); }}
                    className="feed-icon-btn is-danger" aria-label="Excluir log" title="Excluir">
                    <Icon name="trash" size={16} />
                  </button>
                </>
              ))}
            </footer>
          )}
          {(confirmDeleteId === log.id || (!isMaster && mine)) && deleteError && <div className="px-3 pb-3">{err(deleteError)}</div>}
        </div>
      </article>
    );
  }

  const shown = groups.reduce((n, [, items]) => n + items.length, 0);

  return (
    <div className="flex flex-col gap-4">
      {renderForm()}

      {!loading && !error && logs.length > 0 && (
        <div className="feed-toolbar">
          <label className="feed-search">
            <span className="feed-search-icon"><Icon name="search" size={16} /></span>
            <input type="search" aria-label="Buscar nos logs" placeholder="Buscar no registro…" value={filters.text}
              onChange={setFilter('text')} className="field" />
          </label>
          <select aria-label="Autor" value={filters.author} onChange={setFilter('author')} className="field">
            <option value="">Autor</option>
            {authors.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
          {npcNames.length > 0 && (
            <select aria-label="NPC" value={filters.npc} onChange={setFilter('npc')} className="field">
              <option value="">NPC</option>
              {npcNames.map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          )}
          <select aria-label="Tipo" value={filters.type} onChange={setFilter('type')} className="field">
            <option value="">Tipo</option>
            <option value="livre">Narração</option>
            <option value="agendamento">Agendamento</option>
            <option value="pendentes">Pendentes</option>
          </select>
          {days.length > 0 && (
            <select aria-label="Dia" value={filters.day} onChange={setFilter('day')} className="field">
              <option value="">Dia</option>
              {days.map((d) => <option key={d} value={String(d)}>Dia {d}</option>)}
            </select>
          )}
          <span className="feed-count">{shown} de {logs.length}</span>
          {filtersActive && <button type="button" onClick={() => setFilters(EMPTY_FILTERS)} className="btn-ghost text-xs">Limpar</button>}
        </div>
      )}

      {loading && <div className="feed">{[0, 1, 2].map((i) => <LogSkeleton key={i} />)}</div>}
      {!loading && error && <p className="text-sm font-bold text-[color:var(--accent)]">{error}</p>}
      {!loading && !error && logs.length === 0 && (
        <EmptyState icon="scroll" title="Nenhum log narrativo ainda"
          description="Os registros da campanha aparecerão aqui conforme forem publicados." />
      )}
      {!loading && !error && logs.length > 0 && groups.length === 0 && (
        <p className="text-sm opacity-70">Nenhum log corresponde aos filtros.</p>
      )}
      {!loading && !error && groups.map(([dayLabel, items]) => (
        <section key={dayLabel} aria-label={dayLabel}>
          <h3 className="feed-day"><span className="capitalize">{dayLabel}</span></h3>
          <div className="feed">{items.map(renderLog)}</div>
        </section>
      ))}
    </div>
  );
}
