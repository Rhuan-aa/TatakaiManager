import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listCampaigns } from '../api/campaigns';
import { parseApiError } from '../api/parseApiError';
import { useAuth } from '../contexts/AuthContext';
import CreateCampaignForm from '../components/CreateCampaignForm';

function RoleBadge({ role }) {
  const isMaster = role === 'MASTER';
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
        isMaster ? 'bg-purple-100 text-purple-700' : 'bg-slate-100 text-slate-600'
      }`}
    >
      {isMaster ? 'Mestre' : 'Jogador'}
    </span>
  );
}

export default function Dashboard() {
  const { user, logout } = useAuth();
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
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
        <h1 className="text-lg font-semibold text-slate-900">Tatakai Manager</h1>
        <div className="flex items-center gap-4">
          <span className="text-sm text-slate-600">{user?.name}</span>
          <button
            type="button"
            onClick={logout}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            Sair
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-3xl p-6">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold text-slate-900">Minhas campanhas</h2>
          {!creating && (
            <button
              type="button"
              onClick={() => setCreating(true)}
              className="rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700"
            >
              Nova campanha
            </button>
          )}
        </div>

        {creating && (
          <div className="mt-4">
            <CreateCampaignForm onCreated={handleCreated} onCancel={() => setCreating(false)} />
          </div>
        )}

        <div className="mt-6">
          {loading && <p className="text-sm text-slate-500">Carregando...</p>}

          {!loading && error && <p className="text-sm text-red-600">{error}</p>}

          {!loading && !error && campaigns.length === 0 && (
            <p className="text-sm text-slate-500">
              Você ainda não participa de nenhuma campanha. Crie a primeira!
            </p>
          )}

          {!loading && !error && campaigns.length > 0 && (
            <ul className="space-y-3">
              {campaigns.map((campaign) => (
                <li key={campaign.id}>
                  <Link
                    to={`/campaigns/${campaign.id}`}
                    className="block rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition hover:border-purple-300 hover:shadow"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="font-medium text-slate-900">{campaign.name}</h3>
                      <RoleBadge role={campaign.currentUserRole} />
                    </div>
                    {campaign.description && (
                      <p className="mt-1 text-sm text-slate-600">{campaign.description}</p>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </main>
    </div>
  );
}
