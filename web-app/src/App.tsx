import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { ToastProvider } from './components/ui/Toast';
import { Header } from './components/layout/Header';
import { Footer } from './components/layout/Footer';
import { useAuth } from './hooks/useAuth';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DocumentPage } from './pages/DocumentPage';
import { HistoryPage } from './pages/HistoryPage';
import { GlossaryPage } from './pages/GlossaryPage';
import { UserCenterPage } from './pages/UserCenterPage';
import { SettingsPage } from './pages/SettingsPage';
import { CollabPage } from './pages/CollabPage';
import { AboutPage, HelpPage, PrivacyPage, TermsPage, NotFoundPage } from './pages/StaticPages';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
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
      <Route path="/documents" element={<ProtectedRoute><DocumentPage /></ProtectedRoute>} />
      <Route path="/history" element={<ProtectedRoute><HistoryPage /></ProtectedRoute>} />
      <Route path="/glossary" element={<ProtectedRoute><GlossaryPage /></ProtectedRoute>} />
      <Route path="/user/*" element={<ProtectedRoute><UserCenterPage /></ProtectedRoute>} />
      <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
      <Route path="/collab/*" element={<ProtectedRoute><CollabPage /></ProtectedRoute>} />
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
            <div className="flex flex-col min-h-screen bg-background">
              <Header />
              <main className="flex-1 flex flex-col items-center">
                <div className="w-full max-w-5xl">
                  <AppRoutes />
                </div>
              </main>
              <Footer />
            </div>
          </ToastProvider>
        </ThemeProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
