import Sidebar from './Sidebar';

/**
 * Fundo decorativo de página de quadrinho: retícula + linhas de velocidade
 * em cantos opostos, longe do texto. Puramente decorativo.
 */
function BackgroundDecor() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 overflow-hidden">
      <div className="halftone halftone-fade absolute right-0 top-0 h-[32rem] w-[32rem]" />
      <div className="speed-lines absolute bottom-0 left-0 h-[28rem] w-[28rem]" style={{ '--burst-at': '0% 100%', '--burst-clear': '0%' }} />
    </div>
  );
}

/**
 * Painel de superfície — envolve o conteúdo de uma tela num "surface"
 * de quadrinho (borda grossa + sombra dura).
 */
export function Surface({ as: Tag = 'section', className = '', children }) {
  return <Tag className={`panel ${className}`}>{children}</Tag>;
}

/** Cabeçalho de página com barra de acento, título display e ações à direita. */
export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-gutter mb-6 flex flex-wrap items-end justify-between border-b-[length:var(--border-w)] border-[var(--line)] pb-3">
      <div className="flex items-start gap-3">
        <span aria-hidden className="mt-1 h-8 w-3 shrink-0 border-2 border-[var(--line)] bg-[var(--accent)]" />
        <div className="min-w-0">
          <h1 className="panel-title text-3xl text-[var(--text)] sm:text-4xl">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-[var(--text)] opacity-80">{subtitle}</p>}
        </div>
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

/** Estado vazio padrão: card tracejado com ícone, mensagem e ação opcional. */
export function EmptyState({ icon, title, description, action, className = '' }) {
  return (
    <div
      className={`panel border-dashed p-10 text-center ${className}`}
    >
      {icon && <div className="mb-3 text-3xl">{icon}</div>}
      <p className="panel-title text-xl text-[var(--text)]">{title}</p>
      {description && (
        <p className="mx-auto mt-1 max-w-sm text-sm text-[var(--text)] opacity-75">{description}</p>
      )}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}

/** Placeholder animado no formato de um card de retrato (usado no loading da grade). */
export function SkeletonCard() {
  return (
    <div className="panel overflow-hidden">
      <div className="skeleton aspect-[3/4] w-full rounded-none" />
    </div>
  );
}

/**
 * Shell de aplicação: fundo decorativo + sidebar retrátil + container centrado.
 * Usado por todas as telas autenticadas (Dashboard, CampaignDetail).
 */
export default function AppShell({ items = [], children, maxWidth = 'max-w-6xl' }) {
  return (
    <div className="relative min-h-screen bg-[var(--bg)] text-[var(--text)]">
      <BackgroundDecor />
      <Sidebar items={items} />
      <div className="relative pl-16">
        <main className={`mx-auto ${maxWidth} px-4 py-6 sm:px-8 sm:py-10`}>{children}</main>
      </div>
    </div>
  );
}
