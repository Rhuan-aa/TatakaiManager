import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCampaigns } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import AppShell, { PageHeader, Surface, EmptyState } from '../../components/layout/AppShell';
import CreateCampaignForm from '../../components/campaign/CreateCampaignForm';

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
      className="panel group flex flex-col p-5 transition-transform duration-150 hover:-translate-y-1"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center border-2 border-[var(--line)] text-lg">
          ⚔️
        </span>
        <RoleBadge role={campaign.currentUserRole} />
      </div>
      <h3 className="panel-title mt-3 truncate text-xl group-hover:text-[var(--accent)]">
        {campaign.name}
      </h3>
      <p className="mt-1 line-clamp-2 min-h-[2.5rem] text-sm opacity-80">
        {campaign.description || 'Sem descrição.'}
      </p>
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
          icon="⚔️"
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
