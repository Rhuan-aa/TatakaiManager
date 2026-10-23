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
    <div className="panel mb-4">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center justify-between px-3 py-2 text-sm font-bold uppercase text-[var(--text)] hover:underline"
      >
        <span>🛠️ Atividades solo exclusivas deste TimeSkip</span>
        <span className="text-xs">{open ? '▲' : '▼'}</span>
      </button>

      {open && (
        <div className="border-t-2 border-[var(--line)] p-3">
          {!isActive && (
            <p className="mb-2 caption">
              TimeSkip encerrado — o catálogo não pode mais ser editado.
            </p>
          )}
          {error && <p className="mb-2 text-sm text-[var(--accent)]">{error}</p>}

          {loading ? (
            <div className="space-y-2">
              <div className="skeleton h-10 w-full" />
              <div className="skeleton h-10 w-full" />
            </div>
          ) : (
            <>
              {activities.length === 0 && editingId !== 'new' && (
                <p className="text-xs text-[var(--text)] opacity-70">
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
                      className="flex items-start justify-between gap-2 border-2 border-[var(--line)] bg-[var(--bg)] px-2.5 py-2"
                    >
                      <div className="min-w-0">
                        <p className="text-sm font-bold text-[var(--text)]">
                          {a.name}{' '}
                          <span className="tag-accent">
                            · {a.idlePointCost} pts de ócio
                          </span>
                        </p>
                        <p className="mt-0.5 text-xs text-[var(--text)] opacity-70">{a.description}</p>
                      </div>
                      {isActive && (
                        <div className="flex shrink-0 gap-2">
                          <button
                            type="button"
                            onClick={() => startEdit(a)}
                            className="btn-ghost text-xs"
                          >
                            editar
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(a)}
                            className="btn-ghost text-xs text-[var(--accent)]"
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
                    className="btn-ghost mt-2 text-sm text-[var(--accent)]"
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
    <form onSubmit={onSubmit} noValidate className="border-2 border-[var(--line)] bg-[var(--panel-bg)] p-2.5">
      <input
        type="text"
        required
        value={form.name}
        onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
        placeholder="Nome (ex.: Reconstrução da vila)"
        className="field text-sm"
      />
      {formFields.name && <p className="mt-1 text-xs text-[var(--accent)]">{formFields.name}</p>}
      <textarea
        required
        value={form.description}
        onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
        placeholder="Descrição (o que o jogador está fazendo)"
        rows={2}
        className="field mt-2 text-sm"
      />
      {formFields.description && <p className="mt-1 text-xs text-[var(--accent)]">{formFields.description}</p>}
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
        <span className="text-xs text-[var(--text)] opacity-70">pts de ócio</span>
      </div>
      {formFields.idlePointCost && (
        <p className="mt-1 text-xs text-[var(--accent)]">{formFields.idlePointCost}</p>
      )}
      {formError && <p className="mt-1 text-xs text-[var(--accent)]">{formError}</p>}
      <div className="mt-2 flex gap-2">
        <button
          type="submit"
          disabled={saving}
          className="btn-primary px-3 py-1.5 text-xs disabled:opacity-50"
        >
          {saving ? 'Salvando...' : 'Salvar'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="btn-secondary px-3 py-1.5 text-xs"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
