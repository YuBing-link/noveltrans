import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useTheme } from '../../hooks/useTheme';
import { useState } from 'react';
import { Moon, Sun, ChevronDown, LogOut, User, Settings, Globe } from 'lucide-react';
import { useTranslation } from 'react-i18next';

function Header() {
  const { user, isAuthenticated, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const [showMenu, setShowMenu] = useState(false);
  const [showLangMenu, setShowLangMenu] = useState(false);
  const { t, i18n } = useTranslation();

  const handleLogout = async () => {
    await logout();
    navigate('/');
    setShowMenu(false);
  };

  const handleLangChange = (lang: string) => {
    i18n.changeLanguage(lang);
    setShowLangMenu(false);
  };

  const currentLang = i18n.language === 'zh' ? '中文' : 'English';

  return (
    <header className="sticky top-0 z-40 bg-white/80 dark:bg-gray-50/80 backdrop-blur-xl border-b border-divider dark:border-border">
      <div className="w-full px-6 h-14 flex items-center justify-between">
        {/* Logo + Nav */}
        <div className="flex items-center gap-10">
          <Link to="/" className="text-[15px] font-semibold text-text-primary flex items-center gap-2">
            <svg width="28" height="28" viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg" className="flex-shrink-0">
              <rect x="3" y="4" width="22" height="20" rx="3" stroke="currentColor" strokeWidth="2"/>
              <path d="M10 10H18M10 14H18M10 18H14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
              <path d="M20 10L24 7V23L20 20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="var(--color-accent-bg)"/>
            </svg>
            NovelTrans
          </Link>
          <nav className="hidden md:flex items-center gap-7">
            <Link to="/" className="text-[14px] text-text-secondary hover:text-text-primary transition-colors">
              {t('header.nav.translate')}
            </Link>
            <Link to="/pricing" className="text-[14px] text-text-secondary hover:text-text-primary transition-colors">
              {t('header.nav.pricing')}
            </Link>
            {isAuthenticated && (
              <>
                <Link to="/documents" className="text-[14px] text-text-secondary hover:text-text-primary transition-colors">
                  {t('header.nav.docs')}
                </Link>
                <Link to="/history" className="text-[14px] text-text-secondary hover:text-text-primary transition-colors">
                  {t('header.nav.history')}
                </Link>
                <Link to="/glossary" className="text-[14px] text-text-secondary hover:text-text-primary transition-colors">
                  {t('header.nav.glossary')}
                </Link>
                <Link to="/collab" className="text-[14px] text-text-secondary hover:text-text-primary transition-colors">
                  {t('header.nav.collaboration')}
                </Link>
              </>
            )}
          </nav>
        </div>

        {/* Right side */}
        <div className="flex items-center gap-1">
          {/* Language selector dropdown */}
          <div className="relative">
            <button
              onClick={() => setShowLangMenu(!showLangMenu)}
              className="flex items-center gap-1 px-2 py-1.5 rounded-full text-text-secondary hover:text-text-primary hover:bg-gray-100 transition-button"
            >
              <Globe className="w-[15px] h-[15px]" />
              <span className="text-[13px]">{currentLang}</span>
              <ChevronDown className="w-3 h-3 text-text-tertiary" />
            </button>

            {showLangMenu && (
              <>
                <div className="fixed inset-0 z-40" onClick={() => setShowLangMenu(false)} />
                <div className="absolute right-0 top-full mt-2 w-40 rounded-card bg-white dark:bg-gray-50 shadow-elevated dark:border dark:border-border py-1 z-50 animate-fade-in">
                  <button
                    onClick={() => handleLangChange('zh')}
                    className={`w-full px-4 py-2.5 text-[13px] text-left hover:bg-gray-100 ${i18n.language === 'zh' ? 'text-accent font-medium' : 'text-text-secondary'}`}
                  >
                    中文
                  </button>
                  <button
                    onClick={() => handleLangChange('en')}
                    className={`w-full px-4 py-2.5 text-[13px] text-left hover:bg-gray-100 ${i18n.language === 'en' ? 'text-accent font-medium' : 'text-text-secondary'}`}
                  >
                    English
                  </button>
                </div>
              </>
            )}
          </div>

          {/* Theme toggle */}
          <button
            onClick={toggleTheme}
            className="p-2 rounded-full text-text-secondary hover:text-text-primary hover:bg-gray-100 transition-button"
            aria-label={t('header.theme.toggle')}
          >
            {theme === 'dark' ? <Sun className="w-[15px] h-[15px]" /> : <Moon className="w-[15px] h-[15px]" />}
          </button>

          {isAuthenticated ? (
            <div className="relative">
              <button
                onClick={() => setShowMenu(!showMenu)}
                className="flex items-center gap-2 px-3 py-1.5 rounded-button hover:bg-gray-100 transition-button"
              >
                <div className="w-7 h-7 rounded-full bg-accent text-white flex items-center justify-center text-xs font-semibold">
                  {user?.username?.slice(0, 1).toUpperCase() || 'U'}
                </div>
                <span className="hidden sm:block text-[13px] text-text-primary max-w-24 truncate">
                  {user?.username || t('header.user.userCenter')}
                </span>
                <ChevronDown className="w-3 h-3 text-text-tertiary" />
              </button>

              {showMenu && (
                <>
                  <div className="fixed inset-0 z-40" onClick={() => setShowMenu(false)} />
                  <div className="absolute right-0 top-full mt-2 w-48 rounded-card bg-white dark:bg-gray-50 shadow-elevated dark:border dark:border-border py-1 z-50 animate-fade-in">
                    <Link
                      to="/user"
                      onClick={() => setShowMenu(false)}
                      className="flex items-center gap-2 px-4 py-2.5 text-[13px] text-text-secondary hover:bg-gray-100"
                    >
                      <User className="w-3.5 h-3.5" /> {t('header.user.userCenter')}
                    </Link>
                    <Link
                      to="/settings"
                      onClick={() => setShowMenu(false)}
                      className="flex items-center gap-2 px-4 py-2.5 text-[13px] text-text-secondary hover:bg-gray-100"
                    >
                      <Settings className="w-3.5 h-3.5" /> {t('header.user.settings')}
                    </Link>
                    <hr className="my-1 border-divider dark:border-border" />
                    <button
                      onClick={handleLogout}
                      className="flex items-center gap-2 w-full px-4 py-2.5 text-[13px] text-red hover:bg-red-bg"
                    >
                      <LogOut className="w-3.5 h-3.5" /> {t('header.user.logout')}
                    </button>
                  </div>
                </>
              )}
            </div>
          ) : (
            <Link to="/login" className="text-[13px] font-medium text-blue px-4 py-1.5 hover:underline">
              {t('header.user.login')}
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}

export { Header };
