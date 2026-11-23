import { useMemo, useState } from 'react';
import { reviewLog } from '../../api/logs';
import { reviewSubmission } from '../../api/timeskips';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import { EmptyState } from '../layout/AppShell';
import Icon from '../ui/Icon';

const KIND_LABELS = {
  SHEET: 'Fichas',
  SHEET_CHANGES: 'Alterações de ficha',
  BUILD_TRAIT: 'Traços da construção',
  ART_REQUEST: 'Artes solicitadas',
  TECHNIQUE_REQUEST: 'Técnicas solicitadas',
  NOTEBOOK_DRAFT: 'Caderno Marcial',
  OVERCOME: 'Pontos de Superação',
  CREATED_ART: 'Artes criadas (catálogo)',
  LOG: 'Logs',
  TIMESKIP_SUBMISSION: 'Agendamentos do TimeSkip',
};

const KIND_ORDER = Object.keys(KIND_LABELS);
const rtf = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' });

function formatWhen(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const mins = Math.round((d.getTime() - Date.now()) / 60000);
  const abs = Math.abs(mins);
  const rel = abs < 60 ? rtf.format(mins, 'minute')
    : abs < 1440 ? rtf.format(Math.round(mins / 60), 'hour')
    : abs < 10080 ? rtf.format(Math.round(mins / 1440), 'day')
    : null;
  const date = d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  return rel ?? date;
}

function ApprovalSkeleton() {
  return (
    <div className="panel p-4">
      <div className="skeleton h-4 w-40" />
      <div className="skeleton mt-2 h-3 w-64" />
    </div>
  );
}

export default function ApprovalsPanel({ campaignId, items, loading, onReload, onOpen }) {
  const toast = useToast();
  const [busyKey, setBusyKey] = useState(null);

  const groups = useMemo(() => KIND_ORDER
    .map((kind) => [kind, (items ?? []).filter((i) => i.kind === kind)])
    .filter(([, list]) => list.length > 0), [items]);

  async function review(item, approved) {
    const key = `${item.kind}:${item.id}`;
    setBusyKey(key);
    try {
      if (item.kind === 'LOG') await reviewLog(campaignId, item.logId, { approved, note: null });
      else await reviewSubmission(item.timeSkipId, item.userId, { approved, note: null });
      toast(approved ? 'Aprovado.' : 'Recusado.', 'success');
      onReload?.();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusyKey(null);
    }
  }

  if (loading && !items?.length) {
    return <div className="space-y-3"><ApprovalSkeleton /><ApprovalSkeleton /><ApprovalSkeleton /></div>;
  }
  if (!groups.length) {
    return <EmptyState icon="check" title="Nada aguardando aprovação." />;
  }

  return (
    <div className="space-y-6">
      {groups.map(([kind, list]) => (
        <section key={kind}>
          <h3 className="panel-title mb-2 flex items-center gap-2 text-lg text-[var(--text)]">
            {KIND_LABELS[kind]}
            <span className="tag-accent">{list.length}</span>
          </h3>
          <ul className="space-y-2">
            {list.map((item) => {
              const key = `${item.kind}:${item.id}`;
              const inline = item.kind === 'LOG' || item.kind === 'TIMESKIP_SUBMISSION';
              const busy = busyKey === key;
              return (
                <li key={key} className="panel flex flex-wrap items-center gap-2 p-0">
                  <button
                    type="button"
                    onClick={() => onOpen?.(item)}
                    className="flex min-w-0 flex-1 items-center gap-3 p-3 text-left hover:bg-[var(--highlight)] focus-visible:outline focus-visible:outline-[3px] focus-visible:outline-offset-[-3px] focus-visible:outline-[var(--accent)]"
                  >
                    <Icon name="forward" size={16} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-bold text-[var(--text)]">{item.title}</span>
                      <span className="block truncate text-xs text-[var(--text)] opacity-70">
                        {item.requesterName}{item.createdAt ? ` · ${formatWhen(item.createdAt)}` : ''}
                      </span>
                      {item.details?.length > 0 && (
                        <ul className="mt-1 list-disc pl-5 text-xs text-[var(--text)]">
                          {item.details.map((l, i) => <li key={i}>{l}</li>)}
                        </ul>
                      )}
                    </span>
                  </button>
                  {inline && (
                    <div className="flex shrink-0 gap-2 pr-3">
                      <button type="button" disabled={busy} onClick={() => review(item, true)}
                        className="btn-primary px-2 py-1 text-xs disabled:opacity-50">Aprovar</button>
                      <button type="button" disabled={busy} onClick={() => review(item, false)}
                        className="btn-ghost text-xs text-[color:var(--accent)] disabled:opacity-50">Recusar</button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}
