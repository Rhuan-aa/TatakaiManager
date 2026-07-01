import { useEffect, useState } from 'react';
import {
  listTimeSkips,
  createTimeSkip,
  closeTimeSkip,
  setCurrentDay,
  deleteTimeSkip,
} from '../../api/timeskips';
import { parseApiError } from '../../api/parseApiError';
import SlotGrid from '../booking/SlotGrid';

const inputClass =
  'mt-1 w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500';

export default function TimeSkipPanel({ campaignId, isMaster, npcs }) {
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
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setDeleting(false);
    }
  }

  if (loading) return <p className="text-sm text-zinc-500">Carregando TimeSkips...</p>;

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
              className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                t.id === selectedId
                  ? 'bg-red-600 text-white'
                  : 'border border-zinc-700 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200'
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
            className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-700"
          >
            Novo TimeSkip
          </button>
        )}
      </div>

      {error && <p className="mt-2 text-sm text-red-400">{error}</p>}

      {creating && (
        <form
          onSubmit={handleCreate}
          className="mt-4 rounded-lg border border-zinc-700 bg-zinc-800 p-4"
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
        <p className="mt-4 text-sm text-zinc-500">
          Nenhum TimeSkip ainda.
          {isMaster ? ' Crie o primeiro para começar a agendar.' : ''}
        </p>
      ) : (
        <div className="mt-4 rounded-lg border border-zinc-700 bg-zinc-800 p-4">
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
