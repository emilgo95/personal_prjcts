import { Navigate, Route, Routes } from 'react-router-dom';
import { Box, CircularProgress } from '@mui/material';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import FarmsPage from './pages/FarmsPage';
import FarmFormPage from './pages/FarmFormPage';
import AuditsPage from './pages/AuditsPage';
import AuditWizardPage from './pages/AuditWizardPage';
import AuditDetailPage from './pages/AuditDetailPage';
import StandardsPage from './pages/StandardsPage';
import RegulationsPage from './pages/RegulationsPage';
import DocumentCenterPage from './pages/DocumentCenterPage';
import TracePage from './pages/TracePage';
import UsersPage from './pages/UsersPage';
import PrivacyPage from './pages/PrivacyPage';

function FullscreenLoader() {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', height: '100vh' }}>
      <CircularProgress />
    </Box>
  );
}

export default function App() {
  const { user, loading, hasRole } = useAuth();

  if (loading) return <FullscreenLoader />;

  if (!user) {
    return (
      <Routes>
        <Route path="/privacy" element={hasRole('ADMIN') ? <PrivacyPage /> : <Navigate to="/" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/farms" element={<FarmsPage />} />
        <Route path="/farms/new" element={hasRole('ADMIN', 'AUDITOR') ? <FarmFormPage /> : <Navigate to="/farms" replace />} />
        <Route path="/farms/:id" element={<FarmFormPage />} />
        <Route path="/audits" element={<AuditsPage />} />
        <Route path="/audits/new" element={hasRole('ADMIN', 'AUDITOR') ? <AuditWizardPage /> : <Navigate to="/audits" replace />} />
        <Route path="/audits/:id" element={<AuditDetailPage />} />
        <Route path="/documents" element={<DocumentCenterPage />} />
        <Route path="/standards" element={<StandardsPage />} />
        <Route path="/regulations" element={<RegulationsPage />} />
        <Route path="/trace" element={hasRole('ADMIN', 'QUALITY') ? <TracePage /> : <Navigate to="/" replace />} />
        <Route path="/users" element={hasRole('ADMIN') ? <UsersPage /> : <Navigate to="/" replace />} />
        <Route path="/login" element={<Navigate to="/" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}
