import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-[var(--bg)] px-4 text-center">
      <div aria-hidden className="speed-lines pointer-events-none absolute inset-0 opacity-30" />
      <div className="relative">
        <p aria-hidden className="onomatopoeia text-8xl sm:text-9xl">404!</p>
        <div className="panel mx-auto mt-6 max-w-md p-6">
          <h1 className="panel-title text-3xl">Página não encontrada</h1>
          <p className="mt-2 text-sm">
            O endereço que você acessou não existe ou foi movido.
          </p>
          <Link to="/dashboard" className="btn-primary mt-6 inline-block">
            Voltar ao início
          </Link>
        </div>
      </div>
    </div>
  );
}
