import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCampaigns } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import AppShell, { PageHeader, Surface, EmptyState } from '../../components/layout/AppShell';
import CreateCampaignForm from '../../components/campaign/CreateCampaignForm';

/** Placeholder de card de campanha durante o carregamento. */
function CampaignSkeleton() {
  return (
    <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
      <div className="flex items-start justify-between">
        <div className="skeleton h-10 w-10 rounded-lg" />
        <div className="skeleton h-5 w-16 rounded-full" />
      </div>
      <div className="skeleton mt-4 h-4 w-3/4" />
      <div className="skeleton mt-2 h-3 w-full" />
      <div className="skeleton mt-1.5 h-3 w-2/3" />
    </div>
  );
}

function RoleBadge({ role }) {
  const isMaster = role === 'MASTER';
  return (
    <span
      className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold ${
        isMaster
          ? 'border border-red-900 bg-red-950 text-red-400'
          : 'bg-zinc-800 text-zinc-400'
      }`}
    >
      {isMaster ? 'Mestre' : 'Jogador'}
    </span>
  );
}

function CampaignCard({ campaign }) {
  return (
    <Link
      to={`/campaigns/${campaign.id}`}
      className="group relative flex flex-col overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900 p-5 shadow-md shadow-black/20 transition-all duration-200 hover:-translate-y-1 hover:border-zinc-700 hover:shadow-xl hover:shadow-black/50"
    >
      {/* Brilho superior no hover */}
      <span className="pointer-events-none absolute inset-x-0 -top-16 h-32 bg-red-600/0 blur-2xl transition-colors duration-300 group-hover:bg-red-600/10" />
      <div className="flex items-start justify-between gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-zinc-800 text-lg ring-1 ring-white/5 transition-colors group-hover:bg-red-950">
          ⚔️
        </span>
        <RoleBadge role={campaign.currentUserRole} />
      </div>
      <h3 className="mt-3 truncate font-semibold text-zinc-50 transition-colors group-hover:text-red-400">
        {campaign.name}
      </h3>
      <p className="mt-1 line-clamp-2 min-h-[2.5rem] text-sm text-zinc-500">
        {campaign.description || 'Sem descrição.'}
      </p>
      {/* Barra de acento no hover */}
      <span className="absolute inset-x-0 bottom-0 h-0.5 origin-left scale-x-0 bg-gradient-to-r from-red-500 to-red-700 transition-transform duration-200 group-hover:scale-x-100" />
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
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <CampaignSkeleton key={i} />
            ))}
          </div>
        </Surface>
      )}
      {!loading && error && <p className="text-sm text-red-400">{error}</p>}

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
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {campaigns.map((campaign) => (
              <CampaignCard key={campaign.id} campaign={campaign} />
            ))}
          </div>
        </Surface>
      )}
    </AppShell>
  );
}
