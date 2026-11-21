import { useCallback, useEffect, useState } from 'react';
import { listMembers } from '../../api/campaigns';
import InviteMemberForm from './InviteMemberForm';
import RuleSettings from './RuleSettings';

/** Aba Configurações da campanha (Mestre): quem está na mesa + regras opcionais. */
export default function CampaignSettings({ campaignId, isMaster }) {
  const [members, setMembers] = useState(null);
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
    </div>
  );
}
