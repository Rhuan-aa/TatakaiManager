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
          <div className="skeleton h-9 w-28 rounded-lg" />
          <div className="skeleton h-9 w-24 rounded-lg" />
        </div>
        <div className="skeleton h-64 w-full rounded-xl" />
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
              className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
                t.id === selectedId
                  ? 'bg-red-500/15 text-red-300 ring-1 ring-inset ring-red-500/40'
                  : 'border border-zinc-700/70 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200'
              }`}
            >
              {t.name}
              {t.status === 'CLOSED' && (
                <span className="ml-1 text-xs opacity-60">(encerrado)</span>
              )}
            </button>
          ))}
        </div>
        {isMaster && !creating && (
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="rounded-lg bg-gradient-to-b from-red-500 to-red-600 px-3 py-1.5 text-sm font-semibold text-white shadow-lg shadow-red-950/40 transition hover:to-red-700"
          >
            + Novo TimeSkip
          </button>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-red-400">{error}</p>}

      {creating && (
        <form
          onSubmit={handleCreate}
          className="mt-4 rounded-xl border border-zinc-800 bg-zinc-950/40 p-4"
          noValidate
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label htmlFor="ts-name" className="block text-sm font-medium text-zinc-400">
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
                <p className="mt-1 text-xs text-red-400">{createFields.name}</p>
              )}
            </div>
            <div className="w-full sm:w-32">
              <label htmlFor="ts-days" className="block text-sm font-medium text-zinc-400">
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
                <p className="mt-1 text-xs text-red-400">{createFields.totalDays}</p>
              )}
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700"
              >
                Criar
              </button>
              <button
                type="button"
                onClick={() => setCreating(false)}
                className="rounded-md border border-zinc-700 px-3 py-2 text-sm font-medium text-zinc-400 hover:bg-zinc-700"
              >
                Cancelar
              </button>
            </div>
          </div>
          {createError && <p className="mt-2 text-sm text-red-400">{createError}</p>}
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
                className="rounded-lg bg-gradient-to-b from-red-500 to-red-600 px-3 py-1.5 text-sm font-semibold text-white shadow-lg shadow-red-950/40 transition hover:to-red-700"
              >
                + Novo TimeSkip
              </button>
            ) : null
          }
        />
      ) : (
        <div className="mt-4 rounded-xl border border-zinc-800 bg-zinc-950/40 p-4">
          {isMaster && (
            <div className="mb-4 flex flex-wrap items-center gap-2">
              {selected.status === 'ACTIVE' && (
                <>
                  <button
                    type="button"
                    onClick={handleAdvanceDay}
                    disabled={selected.currentDay >= selected.totalDays}
                    className="rounded-md border border-zinc-700 px-3 py-1.5 text-sm font-medium text-zinc-300 hover:bg-zinc-700 disabled:opacity-40"
                  >
                    Avançar dia ({selected.currentDay} → {selected.currentDay + 1})
                  </button>
                  <button
                    type="button"
                    onClick={handleClose}
                    className="rounded-md border border-red-900 px-3 py-1.5 text-sm font-medium text-red-400 hover:bg-red-950/50"
                  >
                    Encerrar TimeSkip
                  </button>
                </>
              )}
              {confirmingDelete ? (
                <div className="flex items-center gap-2">
                  <span className="text-sm text-red-400">Excluir permanentemente?</span>
                  <button
                    type="button"
                    onClick={handleDelete}
                    disabled={deleting}
                    className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                  >
                    {deleting ? 'Excluindo...' : 'Confirmar exclusão'}
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmingDelete(false)}
                    disabled={deleting}
                    className="rounded-md border border-zinc-700 px-3 py-1.5 text-sm font-medium text-zinc-400 hover:bg-zinc-700"
                  >
                    Cancelar
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmingDelete(true)}
                  className="ml-auto rounded-md border border-red-900 px-3 py-1.5 text-sm font-medium text-red-400 hover:bg-red-950/50"
                >
                  Excluir TimeSkip
                </button>
              )}
            </div>
          )}
          <SlotGrid campaignId={campaignId} timeSkip={selected} npcs={npcs} />
        </div>
      )}
    </div>
  );
}
