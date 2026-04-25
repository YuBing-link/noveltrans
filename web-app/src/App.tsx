import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { ToastProvider } from './components/ui/Toast';
import { Layout } from './components/layout/Layout';
import { useAuth } from './hooks/useAuth';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { DocumentPage } from './pages/DocumentPage';
import { HistoryPage } from './pages/HistoryPage';
import { GlossaryPage } from './pages/GlossaryPage';
import { CollabPage } from './pages/CollabPage';
import { CollabWorkspace } from './pages/CollabWorkspace';
import { UserCenterPage } from './pages/UserCenterPage';
import { PricingPage } from './pages/PricingPage';
import { PaymentResultPage } from './pages/PaymentResultPage';
import { SettingsPage } from './pages/SettingsPage';
import { AboutPage, HelpPage, PrivacyPage, TermsPage, NotFoundPage } from './pages/StaticPages';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) return null;
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: window.location.pathname }} replace />;
  }
  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/documents" element={<ProtectedRoute><DocumentPage /></ProtectedRoute>} />
      <Route path="/history" element={<ProtectedRoute><HistoryPage /></ProtectedRoute>} />
      <Route path="/glossary" element={<ProtectedRoute><GlossaryPage /></ProtectedRoute>} />
      <Route path="/collab/workspace" element={<ProtectedRoute><CollabWorkspace /></ProtectedRoute>} />
      <Route path="/collab" element={<ProtectedRoute><CollabPage /></ProtectedRoute>} />
      <Route path="/user/*" element={<ProtectedRoute><UserCenterPage /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
      <Route path="/pricing" element={<PricingPage />} />
      <Route path="/subscription/result" element={<PaymentResultPage />} />
      <Route path="/about" element={<AboutPage />} />
      <Route path="/help" element={<HelpPage />} />
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route path="/terms" element={<TermsPage />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ThemeProvider>
          <ToastProvider>
            <Layout>
              <AppRoutes />
            </Layout>
          </ToastProvider>
        </ThemeProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
