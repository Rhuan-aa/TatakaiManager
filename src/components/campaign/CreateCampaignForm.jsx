import { useState } from 'react';
import { createCampaign } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';

const inputClass =
  'mt-1 w-full rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder:text-zinc-500 focus:border-red-500 focus:outline-none focus:ring-1 focus:ring-red-500';

export default function CreateCampaignForm({ onCreated, onCancel }) {
  const [form, setForm] = useState({ name: '', description: '' });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      const campaign = await createCampaign(form);
      onCreated(campaign);
    } catch (err) {
      const parsed = parseApiError(err);
      setError(parsed.message);
      setFieldErrors(parsed.fields);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-xl border border-zinc-700 bg-zinc-800 p-6"
      noValidate
    >
      <h2 className="text-base font-semibold text-white">Nova campanha</h2>

      <div className="mt-4 space-y-4">
        <div>
          <label htmlFor="name" className="block text-sm font-medium text-zinc-400">
            Nome
          </label>
          <input
            id="name"
            name="name"
            type="text"
            required
            value={form.name}
            onChange={handleChange}
            className={inputClass}
          />
          {fieldErrors.name && <p className="mt-1 text-xs text-red-400">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-medium text-zinc-400">
            Descrição <span className="text-zinc-600">(opcional)</span>
          </label>
          <textarea
            id="description"
            name="description"
            rows={3}
            value={form.description}
            onChange={handleChange}
            className={inputClass}
          />
          {fieldErrors.description && (
            <p className="mt-1 text-xs text-red-400">{fieldErrors.description}</p>
          )}
        </div>

        {error && <p className="text-sm text-red-400">{error}</p>}
      </div>

      <div className="mt-5 flex justify-end gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-md border border-zinc-700 px-3 py-2 text-sm font-medium text-zinc-400 hover:bg-zinc-700"
        >
          Cancelar
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
        >
          {submitting ? 'Criando...' : 'Criar campanha'}
        </button>
      </div>
    </form>
  );
}
