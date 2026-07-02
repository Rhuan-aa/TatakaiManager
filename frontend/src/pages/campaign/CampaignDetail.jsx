import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getCampaign } from '../../api/campaigns';
import { listCampaignNpcs } from '../../api/npcs';
import { parseApiError } from '../../api/parseApiError';
import AppShell, { Surface } from '../../components/layout/AppShell';
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

  const TAB_META = {
    NPCs: { label: 'NPCs', hint: 'Fichas e acervo desta campanha.' },
    Agenda: { label: 'Agenda', hint: 'TimeSkips, tempo de jogo e grade de slots.' },
    Logs: { label: 'Logs', hint: 'Registro narrativo da campanha.' },
    Configurações: { label: 'Configurações', hint: 'Membros e ajustes da mesa.' },
  };
  const meta = TAB_META[activeTab] ?? TAB_META.NPCs;

  return (
    <AppShell items={sidebarItems}>
      {loading && (
        <div className="space-y-6">
          <div className="flex items-start gap-3">
            <span className="mt-1 h-9 w-1 shrink-0 rounded-full bg-zinc-800" />
            <div className="flex-1">
              <div className="skeleton h-7 w-64" />
              <div className="skeleton mt-2 h-4 w-40" />
            </div>
          </div>
          <Surface className="space-y-4 p-5 sm:p-6">
            <div className="skeleton h-5 w-24" />
            <div className="skeleton h-4 w-full" />
            <div className="skeleton h-4 w-5/6" />
            <div className="skeleton h-4 w-2/3" />
          </Surface>
        </div>
      )}
      {!loading && error && <p className="text-sm text-red-400">{error}</p>}

      {!loading && !error && campaign && (
        <div className="space-y-6">
          {/* Cabeçalho da campanha */}
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-start gap-3">
              <span className="mt-1 h-9 w-1 shrink-0 rounded-full bg-gradient-to-b from-red-500 to-red-700" />
              <div className="min-w-0">
                <h1 className="truncate text-2xl font-bold tracking-tight text-zinc-50 sm:text-[1.75rem]">
                  {campaign.name}
                </h1>
                {campaign.description && (
                  <p className="mt-1 text-sm text-zinc-400">{campaign.description}</p>
                )}
              </div>
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

          {/* Seção ativa, dentro de uma superfície */}
          <Surface className="p-5 sm:p-6">
            <div className="mb-5 border-b border-zinc-800/80 pb-4">
              <h2 className="text-base font-semibold text-zinc-50">{meta.label}</h2>
              <p className="mt-0.5 text-sm text-zinc-500">{meta.hint}</p>
            </div>

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
              <div className="max-w-md">
                <p className="text-sm text-zinc-500">
                  Adicione um jogador já cadastrado pelo e-mail.
                </p>
                <div className="mt-4">
                  <InviteMemberForm campaignId={id} />
                </div>
              </div>
            )}
          </Surface>
        </div>
      )}
    </AppShell>
  );
}
