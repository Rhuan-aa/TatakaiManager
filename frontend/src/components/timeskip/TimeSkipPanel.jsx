import { useCallback, useEffect, useRef, useState } from 'react';
import {
  listTimeSkips,
  createTimeSkip,
  closeTimeSkip,
  setCurrentDay,
  setVenue,
  getFatiguePreview,
  deleteTimeSkip,
  listSubmissions,
  reviewSubmission,
} from '../../api/timeskips';
import { parseApiError } from '../../api/parseApiError';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';
import SlotGrid from '../booking/SlotGrid';
import { useCampaignTopic } from '../../hooks/useWebSocket';
import TimeSkipActivityManager from './TimeSkipActivityManager';
import Icon from '../ui/Icon';

const inputClass = 'field';

const SUB_LABEL = { PENDING: 'Aguardando', APPROVED: 'Aprovado', REJECTED: 'Recusado' };

export default function TimeSkipPanel({ campaignId, isMaster, npcs, focusTimeSkipId }) {
  const toast = useToast();
  const [timeSkips, setTimeSkips] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: '', totalDays: 1, mode: 'IDLE_PERIOD', slotsPerDay: 4 });
  const [createError, setCreateError] = useState('');
  const [createFields, setCreateFields] = useState({});
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [activitiesVersion, setActivitiesVersion] = useState(0);
  const [tick, setTick] = useState(0);
  const [submissions, setSubmissions] = useState([]);
  const [rejectingId, setRejectingId] = useState(null);
  const [rejectNote, setRejectNote] = useState('');
  const submissionsRef = useRef(null);
  // outro cliente mudou dia, encerrou, criou ou excluiu um TimeSkip: recarrega a lista
  const onCampaign = useCallback((msg) => { if (msg?.type === 'TIMESKIP') setTick((t) => t + 1); }, []);
  useCampaignTopic(campaignId, 'campaign', onCampaign);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const list = await listTimeSkips(campaignId);
        if (!active) return;
        setTimeSkips(list);
        setSelectedId((prev) => {
          if (focusTimeSkipId && list.some((t) => t.id === focusTimeSkipId)) return focusTimeSkipId;
          return list.some((t) => t.id === prev) ? prev : list[0]?.id ?? null;
        });
      } catch (err) {
        if (active) setError(parseApiError(err).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [campaignId, tick, focusTimeSkipId]);

  const selected = timeSkips.find((t) => t.id === selectedId) ?? null;
  const showSubmissions = isMaster && selected?.mode === 'TIMESKIP' && selected?.status === 'ACTIVE';
  const selectedKey = showSubmissions ? selected.id : null;

  // envios dos jogadores (Mestre): recarrega ao trocar de TimeSkip ou ao chegar evento TIMESKIP
  useEffect(() => {
    if (!selectedKey) { setSubmissions([]); return undefined; }
    let active = true;
    listSubmissions(selectedKey)
      .then((list) => { if (active) setSubmissions(list); })
      .catch(() => {});
    return () => { active = false; };
  }, [selectedKey, tick]);

  // navegação vinda da aba Aprovações: rola até a lista de envios
  useEffect(() => {
    if (focusTimeSkipId && selectedKey === focusTimeSkipId) {
      submissionsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [focusTimeSkipId, selectedKey, submissions.length]);

  async function handleReview(userId, approved) {
    try {
      const updated = await reviewSubmission(selected.id, userId, {
        approved,
        note: approved ? null : rejectNote.trim() || null,
      });
      setSubmissions((prev) => prev.map((s) => (s.userId === userId ? updated : s)));
      setRejectingId(null);
      setRejectNote('');
      toast(approved ? 'Envio aprovado.' : 'Envio recusado.');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    }
  }

  function upsert(updated) {
    setTimeSkips((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
  }

  async function handleCreate(event) {
    event.preventDefault();
    setCreateError('');
    setCreateFields({});
    if (!form.name.trim()) {
      setCreateFields({ name: 'Informe o nome.' });
      return;
    }
    try {
      const created = await createTimeSkip(campaignId, {
        name: form.name,
        totalDays: form.mode === 'IDLE_PERIOD' ? 1 : Number(form.totalDays),
        mode: form.mode,
        slotsPerDay: Number(form.slotsPerDay),
      });
      setTimeSkips((prev) => [...prev, created]);
      setSelectedId(created.id);
      setCreating(false);
      setForm({ name: '', totalDays: 1, mode: 'IDLE_PERIOD', slotsPerDay: 4 });
      toast(
        form.mode === 'IDLE_PERIOD'
          ? `Período Ocioso "${created.name}" criado.`
          : `TimeSkip "${created.name}" criado.`
      );
    } catch (err) {
      const parsed = parseApiError(err);
      setCreateError(parsed.message);
      setCreateFields(parsed.fields);
    }
  }

  async function handleAdvanceDay() {
    setError('');
    try {
      const updated = await setCurrentDay(selected.id, selected.currentDay + 1);
      upsert(updated);
      toast(`Dia ${updated.currentDay}: treinos e Repousos do dia anterior processados.`);
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function handleVenue(venue) {
    try {
      upsert(await setVenue(selected.id, venue));
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function showPreview() {
    setError('');
    try {
      setPreview(await getFatiguePreview(selected.id));
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function handleClose() {
    setError('');
    setPreview(null);
    try {
      const updated = await closeTimeSkip(selected.id);
      upsert(updated);
      toast('TimeSkip encerrado.');
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function handleDelete() {
    setError('');
    setDeleting(true);
    try {
      await deleteTimeSkip(selected.id);
      setTimeSkips((prev) => {
        const remaining = prev.filter((t) => t.id !== selected.id);
        setSelectedId(remaining[0]?.id ?? null);
        return remaining;
      });
      setConfirmingDelete(false);
      toast('TimeSkip excluído.');
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setDeleting(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="flex gap-2">
          <div className="skeleton h-9 w-28" />
          <div className="skeleton h-9 w-24" />
        </div>
        <div className="skeleton h-64 w-full" />
      </div>
    );
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          {timeSkips.map((t) => {
            const isIdle = t.mode === 'IDLE_PERIOD';
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => {
                  setSelectedId(t.id);
                  setPreview(null);
                  setConfirmingDelete(false);
                }}
                className={`${t.id === selectedId ? 'btn-primary' : 'btn-secondary'} px-3 py-1.5 text-sm inline-flex items-center gap-2`}
              >
                <span
                  className={`caption ${isIdle ? '' : 'bg-[var(--ink)] text-[var(--paper)]'}`}
                >
                  {isIdle ? 'Ócio' : 'TimeSkip'}
                </span>
                <span>{t.name}</span>
                {t.status === 'CLOSED' && (
                  <span className="text-xs opacity-70">(encerrado)</span>
                )}
              </button>
            );
          })}
        </div>
        {isMaster && !creating && (
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => {
                setForm({ name: 'Dia Ocioso', totalDays: 1, mode: 'IDLE_PERIOD', slotsPerDay: 4 });
                setCreating(true);
              }}
              className="btn-primary px-3 py-1.5 text-sm"
            >
              + Período Ocioso (Dia)
            </button>
            <button
              type="button"
              onClick={() => {
                setForm({ name: '', totalDays: 7, mode: 'TIMESKIP', slotsPerDay: 4 });
                setCreating(true);
              }}
              className="btn-secondary px-3 py-1.5 text-sm"
            >
              + Novo TimeSkip
            </button>
          </div>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-[var(--accent)]">{error}</p>}

      {creating && (
        <form
          onSubmit={handleCreate}
          className="panel mt-4 p-4"
          noValidate
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
            <div className="w-full sm:w-64">
              <label htmlFor="ts-mode" className="block text-sm font-bold uppercase text-[var(--text)]">
                Tipo de período
              </label>
              <select
                id="ts-mode"
                value={form.mode}
                onChange={(e) => {
                  const newMode = e.target.value;
                  setForm((p) => ({
                    ...p,
                    mode: newMode,
                    totalDays: newMode === 'IDLE_PERIOD' ? (p.totalDays === 7 ? 1 : p.totalDays) : (p.totalDays === 1 ? 7 : p.totalDays),
                    name:
                      newMode === 'IDLE_PERIOD' && (!p.name || p.name === '')
                        ? 'Dia Ocioso'
                        : p.name,
                  }));
                }}
                className={inputClass}
              >
                <option value="IDLE_PERIOD">Período Ocioso — um único dia (pontos de ócio configuráveis)</option>
                <option value="TIMESKIP">TimeSkip — Salto temporal (fora de cena / semanas)</option>
              </select>
            </div>
            <div className="flex-1">
              <label htmlFor="ts-name" className="block text-sm font-bold uppercase text-[var(--text)]">
                Nome
              </label>
              <input
                id="ts-name"
                type="text"
                required
                value={form.name}
                onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
                className={inputClass}
              />
              {createFields.name && (
                <p className="mt-1 text-xs text-[var(--accent)]">{createFields.name}</p>
              )}
            </div>
            {form.mode === 'IDLE_PERIOD' ? (
              <div className="w-full sm:w-28">
                <label htmlFor="ts-points" className="block text-sm font-bold uppercase text-[var(--text)]">
                  Pontos de ócio
                </label>
                <input
                  id="ts-points"
                  type="number"
                  min={1}
                  max={12}
                  required
                  value={form.slotsPerDay}
                  onChange={(e) => setForm((p) => ({ ...p, slotsPerDay: e.target.value }))}
                  className={inputClass}
                />
                <p className="hint mt-1">Padrão 4. Use mais para um TimeSkip em que cada dia vale um período maior. O slot extra (custo 0) continua existindo.</p>
                {createFields.slotsPerDay && (
                  <p className="mt-1 text-xs text-[var(--accent)]">{createFields.slotsPerDay}</p>
                )}
              </div>
            ) : (
            <div className="w-full sm:w-28">
              <label htmlFor="ts-days" className="block text-sm font-bold uppercase text-[var(--text)]">
                Dias
              </label>
              <input
                id="ts-days"
                type="number"
                min={1}
                max={365}
                required
                value={form.totalDays}
                onChange={(e) => setForm((p) => ({ ...p, totalDays: e.target.value }))}
                className={inputClass}
              />
              {createFields.totalDays && (
                <p className="mt-1 text-xs text-[var(--accent)]">{createFields.totalDays}</p>
              )}
            </div>
            )}
            <div className="flex gap-2">
              <button
                type="submit"
                className="btn-primary px-3 py-2 text-sm"
              >
                Criar
              </button>
              <button
                type="button"
                onClick={() => setCreating(false)}
                className="btn-secondary px-3 py-2 text-sm"
              >
                Cancelar
              </button>
            </div>
          </div>
          {createError && <p className="mt-2 text-sm text-[var(--accent)]">{createError}</p>}
        </form>
      )}

      {!selected ? (
        <EmptyState
          className="mt-4"
          icon="calendar"
          title="Nenhum TimeSkip ou Período Ocioso ainda"
          description={
            isMaster
              ? 'Crie o primeiro Período Ocioso ou TimeSkip para começar a agendar as ações.'
              : 'Aguarde o Mestre criar um Período Ocioso ou TimeSkip para começar a agendar.'
          }
          action={
            isMaster && !creating ? (
              <div className="flex flex-wrap justify-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setForm({ name: 'Dia Ocioso', totalDays: 1, mode: 'IDLE_PERIOD', slotsPerDay: 4 });
                    setCreating(true);
                  }}
                  className="btn-primary px-3 py-1.5 text-sm"
                >
                  + Período Ocioso (Dia)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setForm({ name: '', totalDays: 7, mode: 'TIMESKIP', slotsPerDay: 4 });
                    setCreating(true);
                  }}
                  className="btn-secondary px-3 py-1.5 text-sm"
                >
                  + Novo TimeSkip
                </button>
              </div>
            ) : null
          }
        />
      ) : (
        <div className="panel mt-4 p-4">
          <div className="page-hero page-hero-compact halftone-bg">
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`caption ${selected.mode === 'IDLE_PERIOD' ? '' : 'bg-[var(--ink)] text-[var(--paper)]'}`}
              >
                {selected.mode === 'IDLE_PERIOD' ? 'Período Ocioso' : 'TimeSkip'}
              </span>
              <h2 className="title-stamp text-2xl">
                {selected.name}
              </h2>
              {selected.status === 'CLOSED' && (
                <span className="text-xs font-bold uppercase text-[var(--muted)] border border-current px-1.5 py-0.5 rounded">
                  (encerrado)
                </span>
              )}
            </div>
            <p className="text-xs text-[var(--muted)] font-medium">
              {selected.mode === 'IDLE_PERIOD'
                ? `Período Ocioso: um dia com ${selected.slotsPerDay ?? 4} pontos de ócio + 1 slot extra (custo 0). Quem não atua no dia não sofre desgaste.`
                : 'TimeSkip (Salto Temporal): os dias só organizam a agenda e podem ser editados a qualquer momento. Treino, repouso e desgaste são consolidados ao encerrar.'}
            </p>
          </div>
          {preview && (
            <div className="panel mb-4 p-3 text-sm">
              <p className="caption mb-2">Desgaste projetado ao encerrar</p>
              {preview.length === 0 ? <p>Nenhuma ficha concluída.</p> : (
                <ul className="space-y-1">
                  {preview.map((p) => (
                    <li key={p.userId}>
                      <b>{p.userName}</b>: Físico {p.physicalFatigue} <Icon name="forward" size={14} className="align-[-2px]" /> {p.projectedPhysical}
                      {p.unrestedDays > 0 && ` (${p.unrestedDays} dia(s) sem Repouso)`} · Mental {p.mentalFatigue}
                    </li>
                  ))}
                </ul>
              )}
              <p className="mt-2 text-xs text-[var(--muted)]">Inclua Repouso nos dias marcados para evitar o desgaste.</p>
            </div>
          )}
          {isMaster && (
            <div className="mb-4 flex flex-wrap items-center gap-2">
              {selected.status === 'ACTIVE' && (
                <>
                  {selected.mode === 'IDLE_PERIOD' && <button
                    type="button"
                    onClick={handleAdvanceDay}
                    disabled={selected.currentDay >= selected.totalDays}
                    className="btn-secondary px-3 py-1.5 text-sm disabled:opacity-40"
                  >
                    Avançar dia ({selected.currentDay} <Icon name="forward" size={14} className="align-[-2px]" /> {selected.currentDay + 1})
                  </button>}
                  <button
                    type="button"
                    onClick={preview ? handleClose : showPreview}
                    className="btn-secondary px-3 py-1.5 text-sm text-[var(--accent)]"
                  >
                    {preview ? 'Confirmar: ' : ''}
                    {selected.mode === 'IDLE_PERIOD' ? 'Encerrar Período' : 'Encerrar TimeSkip'}
                  </button>
                  {preview && (
                    <button type="button" onClick={() => setPreview(null)} className="btn-secondary px-3 py-1.5 text-sm">
                      Cancelar
                    </button>
                  )}
                  <label className="label flex items-center gap-2">
                    Local de treino
                    <select className="field m-0 w-auto py-1 text-sm" value={selected.venue ?? 'COMMON_GYM'}
                      onChange={(e) => handleVenue(e.target.value)}>
                      <option value="FIELD">Campo (2 Plenos/semana)</option>
                      <option value="COMMON_GYM">Academia Comum (3)</option>
                      <option value="FIGHT_GYM">Academia de Luta (3)</option>
                      <option value="HIGH_PERFORMANCE">Centro de Alto Rendimento (4)</option>
                    </select>
                  </label>
                </>
              )}
              {confirmingDelete ? (
                <div className="flex items-center gap-2">
                  <span className="text-sm text-[var(--accent)]">Excluir permanentemente?</span>
                  <button
                    type="button"
                    onClick={handleDelete}
                    disabled={deleting}
                    className="btn-primary px-3 py-1.5 text-sm disabled:opacity-50"
                  >
                    {deleting ? 'Excluindo...' : 'Confirmar exclusão'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmingDelete(false)}
                    disabled={deleting}
                    className="btn-secondary px-3 py-1.5 text-sm"
                  >
                    Cancelar
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(true)}
                  className="ml-auto btn-secondary px-3 py-1.5 text-sm text-[var(--accent)]"
                >
                  {selected.mode === 'IDLE_PERIOD' ? 'Excluir Período' : 'Excluir TimeSkip'}
                </button>
              )}
            </div>
          )}
          {isMaster && (
            <TimeSkipActivityManager
              campaignId={campaignId}
              timeSkipId={selected.id}
              isActive={selected.status === 'ACTIVE'}
              onChanged={() => setActivitiesVersion((v) => v + 1)}
            />
          )}
          {showSubmissions && (
            <div ref={submissionsRef} className="panel mb-4 p-3">
              <p className="caption mb-2">Envios dos jogadores</p>
              {submissions.length === 0 ? (
                <p className="text-sm text-[var(--muted)]">Nenhum jogador enviou a agenda ainda.</p>
              ) : (
                <ul className="space-y-2">
                  {submissions.map((s) => (
                    <li key={s.userId} className="text-sm">
                      <div className="flex flex-wrap items-center gap-2">
                        <b>{s.userName}</b>
                        <span className="caption">{SUB_LABEL[s.status] ?? s.status}</span>
                        {s.status === 'REJECTED' && s.note && <span className="text-[var(--muted)]">{s.note}</span>}
                        {s.status === 'PENDING' && rejectingId !== s.userId && (
                          <span className="ml-auto flex gap-2">
                            <button type="button" onClick={() => handleReview(s.userId, true)} className="btn-primary px-3 py-1 text-xs">Aprovar</button>
                            <button type="button" onClick={() => { setRejectingId(s.userId); setRejectNote(''); }} className="btn-secondary px-3 py-1 text-xs text-[var(--accent)]">Recusar</button>
                          </span>
                        )}
                      </div>
                      {rejectingId === s.userId && (
                        <div className="mt-2 flex flex-wrap gap-2">
                          <input
                            type="text"
                            className={`${inputClass} m-0 flex-1`}
                            placeholder="Motivo (opcional)"
                            maxLength={500}
                            value={rejectNote}
                            onChange={(e) => setRejectNote(e.target.value)}
                          />
                          <button type="button" onClick={() => handleReview(s.userId, false)} className="btn-primary px-3 py-1 text-xs">Confirmar recusa</button>
                          <button type="button" onClick={() => setRejectingId(null)} className="btn-secondary px-3 py-1 text-xs">Cancelar</button>
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <SlotGrid
            campaignId={campaignId}
            isMaster={isMaster}
            timeSkip={selected}
            npcs={npcs.filter((n) => n.bookable !== false)}
            activitiesVersion={activitiesVersion}
          />
        </div>
      )}
    </div>
  );
}
