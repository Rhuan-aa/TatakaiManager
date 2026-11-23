import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { deleteCampaign, listMembers } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import { useToast } from '../../contexts/ToastContext';
import InviteMemberForm from './InviteMemberForm';
import RuleSettings from './RuleSettings';

/** Aba Configurações da campanha (Mestre): quem está na mesa + regras opcionais. */
export default function CampaignSettings({ campaignId, isMaster }) {
  const [members, setMembers] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  const toast = useToast();

  async function remove() {
    setBusy(true);
    try {
      await deleteCampaign(campaignId);
      toast('Campanha excluída.');
      navigate('/dashboard');
    } catch (err) {
      toast(parseApiError(err).message, 'error');
      setBusy(false);
    }
  }
  const load = useCallback(() => {
    listMembers(campaignId).then(setMembers).catch(() => setMembers([]));
  }, [campaignId]);
  useEffect(() => { load(); }, [load]);

  const sorted = (members ?? []).slice().sort((a, b) => (a.role === b.role ? a.name.localeCompare(b.name) : a.role === 'MASTER' ? -1 : 1));

  return (
    <div className="flex flex-col gap-6">
      <section className="panel p-4" aria-labelledby="cfg-table">
        <h3 id="cfg-table" className="panel-title ribbon">Mesa</h3>
        <div className="mt-4 flex flex-col gap-4">
          {!members ? <div className="skeleton h-16 w-full" /> : (
            <ul className="roster">
              {sorted.map((m) => (
                <li key={m.userId} className={`roster-item${m.role === 'MASTER' ? ' is-master' : ''}`}>
                  <span className="roster-avatar" aria-hidden="true">{m.name?.charAt(0)?.toUpperCase() ?? '?'}</span>
                  <span className="min-w-0">
                    <span className="roster-name">{m.name}</span>
                    <span className="roster-mail">{m.email}</span>
                  </span>
                  <span className={m.role === 'MASTER' ? 'tag tag-accent' : 'tag'}>{m.role === 'MASTER' ? 'Mestre' : 'Jogador'}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="well flex flex-col gap-2">
            <p className="label">Convidar jogador</p>
            <p className="hint">A pessoa precisa já ter conta; entra como jogador.</p>
            <div className="max-w-md"><InviteMemberForm campaignId={campaignId} onInvited={load} /></div>
          </div>
        </div>
      </section>

      <section className="panel p-4" aria-labelledby="cfg-rules">
        <h3 id="cfg-rules" className="panel-title ribbon">Regras opcionais</h3>
        <div className="mt-4"><RuleSettings campaignId={campaignId} isMaster={isMaster} /></div>
      </section>

      <section className="panel p-4" aria-labelledby="cfg-delete">
        <h3 id="cfg-delete" className="panel-title ribbon">Excluir campanha</h3>
        <div className="mt-4 flex flex-col gap-2">
          {confirming ? (
            <>
              <p className="text-sm">Isso apaga a campanha para todos: fichas dos jogadores, TimeSkips, agendamentos, logs, rolagens e combates. Os NPCs continuam no seu acervo. Não dá para desfazer.</p>
              <div className="flex gap-2">
                <button type="button" className="btn-primary" disabled={busy} onClick={remove}>{busy ? 'Excluindo…' : 'Confirmar exclusão'}</button>
                <button type="button" className="btn-secondary" disabled={busy} onClick={() => setConfirming(false)}>Cancelar</button>
              </div>
            </>
          ) : (
            <button type="button" className="btn-secondary self-start" onClick={() => setConfirming(true)}>Excluir campanha</button>
          )}
        </div>
      </section>
    </div>
  );
}
