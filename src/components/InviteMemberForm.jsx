import { useState } from 'react';
import { inviteMember } from '../api/campaigns';
import { parseApiError } from '../api/parseApiError';

export default function InviteMemberForm({ campaignId }) {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSuccess('');
    setSubmitting(true);
    try {
      const member = await inviteMember(campaignId, email);
      setSuccess(`${member.name} foi adicionado como jogador.`);
      setEmail('');
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-start" noValidate>
      <div className="flex-1">
        <input
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="e-mail do jogador"
          className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
        />
        {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
        {success && <p className="mt-1 text-xs text-green-600">{success}</p>}
      </div>
      <button
        type="submit"
        disabled={submitting}
        className="rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-60"
      >
        {submitting ? 'Convidando...' : 'Convidar'}
      </button>
    </form>
  );
}
