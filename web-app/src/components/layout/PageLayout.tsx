import { type ReactNode } from 'react';

interface PageLayoutProps {
  children: ReactNode;
  className?: string;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
}

const maxWidthClasses: Record<string, string> = {
  sm: 'max-w-2xl',
  md: 'max-w-3xl',
  lg: 'max-w-5xl',
  xl: 'max-w-7xl',
  full: 'max-w-full',
};

function PageLayout({ children, className = '', maxWidth = 'xl' }: PageLayoutProps) {
  return (
    <div className={`${maxWidthClasses[maxWidth]} mx-auto px-4 sm:px-6 ${className}`}>
      {children}
    </div>
  );
}

export { PageLayout };
