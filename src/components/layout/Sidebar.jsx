import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

function IconGrid() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="shrink-0">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  );
}

function IconPlus() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="shrink-0">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function IconLogout() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5M21 12H9" />
    </svg>
  );
}

// Rótulo que só aparece quando a sidebar está expandida (hover)
const label =
  'overflow-hidden whitespace-nowrap opacity-0 transition-opacity duration-150 group-hover/sb:opacity-100';
const item =
  'flex items-center gap-3 rounded-lg py-2.5 pl-3.5 pr-3 text-sm font-medium transition-colors';

export default function Sidebar({ onNewCampaign }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const onCampaigns = pathname.startsWith('/dashboard');

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <aside className="group/sb fixed inset-y-0 left-0 z-40 flex w-16 flex-col overflow-hidden border-r border-zinc-800 bg-zinc-900/95 backdrop-blur transition-[width] duration-200 ease-out hover:w-60 hover:shadow-2xl hover:shadow-black/50">
      {/* Logo */}
      <Link to="/dashboard" className="flex h-16 shrink-0 items-center gap-3 px-3.5">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-red-600 text-lg font-black text-white shadow-sm shadow-red-900/40">
          武
        </span>
        <span className={`flex items-baseline gap-1 ${label}`}>
          <span className="text-base font-black tracking-tight text-red-500">TATAKAI</span>
          <span className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500">
            Manager
          </span>
        </span>
      </Link>

      {/* Navegação */}
      <nav className="flex-1 space-y-1 px-2.5 py-2">
        <Link
          to="/dashboard"
          className={`${item} ${
            onCampaigns
              ? 'bg-red-950/50 text-red-400'
              : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100'
          }`}
        >
          <IconGrid />
          <span className={label}>Campanhas</span>
        </Link>
        <button
          type="button"
          onClick={onNewCampaign}
          className={`${item} w-full text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100`}
        >
          <IconPlus />
          <span className={label}>Nova campanha</span>
        </button>
      </nav>

      {/* Usuário + sair */}
      <div className="space-y-1 border-t border-zinc-800 p-2.5">
        <div className="flex items-center gap-3 px-1 py-1">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-zinc-800 text-sm font-semibold text-zinc-300">
            {user?.name?.charAt(0)?.toUpperCase() ?? '?'}
          </span>
          <span className={`truncate text-sm text-zinc-300 ${label}`}>{user?.name}</span>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          className={`${item} w-full text-zinc-500 hover:bg-zinc-800 hover:text-red-400`}
        >
          <IconLogout />
          <span className={label}>Sair</span>
        </button>
      </div>
    </aside>
  );
}
