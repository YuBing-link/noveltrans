import { type ReactNode } from 'react';

interface BadgeProps {
  children: ReactNode;
  color?: 'blue' | 'red' | 'green' | 'gray' | 'yellow' | 'purple' | 'orange';
  className?: string;
}

const colorClasses: Record<string, string> = {
  blue: 'bg-accent-bg text-accent',
  red: 'bg-badge-red-bg text-badge-red-text',
  green: 'bg-badge-green-bg text-badge-green-text',
  gray: 'bg-badge-gray-bg text-badge-gray-text',
  yellow: 'bg-badge-yellow-bg text-badge-yellow-text',
  purple: 'bg-badge-purple-bg text-badge-purple-text',
  orange: 'bg-badge-orange-bg text-badge-orange-text',
};

function Badge({ children, color = 'blue', className = '' }: BadgeProps) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colorClasses[color]} ${className}`}>
      {children}
    </span>
  );
}

export { Badge };
