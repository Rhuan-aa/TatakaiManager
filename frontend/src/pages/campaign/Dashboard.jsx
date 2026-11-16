import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCampaigns } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import AppShell, { PageHeader, Surface, EmptyState } from '../../components/layout/AppShell';
import CreateCampaignForm from '../../components/campaign/CreateCampaignForm';
import Icon from '../../components/ui/Icon';

/** Placeholder de card de campanha durante o carregamento. */
function CampaignSkeleton() {
  return (
    <div className="panel p-5">
      <div className="flex items-start justify-between">
        <div className="skeleton h-10 w-10" />
        <div className="skeleton h-5 w-16" />
      </div>
      <div className="skeleton mt-4 h-4 w-3/4" />
      <div className="skeleton mt-2 h-3 w-full" />
      <div className="skeleton mt-1.5 h-3 w-2/3" />
    </div>
  );
}

function RoleBadge({ role }) {
  const isMaster = role === 'MASTER';
  return <span className={`tag shrink-0 ${isMaster ? 'tag-accent' : ''}`}>{isMaster ? 'Mestre' : 'Jogador'}</span>;
}

function CampaignCard({ campaign }) {
  return (
    <Link
      to={`/campaigns/${campaign.id}`}
      className="panel campaign-card group flex flex-col overflow-hidden"
    >
      <div className="flex items-center justify-between gap-3 border-b-[length:var(--border-w)] border-[var(--line)] bg-[var(--ink)] px-4 py-2 text-[var(--paper)]">
        <Icon name="swords" size={22} />
        <RoleBadge role={campaign.currentUserRole} />
      </div>
      <div className="p-4">
        <h3 className="title-stamp break-words text-2xl">{campaign.name}</h3>
        <p className="line-clamp-3 mt-3 min-h-[3.75rem] text-sm opacity-85">
          {campaign.description || 'Sem descrição.'}
        </p>
      </div>
    </Link>
  );
}

export default function Dashboard() {
  const [campaigns, setCampaigns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const data = await listCampaigns();
        if (active) setCampaigns(data);
      } catch (err) {
        if (active) setError(parseApiError(err).message);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  function handleCreated(campaign) {
    setCampaigns((prev) => [...prev, campaign]);
    setCreating(false);
  }

  return (
    <AppShell
      items={[
        { key: 'campaigns', label: 'Campanhas', icon: 'grid', to: '/dashboard', active: true },
        { key: 'new', label: 'Nova campanha', icon: 'plus', onClick: () => setCreating(true) },
      ]}
    >
      <PageHeader
        title="Minhas campanhas"
        subtitle="Gerencie NPCs, agendamentos e o tempo de jogo de cada mesa."
        actions={
          !creating && (
            <button type="button" onClick={() => setCreating(true)} className="btn-primary">
              + Nova campanha
            </button>
          )
        }
      />

      {creating && (
        <div className="mb-6">
          <CreateCampaignForm onCreated={handleCreated} onCancel={() => setCreating(false)} />
        </div>
      )}

      {loading && (
        <Surface className="p-5 sm:p-6">
          <div className="page-gutter grid sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <CampaignSkeleton key={i} />
            ))}
          </div>
        </Surface>
      )}
      {!loading && error && <p className="caption" role="alert">{error}</p>}

      {!loading && !error && campaigns.length === 0 && !creating && (
        <EmptyState
          icon="swords"
          title="Nenhuma campanha ainda"
          description="Crie a primeira campanha para começar a gerenciar seus NPCs."
          action={
            <button type="button" onClick={() => setCreating(true)} className="btn-primary">
              Criar campanha
            </button>
          }
        />
      )}

      {!loading && !error && campaigns.length > 0 && (
        <Surface className="p-5 sm:p-6">
          <div className="page-gutter grid sm:grid-cols-2 lg:grid-cols-3">
            {campaigns.map((campaign) => (
              <CampaignCard key={campaign.id} campaign={campaign} />
            ))}
          </div>
        </Surface>
      )}
    </AppShell>
  );
}
