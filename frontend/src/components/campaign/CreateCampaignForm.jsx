import { useState } from 'react';
import { createCampaign } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const inputClass = 'field';

export default function CreateCampaignForm({ onCreated, onCancel }) {
  const toast = useToast();
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
      toast(`Campanha "${campaign.name}" criada.`);
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
    <form onSubmit={handleSubmit} className="panel p-6" noValidate>
      <h2 className="panel-title">Nova campanha</h2>

      <div className="mt-4 space-y-4">
        <div>
          <label htmlFor="name" className="block text-sm font-medium opacity-80">
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
          {fieldErrors.name && <p className="caption mt-1">{fieldErrors.name}</p>}
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-medium opacity-80">
            Descrição <span className="opacity-60">(opcional)</span>
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
            <p className="caption mt-1">{fieldErrors.description}</p>
          )}
        </div>

        {error && <p className="caption">{error}</p>}
      </div>

      <div className="mt-5 flex justify-end gap-3">
        <button type="button" onClick={onCancel} className="btn-secondary">
          Cancelar
        </button>
        <button type="submit" disabled={submitting} className="btn-primary">
          {submitting ? 'Criando...' : 'Criar campanha'}
        </button>
      </div>
    </form>
  );
}
