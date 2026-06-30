import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { PrivateRoute } from './routes/PrivateRoute';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import Dashboard from './pages/campaign/Dashboard';
import CampaignDetail from './pages/campaign/CampaignDetail';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/dashboard"
            element={
              <PrivateRoute>
                <Dashboard />
              </PrivateRoute>
            }
          />
          <Route
            path="/campaigns/:id"
            element={
              <PrivateRoute>
                <CampaignDetail />
              </PrivateRoute>
            }
          />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
