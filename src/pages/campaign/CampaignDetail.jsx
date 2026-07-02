import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getCampaign } from '../../api/campaigns';
import { listCampaignNpcs } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import Sidebar from '../../components/layout/Sidebar';
import InviteMemberForm from '../../components/campaign/InviteMemberForm';
import NpcSection from '../../components/npc/NpcSection';
import TimeSkipPanel from '../../components/timeskip/TimeSkipPanel';
import LogPanel from '../../components/log/LogPanel';

export default function CampaignDetail() {
  const { id } = useParams();
  const [campaign, setCampaign] = useState(null);
  const [npcs, setNpcs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState('NPCs');

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

  const sidebarItems = [
    { key: 'back', label: 'Campanhas', icon: 'back', to: '/dashboard' },
    { divider: true },
    { key: 'NPCs', label: 'NPCs', icon: 'users', active: activeTab === 'NPCs', onClick: () => setActiveTab('NPCs') },
    { key: 'Agenda', label: 'Agenda', icon: 'calendar', active: activeTab === 'Agenda', onClick: () => setActiveTab('Agenda') },
    { key: 'Logs', label: 'Logs', icon: 'scroll', active: activeTab === 'Logs', onClick: () => setActiveTab('Logs') },
    ...(isMaster
      ? [{ key: 'Config', label: 'Configurações', icon: 'settings', active: activeTab === 'Configurações', onClick: () => setActiveTab('Configurações') }]
      : []),
  ];

  return (
    <div className="min-h-screen bg-zinc-950">
      <Sidebar items={sidebarItems} />

      <div className="pl-16">
        <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8">
          {loading && <p className="text-sm text-zinc-500">Carregando...</p>}
          {!loading && error && <p className="text-sm text-red-400">{error}</p>}

          {!loading && !error && campaign && (
            <div className="space-y-6">
              {/* Cabeçalho */}
              <div className="flex items-start justify-between gap-4 border-b border-zinc-800 pb-5">
                <div className="min-w-0">
                  <h1 className="truncate text-2xl font-bold tracking-tight text-white">
                    {campaign.name}
                  </h1>
                  {campaign.description && (
                    <p className="mt-1 text-sm text-zinc-400">{campaign.description}</p>
                  )}
                </div>
                <span
                  className={`mt-1 shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                    isMaster
                      ? 'border border-red-900 bg-red-950 text-red-400'
                      : 'bg-zinc-800 text-zinc-400'
                  }`}
                >
                  {isMaster ? 'Mestre' : 'Jogador'}
                </span>
              </div>

              {/* Seção ativa */}
              {activeTab === 'NPCs' && (
                <NpcSection campaignId={id} isMaster={isMaster} npcs={npcs} setNpcs={setNpcs} />
              )}
              {activeTab === 'Agenda' && (
                <TimeSkipPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
              )}
              {activeTab === 'Logs' && (
                <LogPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
              )}
              {activeTab === 'Configurações' && isMaster && (
                <div className="max-w-md rounded-xl border border-zinc-800 bg-zinc-900 p-6">
                  <h2 className="text-base font-semibold text-white">Convidar jogador</h2>
                  <p className="mt-1 text-sm text-zinc-500">
                    Adicione um jogador já cadastrado pelo e-mail.
                  </p>
                  <div className="mt-4">
                    <InviteMemberForm campaignId={id} />
                  </div>
                </div>
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
