import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getCampaign } from '../../api/campaigns';
import { listCampaignNpcs } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import AppLayout from '../../components/layout/AppLayout';
import InviteMemberForm from '../../components/campaign/InviteMemberForm';
import NpcSection from '../../components/npc/NpcSection';
import TimeSkipPanel from '../../components/timeskip/TimeSkipPanel';
import LogPanel from '../../components/log/LogPanel';

const TABS_MASTER = ['NPCs', 'Agenda', 'Logs', 'Configurações'];
const TABS_PLAYER = ['NPCs', 'Agenda', 'Logs'];

function TabBar({ tabs, active, onChange }) {
  return (
    <div className="flex gap-1 border-b border-zinc-800">
      {tabs.map((tab) => (
        <button
          key={tab}
          type="button"
          onClick={() => onChange(tab)}
          className={`px-4 py-2.5 text-sm font-medium transition-colors ${
            active === tab
              ? 'border-b-2 border-red-500 text-white'
              : 'text-zinc-500 hover:text-zinc-300'
          }`}
        >
          {tab}
        </button>
      ))}
    </div>
  );
}

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
  const tabs = isMaster ? TABS_MASTER : TABS_PLAYER;

  return (
    <AppLayout backTo="/dashboard" backLabel="Campanhas">
      {loading && <p className="text-sm text-zinc-500">Carregando...</p>}
      {!loading && error && <p className="text-sm text-red-400">{error}</p>}

      {!loading && !error && campaign && (
        <div className="space-y-6">
          {/* Cabeçalho */}
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-white">{campaign.name}</h1>
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

          {/* Abas */}
          <div>
            <TabBar tabs={tabs} active={activeTab} onChange={setActiveTab} />

            <div className="mt-6">
              {activeTab === 'NPCs' && (
                <NpcSection
                  campaignId={id}
                  isMaster={isMaster}
                  npcs={npcs}
                  setNpcs={setNpcs}
                />
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
          </div>
        </div>
      )}
    </AppLayout>
  );
}
