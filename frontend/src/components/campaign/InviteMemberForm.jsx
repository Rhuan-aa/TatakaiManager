import { useState } from 'react';
import { inviteMember } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

export default function InviteMemberForm({ campaignId }) {
  const toast = useToast();
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
      toast(`${member.name} adicionado à campanha.`);
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
          className="field w-full"
        />
        {error && <p className="caption mt-1">{error}</p>}
        {success && <p className="caption mt-1">{success}</p>}
      </div>
      <button
        type="submit"
        disabled={submitting}
        className="btn-primary"
      >
        {submitting ? 'Convidando...' : 'Convidar'}
      </button>
    </form>
  );
}
