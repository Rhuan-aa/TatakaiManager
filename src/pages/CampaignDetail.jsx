import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getCampaign } from '../api/campaigns';
import { listCampaignNpcs } from '../api/npcs';
import { parseApiError } from '../api/parseApiError';
import InviteMemberForm from '../components/InviteMemberForm';
import TimeSkipPanel from '../components/TimeSkipPanel';
import LogPanel from '../components/LogPanel';

export default function CampaignDetail() {
  const { id } = useParams();
  const [campaign, setCampaign] = useState(null);
  const [npcs, setNpcs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setError('');
      try {
        const [campaignData, npcData] = await Promise.all([
          getCampaign(id),
          listCampaignNpcs(id),
        ]);
        if (!active) return;
        if (!campaignData) {
          setError('Campanha não encontrada.');
        } else {
          setCampaign(campaignData);
          setNpcs(npcData);
        }
      } catch (err) {
        if (active) setError(parseApiError(err).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [id]);

  const isMaster = campaign?.currentUserRole === 'MASTER';

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white px-6 py-4">
        <Link to="/dashboard" className="text-sm text-purple-600 hover:underline">
          ← Voltar para campanhas
        </Link>
      </header>

      <main className="mx-auto max-w-3xl p-6">
        {loading && <p className="text-sm text-slate-500">Carregando...</p>}

        {!loading && error && <p className="text-sm text-red-600">{error}</p>}

        {!loading && !error && campaign && (
          <>
            <section>
              <h1 className="text-2xl font-semibold text-slate-900">{campaign.name}</h1>
              {campaign.description && (
                <p className="mt-2 text-slate-600">{campaign.description}</p>
              )}
              <span className="mt-3 inline-block rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium text-purple-700">
                {isMaster ? 'Mestre' : 'Jogador'}
              </span>
            </section>

            {isMaster && (
              <section className="mt-8 rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
                <h2 className="text-base font-semibold text-slate-900">Convidar jogador</h2>
                <p className="mt-1 text-sm text-slate-500">
                  Adicione um jogador já cadastrado pelo e-mail.
                </p>
                <div className="mt-4">
                  <InviteMemberForm campaignId={id} />
                </div>
              </section>
            )}

            <section className="mt-8">
              <h2 className="text-base font-semibold text-slate-900">NPCs</h2>
              {npcs.length === 0 ? (
                <p className="mt-2 text-sm text-slate-500">Nenhum NPC nesta campanha ainda.</p>
              ) : (
                <ul className="mt-3 space-y-2">
                  {npcs.map((npc) => (
                    <li
                      key={npc.id}
                      className="flex items-center justify-between rounded-lg border border-slate-200 bg-white p-3 shadow-sm"
                    >
                      <span className="font-medium text-slate-900">{npc.name}</span>
                      {isMaster && (
                        <span
                          className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                            npc.visible
                              ? 'bg-green-100 text-green-700'
                              : 'bg-slate-100 text-slate-500'
                          }`}
                        >
                          {npc.visible ? 'Visível' : 'Oculto'}
                        </span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="mt-8">
              <h2 className="text-base font-semibold text-slate-900">Agenda (TimeSkips)</h2>
              <div className="mt-3">
                <TimeSkipPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
              </div>
            </section>

            <section className="mt-8">
              <h2 className="text-base font-semibold text-slate-900">Logs narrativos</h2>
              <div className="mt-3">
                <LogPanel campaignId={id} isMaster={isMaster} />
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
