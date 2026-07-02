import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';

export default function AppLayout({ children, backTo, backLabel }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="min-h-screen bg-zinc-950">
      <header className="sticky top-0 z-10 border-b border-zinc-800 bg-zinc-950/90 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-6 py-3">
          <div className="flex items-center gap-4">
            <Link to="/dashboard" className="flex items-baseline gap-1.5">
              <span className="text-lg font-black tracking-tight text-red-500">TATAKAI</span>
              <span className="text-xs font-semibold uppercase tracking-widest text-zinc-500">
                Manager
              </span>
            </Link>
            {backTo && (
              <>
                <span className="text-zinc-700">/</span>
                <Link to={backTo} className="text-sm text-zinc-400 hover:text-zinc-200">
                  {backLabel}
                </Link>
              </>
            )}
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-zinc-400">{user?.name}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-md border border-zinc-700 px-3 py-1.5 text-xs font-medium text-zinc-400 hover:border-zinc-600 hover:text-zinc-200"
            >
              Sair
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">{children}</main>
    </div>
  );
}
