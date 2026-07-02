import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import client from '../../api/client';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';

const inputClass = 'field';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const { data } = await client.post('/auth/login', form);
      login(data.token, { userId: data.userId, name: data.name, email: data.email });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(parseApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-zinc-950 px-4">
      {/* Fundo ambiente */}
      <div aria-hidden className="pointer-events-none absolute inset-0">
        <div className="absolute -top-32 left-1/2 h-96 w-[36rem] -translate-x-1/2 rounded-full bg-red-600/15 blur-[120px]" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,var(--vignette),transparent_60%)]" />
      </div>

      <div className="relative w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <span className="grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br from-red-500 to-red-700 text-2xl font-black text-white shadow-xl shadow-red-900/50 ring-1 ring-red-400/30">
            武
          </span>
          <div className="mt-4 flex items-baseline gap-1.5">
            <span className="text-3xl font-black tracking-tight text-red-500">TATAKAI</span>
          </div>
          <p className="mt-1 text-xs font-semibold uppercase tracking-[0.25em] text-zinc-500">
            Manager
          </p>
        </div>

        <div className="surface p-8">
          <h1 className="text-lg font-semibold text-zinc-50">Entrar</h1>
          <p className="mt-1 text-sm text-zinc-500">Acesse sua conta.</p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-zinc-400">
                E-mail
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={form.email}
                onChange={handleChange}
                className={inputClass}
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-zinc-400">
                Senha
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={form.password}
                onChange={handleChange}
                className={inputClass}
              />
            </div>

            {error && <p className="text-sm text-red-400">{error}</p>}

            <button type="submit" disabled={submitting} className="btn-primary w-full">
              {submitting ? 'Entrando...' : 'Entrar'}
            </button>
          </form>
        </div>

        <p className="mt-5 text-center text-sm text-zinc-500">
          Não tem conta?{' '}
          <Link to="/register" className="font-medium text-red-400 hover:text-red-300">
            Cadastre-se
          </Link>
        </p>
      </div>
    </div>
  );
}
