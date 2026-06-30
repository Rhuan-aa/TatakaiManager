import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-950 px-4 text-center">
      <p className="text-6xl font-black text-red-600">404</p>
      <h1 className="mt-4 text-xl font-semibold text-white">Página não encontrada</h1>
      <p className="mt-2 text-sm text-zinc-400">
        O endereço que você acessou não existe ou foi movido.
      </p>
      <Link
        to="/dashboard"
        className="mt-6 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
      >
        Voltar ao início
      </Link>
    </div>
  );
}
