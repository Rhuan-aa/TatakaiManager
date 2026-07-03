import { Component } from 'react';

/**
 * Rede de segurança para erros de render não tratados: sem isso, uma exceção em
 * qualquer componente desmonta a árvore inteira do React e deixa só o body vazio
 * (fundo escuro sem nada — a "tela preta"). Mostra uma mensagem recuperável em vez
 * de deixar a tela em branco.
 */
export class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('Erro não tratado na interface:', error, info);
  }

  render() {
    if (!this.state.error) return this.props.children;

    return (
      <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-950 px-4 text-center">
        <p className="text-5xl">⚠️</p>
        <h1 className="mt-4 text-xl font-semibold text-zinc-50">Algo deu errado</h1>
        <p className="mt-2 max-w-sm text-sm text-zinc-400">
          A tela travou por um erro inesperado. Tente recarregar — se persistir, sua
          sessão pode estar desatualizada.
        </p>
        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700"
          >
            Recarregar
          </button>
          <button
            type="button"
            onClick={() => {
              localStorage.removeItem('token');
              localStorage.removeItem('user');
              window.location.href = '/login';
            }}
            className="rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-zinc-300 hover:bg-zinc-800"
          >
            Sair e entrar de novo
          </button>
        </div>
      </div>
    );
  }
}
