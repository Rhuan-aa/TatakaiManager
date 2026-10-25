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
import CatalogManager from '../../components/catalog/CatalogManager';
import RuleSettings from '../../components/campaign/RuleSettings';
import RollFeed from '../../components/rolls/RollFeed';
import CombatPanel from '../../components/combat/CombatPanel';
import SheetsSection from '../../components/sheet/SheetsSection';

export default function CampaignDetail() {
  const { id } = useParams();
  const [campaign, setCampaign] = useState(null);
  const [npcs, setNpcs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState('NPCs');
  const [fightSheetIds, setFightSheetIds] = useState(undefined);

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
    { key: 'Fichas', label: 'Fichas', icon: 'sheet', active: activeTab === 'Fichas', onClick: () => { setFightSheetIds(undefined); setActiveTab('Fichas'); } },
    { key: 'Agenda', label: 'Agenda', icon: 'calendar', active: activeTab === 'Agenda', onClick: () => setActiveTab('Agenda') },
    { key: 'Combate', label: 'Combate', icon: 'calendar', active: activeTab === 'Combate', onClick: () => setActiveTab('Combate') },
    { key: 'Rolagens', label: 'Rolagens', icon: 'scroll', active: activeTab === 'Rolagens', onClick: () => setActiveTab('Rolagens') },
    { key: 'Logs', label: 'Logs', icon: 'scroll', active: activeTab === 'Logs', onClick: () => setActiveTab('Logs') },
    { key: 'Catálogo', label: 'Catálogo', icon: 'book', active: activeTab === 'Catálogo', onClick: () => setActiveTab('Catálogo') },
    ...(isMaster
      ? [{ key: 'Configurações', label: 'Configurações', icon: 'settings', active: activeTab === 'Configurações', onClick: () => setActiveTab('Configurações') }]
      : []),
  ];

  const TAB_META = {
    NPCs: { label: 'NPCs', hint: 'Fichas e acervo desta campanha.' },
    Fichas: { label: 'Fichas', hint: 'Fichas dos personagens dos jogadores.' },
    Agenda: { label: 'Agenda', hint: 'TimeSkips, tempo de jogo e grade de slots.' },
    Combate: { label: 'Combate', hint: 'Iniciativa, turnos, ações e condições.' },
    Rolagens: { label: 'Rolagens', hint: 'Rolagens da mesa em tempo real.' },
    Logs: { label: 'Logs', hint: 'Registro narrativo da campanha.' },
    Catálogo: { label: 'Catálogo', hint: 'Traços e clãs do sistema e da campanha.' },
    Configurações: { label: 'Configurações', hint: 'Membros e ajustes da mesa.' },
  };
  const meta = TAB_META[activeTab] ?? TAB_META.NPCs;

  return (
    <AppShell items={sidebarItems}>
      {loading && (
        <div className="space-y-6">
          <div className="flex items-start gap-3">
            <span className="mt-1 h-9 w-2 shrink-0 bg-[var(--line)]" />
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
      {!loading && error && <p className="caption" role="alert">{error}</p>}

      {!loading && !error && campaign && (
        <div className="space-y-6">
          {/* Cabeçalho da campanha */}
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-start gap-3">
              <span className="mt-1 h-9 w-2 shrink-0 bg-[var(--accent)]" />
              <div className="min-w-0">
                <h1 className="panel-title truncate text-3xl sm:text-4xl">
                  {campaign.name}
                </h1>
                {campaign.description && (
                  <p className="mt-1 text-sm opacity-80">{campaign.description}</p>
                )}
              </div>
            </div>
            <span className={`tag mt-1 shrink-0 ${isMaster ? 'tag-accent' : ''}`}>
              {isMaster ? 'Mestre' : 'Jogador'}
            </span>
          </div>

          {/* Seção ativa, dentro de uma superfície */}
          <Surface className="p-5 sm:p-6">
            <div className="mb-5 border-b-[3px] border-[var(--line)] pb-4">
              <h2 className="panel-title text-2xl">{meta.label}</h2>
              <p className="mt-0.5 text-sm opacity-80">{meta.hint}</p>
            </div>

            {activeTab === 'NPCs' && (
              <NpcSection campaignId={id} isMaster={isMaster} npcs={npcs} setNpcs={setNpcs} />
            )}
            {activeTab === 'Fichas' && <SheetsSection campaignId={id} isMaster={isMaster} fightSheetIds={fightSheetIds} />}
            {activeTab === 'Agenda' && (
              <TimeSkipPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
            )}
            {activeTab === 'Combate' && <CombatPanel campaignId={id} isMaster={isMaster} npcs={npcs}
              onCloseFight={(ids) => { setFightSheetIds(ids); setActiveTab('Fichas'); }} />}
            {activeTab === 'Rolagens' && <RollFeed campaignId={id} isMaster={isMaster} />}
            {activeTab === 'Logs' && (
              <LogPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
            )}
            {activeTab === 'Catálogo' && <CatalogManager campaignId={id} isMaster={isMaster} />}
            {activeTab === 'Configurações' && isMaster && (
              <div>
                <p className="caption">
                  Adicione um jogador já cadastrado pelo e-mail.
                </p>
                <div className="mt-4 max-w-md">
                  <InviteMemberForm campaignId={id} />
                </div>
                <h3 className="panel-title mt-8 text-lg">Regras opcionais</h3>
                <div className="mt-3">
                  <RuleSettings campaignId={id} isMaster={isMaster} />
                </div>
              </div>
            )}
          </Surface>
        </div>
      )}
    </AppShell>
  );
}
