import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCampaigns } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import Sidebar from '../../components/layout/Sidebar';
import CreateCampaignForm from '../../components/campaign/CreateCampaignForm';

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
      className="group relative flex flex-col overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900 p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-zinc-700 hover:shadow-lg hover:shadow-black/40"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-zinc-800 text-lg transition-colors group-hover:bg-red-950">
          ⚔️
        </span>
        <RoleBadge role={campaign.currentUserRole} />
      </div>
      <h3 className="mt-3 truncate font-semibold text-white transition-colors group-hover:text-red-400">
        {campaign.name}
      </h3>
      <p className="mt-1 line-clamp-2 min-h-[2.5rem] text-sm text-zinc-500">
        {campaign.description || 'Sem descrição.'}
      </p>
      {/* Barra de acento no hover */}
      <span className="absolute inset-x-0 bottom-0 h-0.5 origin-left scale-x-0 bg-red-600 transition-transform duration-200 group-hover:scale-x-100" />
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
    <div className="min-h-screen bg-zinc-950">
      <Sidebar onNewCampaign={() => setCreating(true)} />

      <div className="pl-16">
        <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8">
          {/* Cabeçalho */}
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-white">Minhas campanhas</h1>
              <p className="mt-1 text-sm text-zinc-500">
                Gerencie NPCs, agendamentos e o tempo de jogo de cada mesa.
              </p>
            </div>
            {!creating && (
              <button
                type="button"
                onClick={() => setCreating(true)}
                className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-red-700"
              >
                + Nova campanha
              </button>
            )}
          </div>

          {creating && (
            <div className="mt-6">
              <CreateCampaignForm onCreated={handleCreated} onCancel={() => setCreating(false)} />
            </div>
          )}

          <div className="mt-8">
            {loading && <p className="text-sm text-zinc-500">Carregando...</p>}
            {!loading && error && <p className="text-sm text-red-400">{error}</p>}

            {!loading && !error && campaigns.length === 0 && !creating && (
              <div className="rounded-2xl border border-dashed border-zinc-800 bg-zinc-900/40 p-12 text-center">
                <p className="text-3xl">⚔️</p>
                <p className="mt-3 text-base font-semibold text-zinc-200">Nenhuma campanha ainda</p>
                <p className="mt-1 text-sm text-zinc-500">
                  Crie a primeira campanha para começar a gerenciar seus NPCs.
                </p>
                <button
                  type="button"
                  onClick={() => setCreating(true)}
                  className="mt-5 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
                >
                  Criar campanha
                </button>
              </div>
            )}

            {!loading && !error && campaigns.length > 0 && (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {campaigns.map((campaign) => (
                  <CampaignCard key={campaign.id} campaign={campaign} />
                ))}
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}
