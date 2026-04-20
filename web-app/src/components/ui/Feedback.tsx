import { type ReactNode } from 'react';

function Spinner({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const sizes = { sm: 'h-4 w-4', md: 'h-5 w-5', lg: 'h-8 w-8' };
  return (
    <div className={`animate-spin rounded-full border-2 border-gray-200 border-t-accent ${sizes[size]}`} />
  );
}

function Skeleton({ className = '' }: { className?: string }) {
  return (
    <div className={`animate-pulse rounded-input bg-gray-200 dark:bg-gray-200 ${className}`} />
  );
}

function Avatar({ src, name, size = 'md' }: { src?: string; name: string; size?: 'sm' | 'md' | 'lg' }) {
  const sizes = { sm: 'h-8 w-8 text-xs', md: 'h-10 w-10 text-sm', lg: 'h-16 w-16 text-lg' };
  const initials = name.slice(0, 2).toUpperCase();

  return (
    <div className={`${sizes[size]} rounded-full bg-accent text-white flex items-center justify-center font-semibold overflow-hidden`}>
      {src ? <img src={src} alt={name} className="h-full w-full object-cover" /> : initials}
    </div>
  );
}

function EmptyState({ icon, title, description }: { icon?: ReactNode; title: string; description?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="p-4 rounded-card bg-surface-secondary mb-4">
        {icon || (
          <svg className="w-16 h-16 text-text-tertiary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5m0 0H4m16 0a2 2 0 01-2 2H6a2 2 0 01-2-2" />
          </svg>
        )}
      </div>
      <h3 className="text-base font-medium text-text-primary">{title}</h3>
      {description && <p className="mt-1 text-sm text-text-secondary">{description}</p>}
    </div>
  );
}

export { Spinner, Skeleton, Avatar, EmptyState };
