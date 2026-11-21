import { useFxPref } from '../ui/fxPref';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useTheme } from '../../contexts/ThemeContext';
import Icon from '../ui/Icon';

// Rótulo que só aparece quando a sidebar está expandida (hover)
const label =
  'overflow-hidden whitespace-nowrap opacity-0 transition-opacity duration-150 group-hover/sb:opacity-100 group-has-[:focus-visible]/sb:opacity-100';
const itemBase =
  'group/item relative flex w-full items-center gap-3 border-2 border-transparent py-2.5 pl-3.5 pr-3 text-sm font-bold uppercase tracking-wide transition-colors focus-visible:outline-[3px] focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]';

function NavItem({ item }) {
  const cls = `${itemBase} ${
    item.active
      ? 'border-[var(--line)] bg-[var(--accent)] font-[family-name:var(--font-display)] text-white shadow-[3px_3px_0_var(--line)]'
      : 'text-[var(--text)] hover:border-[var(--line)] hover:bg-[var(--bg)]'
  }`;
  const inner = (
    <>
      <Icon name={item.icon} />
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
  const [fxReduced, toggleFx] = useFxPref();
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
          <Icon name={theme === 'dark' ? 'sun' : 'moon'} />
          <span className={label}>{theme === 'dark' ? 'Tema claro' : 'Tema escuro'}</span>
        </button>
        <button
          type="button"
          onClick={toggleFx}
          aria-pressed={fxReduced}
          className={`${itemBase} text-[var(--text)] hover:border-[var(--line)] hover:bg-[var(--bg)]`}
          title={fxReduced ? 'Ligar animações de condições e golpes' : 'Reduzir animações de condições e golpes'}
        >
          <Icon name={fxReduced ? 'bolt' : 'circle'} />
          <span className={label}>{fxReduced ? 'Efeitos completos' : 'Efeitos reduzidos'}</span>
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
          <Icon name="logout" />
          <span className={label}>Sair</span>
        </button>
      </div>
    </aside>
  );
}
