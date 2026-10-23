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
      <div className="flex min-h-screen flex-col items-center justify-center px-4 text-center" style={{ background: 'var(--bg)' }}>
        <div className="panel max-w-md p-6">
          <span className="caption">Erro</span>
          <h1 className="mt-4 text-3xl">Algo deu errado</h1>
          <p className="mt-2 text-sm">
            A tela travou por um erro inesperado. Tente recarregar — se persistir, sua
            sessão pode estar desatualizada.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <button type="button" onClick={() => window.location.reload()} className="btn-primary">
              Recarregar
            </button>
            <button
              type="button"
              onClick={() => {
                localStorage.removeItem('token');
                localStorage.removeItem('user');
                window.location.href = '/login';
              }}
              className="btn-secondary"
            >
              Sair e entrar de novo
            </button>
          </div>
        </div>
      </div>
    );
  }
}
