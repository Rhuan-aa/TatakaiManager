import Sidebar from './Sidebar';

/**
 * Fundo ambiente: dá profundidade ao zinc-950 chapado com um brilho vermelho
 * sutil no topo e uma malha de grade discreta. Puramente decorativo.
 */
function BackgroundDecor() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 overflow-hidden">
      {/* Brilho de acento no topo */}
      <div className="absolute -top-40 left-1/2 h-96 w-[42rem] -translate-x-1/2 rounded-full bg-red-600/10 blur-[120px]" />
      {/* Vinheta radial para focar o conteúdo (adapta ao tema) */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--vignette),transparent_60%)]" />
      {/* Grade sutil */}
      <div
        className="absolute inset-0 opacity-[0.035]"
        style={{
          backgroundImage:
            'linear-gradient(to right, #71717a 1px, transparent 1px), linear-gradient(to bottom, #71717a 1px, transparent 1px)',
          backgroundSize: '48px 48px',
        }}
      />
    </div>
  );
}

/**
 * Painel de superfície — envolve o conteúdo de uma tela num "surface"
 * translúcido flutuando sobre o fundo (item #1 do handoff). Cards sólidos
 * por dentro ganham profundidade sem conflitar de cor.
 */
export function Surface({ as: Tag = 'section', className = '', children }) {
  return <Tag className={`surface ${className}`}>{children}</Tag>;
}

/** Cabeçalho de página com barra de acento, título display e ações à direita. */
export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="flex items-start gap-3">
        <span className="mt-1 h-8 w-1 shrink-0 rounded-full bg-gradient-to-b from-red-500 to-red-700" />
        <div className="min-w-0">
          <h1 className="text-2xl font-bold tracking-tight text-zinc-50 sm:text-[1.75rem]">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-zinc-400">{subtitle}</p>}
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
      className={`rounded-2xl border border-dashed border-zinc-800 bg-zinc-950/30 p-10 text-center ${className}`}
    >
      {icon && <div className="mb-3 text-3xl">{icon}</div>}
      <p className="text-base font-semibold text-zinc-200">{title}</p>
      {description && (
        <p className="mx-auto mt-1 max-w-sm text-sm text-zinc-500">{description}</p>
      )}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}

/** Placeholder animado no formato de um card de retrato (usado no loading da grade). */
export function SkeletonCard() {
  return (
    <div className="overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900">
      <div className="skeleton aspect-[3/4] w-full rounded-none" />
    </div>
  );
}

/**
 * Shell de aplicação: fundo ambiente + sidebar retrátil + container centrado.
 * Usado por todas as telas autenticadas (Dashboard, CampaignDetail).
 */
export default function AppShell({ items = [], children, maxWidth = 'max-w-6xl' }) {
  return (
    <div className="relative min-h-screen bg-zinc-950 text-zinc-100">
      <BackgroundDecor />
      <Sidebar items={items} />
      <div className="relative pl-16">
        <main className={`mx-auto ${maxWidth} px-4 py-6 sm:px-8 sm:py-10`}>{children}</main>
      </div>
    </div>
  );
}
