import { useEffect, useState } from 'react';
import {
  listTimeSkips,
  createTimeSkip,
  closeTimeSkip,
  setCurrentDay,
  deleteTimeSkip,
} from '../../api/timeskips';
import { parseApiError } from '../../api/parseApiError';
import { EmptyState } from '../../components/layout/AppShell';
import { useToast } from '../../contexts/ToastContext';
import SlotGrid from '../booking/SlotGrid';
import TimeSkipActivityManager from './TimeSkipActivityManager';

const inputClass = 'field';

export default function TimeSkipPanel({ campaignId, isMaster, npcs }) {
  const toast = useToast();
  const [timeSkips, setTimeSkips] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: '', totalDays: 7 });
  const [createError, setCreateError] = useState('');
  const [createFields, setCreateFields] = useState({});
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [activitiesVersion, setActivitiesVersion] = useState(0);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const list = await listTimeSkips(campaignId);
        if (!active) return;
        setTimeSkips(list);
        setSelectedId((prev) => prev ?? list[0]?.id ?? null);
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

  const selected = timeSkips.find((t) => t.id === selectedId) ?? null;

  function upsert(updated) {
    setTimeSkips((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
  }

  async function handleCreate(event) {
    event.preventDefault();
    setCreateError('');
    setCreateFields({});
    try {
      const created = await createTimeSkip(campaignId, {
        name: form.name,
        totalDays: Number(form.totalDays),
      });
      setTimeSkips((prev) => [...prev, created]);
      setSelectedId(created.id);
      setCreating(false);
      setForm({ name: '', totalDays: 7 });
      toast(`TimeSkip "${created.name}" criado.`);
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
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  async function handleClose() {
    setError('');
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
          {timeSkips.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                setSelectedId(t.id);
                setConfirmingDelete(false);
              }}
              className={`${t.id === selectedId ? 'btn-primary' : 'btn-secondary'} px-3 py-1.5 text-sm`}
            >
              {t.name}
              {t.status === 'CLOSED' && (
                <span className="ml-1 text-xs opacity-70">(encerrado)</span>
              )}
            </button>
          ))}
        </div>
        {isMaster && !creating && (
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="btn-primary px-3 py-1.5 text-sm"
          >
            + Novo TimeSkip
          </button>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-[var(--accent)]">{error}</p>}

      {creating && (
        <form
          onSubmit={handleCreate}
          className="panel mt-4 p-4"
          noValidate
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
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
            <div className="w-full sm:w-32">
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
          icon="🗓️"
          title="Nenhum TimeSkip ainda"
          description={
            isMaster
              ? 'Crie o primeiro TimeSkip para começar a agendar as interações.'
              : 'Aguarde o Mestre criar um TimeSkip para começar a agendar.'
          }
          action={
            isMaster && !creating ? (
              <button
                type="button"
                onClick={() => setCreating(true)}
                className="btn-primary px-3 py-1.5 text-sm"
              >
                + Novo TimeSkip
              </button>
            ) : null
          }
        />
      ) : (
        <div className="panel mt-4 p-4">
          {isMaster && (
            <div className="mb-4 flex flex-wrap items-center gap-2">
              {selected.status === 'ACTIVE' && (
                <>
                  <button
                    type="button"
                    onClick={handleAdvanceDay}
                    disabled={selected.currentDay >= selected.totalDays}
                    className="btn-secondary px-3 py-1.5 text-sm disabled:opacity-40"
                  >
                    Avançar dia ({selected.currentDay} → {selected.currentDay + 1})
                  </button>
                  <button
                    type="button"
                    onClick={handleClose}
                    className="btn-secondary px-3 py-1.5 text-sm text-[var(--accent)]"
                  >
                    Encerrar TimeSkip
                  </button>
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
                  Excluir TimeSkip
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
          <SlotGrid
            campaignId={campaignId}
            timeSkip={selected}
            npcs={npcs.filter((n) => n.bookable !== false)}
            activitiesVersion={activitiesVersion}
          />
        </div>
      )}
    </div>
  );
}
