import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import client from '../../api/client';
import { parseApiError } from '../../api/parseApiError';
import { useAuth } from '../../contexts/AuthContext';

export default function Register() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      const { data } = await client.post('/auth/register', form);
      login(data.token, { userId: data.userId, name: data.name, email: data.email });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      const parsed = parseApiError(err);
      setError(parsed.message);
      setFieldErrors(parsed.fields);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Criar conta</h1>
        <p className="mt-1 text-sm text-slate-500">Cadastre-se no Tatakai Manager.</p>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-slate-700">
              Nome
            </label>
            <input
              id="name"
              name="name"
              type="text"
              autoComplete="name"
              required
              value={form.name}
              onChange={handleChange}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
            />
            {fieldErrors.name && <p className="mt-1 text-xs text-red-600">{fieldErrors.name}</p>}
          </div>

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-700">
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
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
            />
            {fieldErrors.email && <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>}
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
              Senha
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="new-password"
              required
              value={form.password}
              onChange={handleChange}
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
            />
            {fieldErrors.password && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.password}</p>
            )}
          </div>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-purple-600 px-3 py-2 text-sm font-medium text-white hover:bg-purple-700 disabled:opacity-60"
          >
            {submitting ? 'Criando...' : 'Criar conta'}
          </button>
        </form>

        <p className="mt-4 text-center text-sm text-slate-500">
          Já tem conta?{' '}
          <Link to="/login" className="font-medium text-purple-600 hover:underline">
            Entrar
          </Link>
        </p>
      </div>
    </div>
  );
}
