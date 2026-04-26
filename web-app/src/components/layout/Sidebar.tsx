import { NavLink } from 'react-router-dom';
import { User, BarChart3, Gauge, Key, Settings, CreditCard } from 'lucide-react';
import { useTranslation } from 'react-i18next';

function Sidebar() {
  const { t } = useTranslation();
  const navItems = [
    { path: '/user', icon: User, label: t('userCenter.tabs.profile') },
    { path: '/user/stats', icon: BarChart3, label: t('userCenter.tabs.stats') },
    { path: '/user/quota', icon: Gauge, label: t('userCenter.tabs.quota') },
    { path: '/user/subscription', icon: CreditCard, label: t('userCenter.tabs.subscription') },
    { path: '/user/api-keys', icon: Key, label: 'API Keys' },
    { path: '/user/preferences', icon: Settings, label: t('userCenter.tabs.preferences') },
  ];

  return (
    <nav className="flex flex-col gap-1">
      {navItems.map(item => (
        <NavLink
          key={item.path}
          to={item.path}
          end={item.path === '/user'}
          className={({ isActive }) =>
            `flex items-center gap-3 px-3 py-2 rounded-button text-[13px] transition-colors ${
              isActive
                ? 'bg-accent/10 text-accent font-medium'
                : 'text-text-secondary hover:bg-surface-secondary'
            }`
          }
        >
          <item.icon className="w-4 h-4" />
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

export { Sidebar };
