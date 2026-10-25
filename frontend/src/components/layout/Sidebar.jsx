import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useTheme } from '../../contexts/ThemeContext';

const svg = (children) => (
  <svg
    width="20"
    height="20"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    className="shrink-0"
  >
    {children}
  </svg>
);

const ICONS = {
  grid: svg(
    <>
      <rect x="3" y="3" width="7" height="7" rx="0" />
      <rect x="14" y="3" width="7" height="7" rx="0" />
      <rect x="3" y="14" width="7" height="7" rx="0" />
      <rect x="14" y="14" width="7" height="7" rx="0" />
    </>
  ),
  sheet: svg(
    <>
      <path d="M6 3h9l4 4v14H6Z" />
      <path d="M14 3v5h5M9 12h7M9 16h7" />
      <circle cx="10" cy="9" r="1" fill="currentColor" />
    </>
  ),
  plus: svg(<path d="M12 5v14M5 12h14" />),
  back: svg(<path d="M19 12H5M12 19l-7-7 7-7" />),
  users: svg(
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c0-4 3.5-6 8-6s8 2 8 6" />
    </>
  ),
  calendar: svg(
    <>
      <rect x="3" y="4" width="18" height="17" rx="2" />
      <path d="M3 9h18M8 2v4M16 2v4" />
    </>
  ),
  swords: svg(
    <>
      <path d="M4 4l9 9M4 4v4l1 1M4 4h4l1 1" />
      <path d="M20 4l-9 9M20 4v4l-1 1M20 4h-4l-1 1" />
      <path d="M9 15l-4 4M15 15l4 4M7 17l-2-2M17 17l2-2" />
    </>
  ),
  scroll: svg(
    <>
      <path d="M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path d="M9 8h6M9 12h6M9 16h4" />
    </>
  ),
  book: svg(
    <>
      <path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5Z" />
      <path d="M4 19a2 2 0 0 1 2-2h13M9 7h6" />
    </>
  ),
  settings: svg(
    <>
      <path d="M4 6h16M4 12h16M4 18h16" />
      <circle cx="9" cy="6" r="2" fill="currentColor" />
      <circle cx="16" cy="12" r="2" fill="currentColor" />
      <circle cx="11" cy="18" r="2" fill="currentColor" />
    </>
  ),
  logout: svg(
    <>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5M21 12H9" />
    </>
  ),
  sun: svg(
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: svg(<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />),
};

// Rótulo que só aparece quando a sidebar está expandida (hover)
const label =
  'overflow-hidden whitespace-nowrap opacity-0 transition-opacity duration-150 group-hover/sb:opacity-100 group-has-[:focus-visible]/sb:opacity-100';
const itemBase =
  'group/item relative flex w-full items-center gap-3 border-2 border-transparent py-2.5 pl-3.5 pr-3 text-sm font-bold uppercase tracking-wide transition-colors focus-visible:outline-[3px] focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]';

function NavItem({ item }) {
  const cls = `${itemBase} ${
    item.active
      ? 'border-[var(--line)] bg-[var(--accent)] text-white shadow-[3px_3px_0_var(--line)]'
      : 'text-[var(--text)] hover:border-[var(--line)] hover:bg-[var(--bg)]'
  }`;
  const inner = (
    <>
      {ICONS[item.icon] ?? ICONS.grid}
      <span className={label}>{item.label}</span>
    </>
  );
  if (item.to) {
    return (
      <Link to={item.to} className={cls}>
        {inner}
      </Link>
    );
  }
  return (
    <button type="button" onClick={item.onClick} className={cls}>
      {inner}
    </button>
  );
}

/**
 * Sidebar lateral retrátil: rail de ícones que expande no hover mostrando os
 * rótulos. `items` é a navegação contextual (ex.: campanhas no dashboard,
 * seções dentro de uma campanha). Itens com `divider: true` viram separador.
 */
export default function Sidebar({ items = [] }) {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <aside className="group/sb fixed inset-y-0 left-0 z-40 flex w-16 flex-col overflow-hidden border-r-[length:var(--border-w)] border-[var(--line)] bg-[var(--panel-bg)] transition-[width] duration-200 ease-out hover:w-60 hover:shadow-[5px_0_0_var(--line)] has-[:focus-visible]:w-60">
      {/* Logo */}
      <Link to="/dashboard" className="flex h-16 shrink-0 items-center gap-3 px-3.5">
        <span className="grid h-9 w-9 shrink-0 place-items-center border-[length:var(--border-w)] border-[var(--line)] bg-[var(--accent)] text-lg font-black text-white shadow-[3px_3px_0_var(--line)]">
          武
        </span>
        <span className={`flex items-baseline gap-1 ${label}`}>
          <span className="panel-title text-xl text-[var(--accent)]">TATAKAI</span>
          <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--text)]">
            Manager
          </span>
        </span>
      </Link>

      {/* Navegação contextual */}
      <nav className="flex-1 space-y-1 px-2.5 py-2">
        {items.map((item, i) =>
          item.divider ? (
            <hr key={`d${i}`} className="my-2 border-t-2 border-[var(--line)]" />
          ) : (
            <NavItem key={item.key ?? item.label} item={item} />
          )
        )}
      </nav>

      {/* Tema + Usuário + sair */}
      <div className="space-y-1 border-t-[length:var(--border-w)] border-[var(--line)] p-2.5">
        <button
          type="button"
          onClick={toggle}
          className={`${itemBase} text-[var(--text)] hover:border-[var(--line)] hover:bg-[var(--bg)]`}
          title={theme === 'dark' ? 'Mudar para tema claro' : 'Mudar para tema escuro'}
        >
          {theme === 'dark' ? ICONS.sun : ICONS.moon}
          <span className={label}>{theme === 'dark' ? 'Tema claro' : 'Tema escuro'}</span>
        </button>
        <div className="flex items-center gap-3 px-1 py-1">
          <span className="grid h-9 w-9 shrink-0 place-items-center border-2 border-[var(--line)] bg-[var(--bg)] text-sm font-bold text-[var(--text)]">
            {user?.name?.charAt(0)?.toUpperCase() ?? '?'}
          </span>
          <span className={`truncate text-sm text-[var(--text)] ${label}`}>{user?.name}</span>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          className={`${itemBase} text-[var(--text)] hover:border-[var(--line)] hover:bg-[var(--accent)] hover:text-white`}
        >
          {ICONS.logout}
          <span className={label}>Sair</span>
        </button>
      </div>
    </aside>
  );
}
