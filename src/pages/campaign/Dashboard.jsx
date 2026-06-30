import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCampaigns } from '../../api/campaigns';
import { parseApiError } from '../../api/parseApiError';
import AppLayout from '../../components/layout/AppLayout';
import CreateCampaignForm from '../../components/campaign/CreateCampaignForm';

function RoleBadge({ role }) {
  const isMaster = role === 'MASTER';
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
        isMaster
          ? 'bg-red-950 text-red-400 border border-red-900'
          : 'bg-zinc-800 text-zinc-400'
      }`}
    >
      {isMaster ? 'Mestre' : 'Jogador'}
    </span>
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
    <AppLayout>
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-white">Minhas campanhas</h2>
        {!creating && (
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
          >
            Nova campanha
          </button>
        )}
      </div>

      {creating && (
        <div className="mt-6">
          <CreateCampaignForm onCreated={handleCreated} onCancel={() => setCreating(false)} />
        </div>
      )}

      <div className="mt-6">
        {loading && <p className="text-sm text-zinc-500">Carregando...</p>}
        {!loading && error && <p className="text-sm text-red-400">{error}</p>}

        {!loading && !error && campaigns.length === 0 && (
          <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-10 text-center">
            <p className="text-2xl">⚔️</p>
            <p className="mt-3 text-sm font-medium text-zinc-300">Nenhuma campanha ainda</p>
            <p className="mt-1 text-sm text-zinc-500">
              Crie a primeira campanha para começar a gerenciar seus NPCs.
            </p>
            <button
              type="button"
              onClick={() => setCreating(true)}
              className="mt-4 rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700"
            >
              Criar campanha
            </button>
          </div>
        )}

        {!loading && !error && campaigns.length > 0 && (
          <ul className="space-y-3">
            {campaigns.map((campaign) => (
              <li key={campaign.id}>
                <Link
                  to={`/campaigns/${campaign.id}`}
                  className="group flex items-center justify-between gap-4 rounded-xl border border-zinc-800 bg-zinc-900 p-5 transition hover:border-red-900 hover:bg-zinc-800"
                >
                  <div className="min-w-0">
                    <h3 className="font-semibold text-white group-hover:text-red-400 transition">
                      {campaign.name}
                    </h3>
                    {campaign.description && (
                      <p className="mt-1 truncate text-sm text-zinc-500">{campaign.description}</p>
                    )}
                  </div>
                  <RoleBadge role={campaign.currentUserRole} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </AppLayout>
  );
}
