import { useEffect, useState } from 'react';
import { listLogs, createLog } from '../../api/logs';
import { parseApiError } from '../../api/parseApiError';

const INTERACTION_LABELS = {
  TREINO: 'Treino',
  TRABALHO: 'Trabalho',
  DESCANSO: 'Descanso',
  OUTRO: 'Outro',
};

function formatDate(iso) {
  return new Date(iso).toLocaleString('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function LogPanel({ campaignId, isMaster }) {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [narrative, setNarrative] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const data = await listLogs(campaignId);
        if (active) setLogs(data);
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

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitError('');
    setSubmitting(true);
    try {
      const created = await createLog(campaignId, { narrative });
      setLogs((prev) => [created, ...prev]);
      setNarrative('');
    } catch (err) {
      setSubmitError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      {isMaster && (
        <form
          onSubmit={handleSubmit}
          className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
          noValidate
        >
          <label htmlFor="narrative" className="block text-sm font-medium text-slate-700">
            Novo log narrativo
          </label>
          <textarea
            id="narrative"
            rows={3}
            required
            value={narrative}
            onChange={(e) => setNarrative(e.target.value)}
            placeholder="Descreva o que aconteceu..."
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
          />
          {submitError && <p className="mt-1 text-sm text-red-600">{submitError}</p>}
          <div className="mt-3 flex justify-end">
            <button
              type="submit"
              disabled={submitting || !narrative.trim()}
              className="rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-60"
            >
              {submitting ? 'Publicando...' : 'Publicar log'}
            </button>
          </div>
        </form>
      )}

      <div className="mt-4">
        {loading && <p className="text-sm text-slate-500">Carregando logs...</p>}
        {!loading && error && <p className="text-sm text-red-600">{error}</p>}
        {!loading && !error && logs.length === 0 && (
          <p className="text-sm text-slate-500">Nenhum log narrativo ainda.</p>
        )}
        {!loading && !error && logs.length > 0 && (
          <ul className="space-y-3">
            {logs.map((log) => (
              <li key={log.id} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm font-medium text-slate-900">{log.authorName}</span>
                  <span className="text-xs text-slate-400">{formatDate(log.createdAt)}</span>
                </div>
                {log.bookingId && (
                  <p className="mt-1 text-xs text-purple-700">
                    {log.npcName} · Dia {log.dayNumber} · Slot {log.slotNumber} ·{' '}
                    {INTERACTION_LABELS[log.interactionType] ?? log.interactionType}
                  </p>
                )}
                <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{log.narrative}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
