import { useEffect, useState } from 'react';
import { deleteSheet, listChangeRequests } from '../../api/characterSheets';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';

const STATUS = { PENDING: 'Aguardando o Mestre', APPROVED: 'Aprovado', REJECTED: 'Recusado' };

/** Exclusão em duas etapas: o primeiro clique só pede confirmação. */
export function DeleteSheetBlock({ sheet, onDeleted }) {
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  async function remove() {
    setBusy(true);
    try {
      await deleteSheet(sheet.id);
      toast('Ficha excluída.');
      onDeleted();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
      setBusy(false);
    }
  }

  return confirming ? (
    <div className="flex flex-col gap-2">
      <p className="text-sm">Excluir <strong>{sheet.name}</strong> apaga a ficha e todo o histórico dela. Não dá para desfazer.</p>
      <div className="flex gap-2">
        <button type="button" className="btn-primary" disabled={busy} onClick={remove}>
          {busy ? 'Excluindo…' : 'Confirmar exclusão'}
        </button>
        <button type="button" className="btn-secondary" disabled={busy} onClick={() => setConfirming(false)}>Cancelar</button>
      </div>
    </div>
  ) : (
    <button type="button" className="btn-secondary self-start" onClick={() => setConfirming(true)}>Excluir ficha</button>
  );
}

/** Histórico dos pedidos de alteração (o pendente é decidido na faixa do topo da ficha). */
export function ChangeRequestsBlock({ sheet, refreshKey }) {
  const [requests, setRequests] = useState(null);
  useEffect(() => {
    let active = true;
    listChangeRequests(sheet.id).then((d) => active && setRequests(d)).catch(() => active && setRequests([]));
    return () => { active = false; };
  }, [sheet.id, refreshKey]);

  if (!requests) return <div className="skeleton h-12 w-full" />;
  if (requests.length === 0) return <p className="text-sm">Nenhum pedido ainda. O jogador usa o Modo edição no topo da ficha.</p>;
  return (
    <div className="flex flex-col gap-3">
      {requests.map((r) => (
        <div key={r.id} className="bubble p-2 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`tag ${r.status === 'PENDING' ? 'tag-accent' : ''}`}>{STATUS[r.status]}</span>
            <span className="opacity-70">{new Date(r.createdAt).toLocaleString('pt-BR')}</span>
          </div>
          <ul className="mt-1 list-disc pl-5">{r.summary.map((l, i) => <li key={i}>{l}</li>)}</ul>
          {r.changes[0]?.reason && <p className="mt-1 opacity-80">Motivo: {r.changes[0].reason}</p>}
          {r.reviewNote && <p className="mt-1 opacity-80">Mestre: {r.reviewNote}</p>}
        </div>
      ))}
    </div>
  );
}
