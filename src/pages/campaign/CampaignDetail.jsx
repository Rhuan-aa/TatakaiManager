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

function SectionCard({ title, children }) {
  return (
    <section className="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
      <h2 className="text-base font-semibold text-white">{title}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}

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
    <AppLayout backTo="/dashboard" backLabel="Campanhas">
      {loading && <p className="text-sm text-zinc-500">Carregando...</p>}
      {!loading && error && <p className="text-sm text-red-400">{error}</p>}

      {!loading && !error && campaign && (
        <div className="space-y-6">
          {/* Cabeçalho da campanha */}
          <div>
            <div className="flex items-start justify-between gap-4">
              <h1 className="text-2xl font-bold text-white">{campaign.name}</h1>
              <span
                className={`mt-1 shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                  isMaster
                    ? 'bg-red-950 text-red-400 border border-red-900'
                    : 'bg-zinc-800 text-zinc-400'
                }`}
              >
                {isMaster ? 'Mestre' : 'Jogador'}
              </span>
            </div>
            {campaign.description && (
              <p className="mt-2 text-sm text-zinc-400">{campaign.description}</p>
            )}
          </div>

          {/* Convidar jogador (só Mestre) */}
          {isMaster && (
            <SectionCard title="Convidar jogador">
              <p className="mb-3 text-sm text-zinc-500">
                Adicione um jogador já cadastrado pelo e-mail.
              </p>
              <InviteMemberForm campaignId={id} />
            </SectionCard>
          )}

          {/* NPCs */}
          <SectionCard title="NPCs">
            <NpcSection
              campaignId={id}
              isMaster={isMaster}
              npcs={npcs}
              setNpcs={setNpcs}
            />
          </SectionCard>

          {/* TimeSkips */}
          <SectionCard title="Agenda (TimeSkips)">
            <TimeSkipPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
          </SectionCard>

          {/* Logs */}
          <SectionCard title="Logs narrativos">
            <LogPanel campaignId={id} isMaster={isMaster} npcs={npcs} />
          </SectionCard>
        </div>
      )}
    </AppLayout>
  );
}
