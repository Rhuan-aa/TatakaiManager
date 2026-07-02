import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

function AuthLoading() {
  return <div className="min-h-screen bg-zinc-950" />;
}

export function PrivateRoute({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <AuthLoading />;
  return user ? children : <Navigate to="/login" replace />;
}

export function MasterRoute({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <AuthLoading />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'MASTER') return <Navigate to="/dashboard" replace />;
  return children;
}
