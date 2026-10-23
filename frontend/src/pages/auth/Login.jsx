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
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[var(--bg)] px-4">
      {/* Fundo decorativo (sem texto por cima) */}
      <div aria-hidden className="pointer-events-none absolute inset-0">
        <div className="halftone halftone-fade absolute inset-0" style={{ '--fade-from': 'top center' }} />
        <div
          className="speed-lines absolute inset-0 opacity-25"
          style={{ '--burst-clear': '38%', maskImage: 'radial-gradient(circle at 50% 50%, transparent 0 38%, #000 50%)', WebkitMaskImage: 'radial-gradient(circle at 50% 50%, transparent 0 38%, #000 50%)' }}
        />
      </div>

      <div className="relative w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <span className="panel grid h-14 w-14 place-items-center bg-[var(--accent)] text-2xl font-black text-white">
            武
          </span>
          <div className="mt-4 flex items-baseline gap-1.5">
            <span className="panel-title text-4xl text-[var(--accent)]">TATAKAI</span>
          </div>
          <p className="caption mt-2 inline-block">Manager</p>
        </div>

        <div className="panel p-8">
          <h1 className="panel-title text-2xl">Entrar</h1>
          <p className="mt-1 text-sm opacity-80">Acesse sua conta.</p>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
            <div>
              <label htmlFor="email" className="block text-sm font-bold uppercase tracking-wide">
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
              <label htmlFor="password" className="block text-sm font-bold uppercase tracking-wide">
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

            {error && <p className="caption" role="alert">{error}</p>}

            <button type="submit" disabled={submitting} className="btn-primary w-full">
              {submitting ? 'Entrando...' : 'Entrar'}
            </button>
          </form>
        </div>

        <p className="mt-5 text-center text-sm">
          Não tem conta?{' '}
          <Link to="/register" className="font-bold uppercase text-[var(--accent)] underline decoration-[3px] underline-offset-4">
            Cadastre-se
          </Link>
        </p>
      </div>
    </div>
  );
}
