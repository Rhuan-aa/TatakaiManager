import { useCallback, useEffect, useState } from 'react';
import GmAdjustmentPanel, { ACTIONS } from './GmAdjustmentPanel';
import {
  cancelChanges, deleteSheet, getSheet, listChangeRequests, proposeChanges, reviewChanges,
} from '../../api/characterSheets';
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

function summary(c) {
  const label = ACTIONS[c.action]?.label ?? c.action;
  const detail = [c.field, c.value, c.item?.name, c.injury?.region].filter((x) => x != null && x !== '').join(' · ');
  return `${label}${detail ? ` — ${detail}` : ''} (${c.reason})`;
}

/**
 * Dono: monta uma lista de ajustes (os mesmos do Mestre) e envia para aprovação.
 * Mestre: vê os pedidos pendentes e aprova (aplica tudo) ou recusa.
 */
export function ChangeRequestsBlock({ sheet, campaignId, isMaster, isOwner, onChange }) {
  const toast = useToast();
  const [requests, setRequests] = useState([]);
  const [draft, setDraft] = useState([]);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => listChangeRequests(sheet.id).then(setRequests).catch(() => {}), [sheet.id]);
  useEffect(() => { load(); }, [load, sheet.version]);

  const pending = requests.find((r) => r.status === 'PENDING');

  async function run(fn, ok) {
    setBusy(true);
    try {
      await fn();
      if (ok) toast(ok);
      await load();
    } catch (err) {
      toast(parseApiError(err).message, 'error');
    } finally {
      setBusy(false);
    }
  }

  const send = () => run(async () => { await proposeChanges(sheet.id, draft); setDraft([]); }, 'Pedido enviado ao Mestre.');
  const decide = (r, approved) => run(async () => {
    await reviewChanges(sheet.id, r.id, { approved, note: note.trim() || null });
    setNote('');
    if (approved) onChange(await getSheet(sheet.id));
  }, approved ? 'Ajustes aplicados.' : 'Pedido recusado.');

  return (
    <div className="flex flex-col gap-3">
      {isOwner && !isMaster && !pending && (
        <>
          <p className="hint">Adicione quaisquer ajustes (atributos, recursos, EL, PS, traços, itens…). Nada muda na ficha até o Mestre aprovar.</p>
          <GmAdjustmentPanel sheet={sheet} campaignId={campaignId}
            onSubmit={async (body) => { setDraft((d) => [...d, body]); return null; }} />
          {draft.length > 0 && (
            <div className="bubble p-2">
              <ul className="flex flex-col gap-1 text-sm">
                {draft.map((c, i) => (
                  <li key={i} className="flex items-start justify-between gap-2">
                    <span>{summary(c)}</span>
                    <button type="button" className="btn-ghost" onClick={() => setDraft(draft.filter((_, j) => j !== i))}>Remover</button>
                  </li>
                ))}
              </ul>
              <button type="button" className="btn-primary mt-2" disabled={busy} onClick={send}>Enviar para aprovação</button>
            </div>
          )}
        </>
      )}
      {requests.length === 0 && (isMaster || pending) && <p className="text-sm">Nenhum pedido.</p>}
      {requests.map((r) => (
        <div key={r.id} className="bubble p-2 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className={`tag ${r.status === 'PENDING' ? 'tag-accent' : ''}`}>{STATUS[r.status]}</span>
            <span className="opacity-70">{new Date(r.createdAt).toLocaleString('pt-BR')}</span>
          </div>
          <ul className="mt-1 list-disc pl-5">{r.changes.map((c, i) => <li key={i}>{summary(c)}</li>)}</ul>
          {r.reviewNote && <p className="mt-1 opacity-80">Mestre: {r.reviewNote}</p>}
          {r.status === 'PENDING' && isMaster && (
            <div className="mt-2 flex flex-col gap-2">
              <input className="field w-full" maxLength={500} placeholder="Observação (opcional)" value={note}
                onChange={(e) => setNote(e.target.value)} />
              <div className="flex gap-2">
                <button type="button" className="btn-primary" disabled={busy} onClick={() => decide(r, true)}>Aprovar e aplicar</button>
                <button type="button" className="btn-secondary" disabled={busy} onClick={() => decide(r, false)}>Recusar</button>
              </div>
            </div>
          )}
          {r.status === 'PENDING' && !isMaster && (
            <button type="button" className="btn-secondary mt-2" disabled={busy}
              onClick={() => run(() => cancelChanges(sheet.id, r.id), 'Pedido cancelado.')}>Cancelar pedido</button>
          )}
        </div>
      ))}
    </div>
  );
}
