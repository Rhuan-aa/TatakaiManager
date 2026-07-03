import { useEffect, useState } from 'react';
import {
  listTimeSkipActivities,
  createTimeSkipActivity,
  updateTimeSkipActivity,
  deleteTimeSkipActivity,
} from '../../api/timeSkipActivities';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const emptyForm = { name: '', description: '', idlePointCost: '1' };

/**
 * Gerenciamento (Mestre) do catálogo de atividades solo exclusivas de um TimeSkip
 * (ex.: "Reconstrução da vila") — ao contrário de Treino/Estudo/Ação geral, que são
 * fixos e aparecem em qualquer TimeSkip.
 */
export default function TimeSkipActivityManager({ campaignId, timeSkipId, isActive, onChanged }) {
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [formFields, setFormFields] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    setError('');
    (async () => {
      try {
        const list = await listTimeSkipActivities(campaignId, timeSkipId);
        if (active) setActivities(list);
      } catch (err) {
        if (active) setError(parseApiError(err).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [open, campaignId, timeSkipId]);

  function startCreate() {
    setEditingId('new');
    setForm(emptyForm);
    setFormError('');
    setFormFields({});
  }

  function startEdit(activity) {
    setEditingId(activity.id);
    setForm({
      name: activity.name,
      description: activity.description,
      idlePointCost: String(activity.idlePointCost),
    });
    setFormError('');
    setFormFields({});
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm);
    setFormError('');
    setFormFields({});
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setFormError('');
    setFormFields({});
    const payload = {
      name: form.name,
      description: form.description,
      idlePointCost: Number(form.idlePointCost),
    };
    try {
      if (editingId === 'new') {
        const created = await createTimeSkipActivity(campaignId, timeSkipId, payload);
        setActivities((prev) => [...prev, created]);
        toast(`Atividade "${created.name}" criada.`);
      } else {
        const updated = await updateTimeSkipActivity(campaignId, timeSkipId, editingId, payload);
        setActivities((prev) => prev.map((a) => (a.id === updated.id ? updated : a)));
        toast(`Atividade "${updated.name}" atualizada.`);
      }
      cancelEdit();
      onChanged?.();
    } catch (err) {
      const parsed = parseApiError(err);
      setFormError(parsed.message);
      setFormFields(parsed.fields);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(activity) {
    setError('');
    try {
      await deleteTimeSkipActivity(campaignId, timeSkipId, activity.id);
      setActivities((prev) => prev.filter((a) => a.id !== activity.id));
      toast(`Atividade "${activity.name}" excluída.`);
      onChanged?.();
    } catch (err) {
      setError(parseApiError(err).message);
    }
  }

  return (
    <div className="mb-4 rounded-lg border border-zinc-800 bg-zinc-950/30">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between px-3 py-2 text-sm font-medium text-zinc-300 hover:text-zinc-100"
      >
        <span>🛠️ Atividades solo exclusivas deste TimeSkip</span>
        <span className="text-xs text-zinc-500">{open ? '▲' : '▼'}</span>
      </button>

      {open && (
        <div className="border-t border-zinc-800 p-3">
          {!isActive && (
            <p className="mb-2 text-xs text-zinc-500">
              TimeSkip encerrado — o catálogo não pode mais ser editado.
            </p>
          )}
          {error && <p className="mb-2 text-sm text-red-400">{error}</p>}

          {loading ? (
            <div className="space-y-2">
              <div className="skeleton h-10 w-full rounded-lg" />
              <div className="skeleton h-10 w-full rounded-lg" />
            </div>
          ) : (
            <>
              {activities.length === 0 && editingId !== 'new' && (
                <p className="text-xs text-zinc-600">
                  Nenhuma atividade customizada ainda. Ex.: "Reconstrução da vila".
                </p>
              )}
              <ul className="space-y-2">
                {activities.map((a) =>
                  editingId === a.id ? (
                    <li key={a.id}>
                      <ActivityForm
                        form={form}
                        setForm={setForm}
                        onSubmit={handleSubmit}
                        onCancel={cancelEdit}
                        saving={saving}
                        formError={formError}
                        formFields={formFields}
                      />
                    </li>
                  ) : (
                    <li
                      key={a.id}
                      className="flex items-start justify-between gap-2 rounded-md border border-zinc-800 bg-zinc-900/40 px-2.5 py-2"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-zinc-200">
                          {a.name}{' '}
                          <span className="text-xs font-normal text-red-400">
                            · {a.idlePointCost} pts de ócio
                          </span>
                        </p>
                        <p className="mt-0.5 text-xs text-zinc-500">{a.description}</p>
                      </div>
                      {isActive && (
                        <div className="flex shrink-0 gap-2">
                          <button
                            type="button"
                            onClick={() => startEdit(a)}
                            className="text-xs text-zinc-400 hover:text-zinc-200"
                          >
                            editar
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(a)}
                            className="text-xs text-red-400 hover:text-red-300"
                          >
                            excluir
                          </button>
                        </div>
                      )}
                    </li>
                  )
                )}
              </ul>

              {isActive &&
                (editingId === 'new' ? (
                  <div className="mt-2">
                    <ActivityForm
                      form={form}
                      setForm={setForm}
                      onSubmit={handleSubmit}
                      onCancel={cancelEdit}
                      saving={saving}
                      formError={formError}
                      formFields={formFields}
                    />
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={startCreate}
                    className="mt-2 text-sm text-red-400 hover:text-red-300"
                  >
                    + Nova atividade
                  </button>
                ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}

function ActivityForm({ form, setForm, onSubmit, onCancel, saving, formError, formFields }) {
  return (
    <form onSubmit={onSubmit} noValidate className="rounded-md border border-zinc-700 bg-zinc-900/60 p-2.5">
      <input
        type="text"
        required
        value={form.name}
        onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        placeholder="Nome (ex.: Reconstrução da vila)"
        className="field text-sm"
      />
      {formFields.name && <p className="mt-1 text-xs text-red-400">{formFields.name}</p>}
      <textarea
        required
        value={form.description}
        onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        placeholder="Descrição (o que o jogador está fazendo)"
        rows={2}
        className="field mt-2 text-sm"
      />
      {formFields.description && <p className="mt-1 text-xs text-red-400">{formFields.description}</p>}
      <div className="mt-2 flex items-center gap-2">
        <input
          type="number"
          min={0}
          max={9999}
          required
          value={form.idlePointCost}
          onChange={(e) => setForm((f) => ({ ...f, idlePointCost: e.target.value }))}
          className="field w-24 text-sm"
        />
        <span className="text-xs text-zinc-500">pts de ócio</span>
      </div>
      {formFields.idlePointCost && (
        <p className="mt-1 text-xs text-red-400">{formFields.idlePointCost}</p>
      )}
      {formError && <p className="mt-1 text-xs text-red-400">{formError}</p>}
      <div className="mt-2 flex gap-2">
        <button
          type="submit"
          disabled={saving}
          className="rounded-md bg-red-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
        >
          {saving ? 'Salvando...' : 'Salvar'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="rounded-md border border-zinc-700 px-3 py-1.5 text-xs font-medium text-zinc-400 hover:bg-zinc-700"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
