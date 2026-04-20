import { NavLink } from 'react-router-dom';
import { User, BarChart3, Gauge, Key, Settings } from 'lucide-react';

const navItems = [
  { path: '/user', icon: User, label: '个人信息' },
  { path: '/user/stats', icon: BarChart3, label: '统计数据' },
  { path: '/user/quota', icon: Gauge, label: '配额用量' },
  { path: '/user/api-keys', icon: Key, label: 'API Keys' },
  { path: '/user/preferences', icon: Settings, label: '偏好设置' },
];

function Sidebar() {
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
