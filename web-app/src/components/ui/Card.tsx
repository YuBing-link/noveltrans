import { type ReactNode, type HTMLAttributes } from 'react';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  variant?: 'default' | 'subtle' | 'elevated';
  hoverable?: boolean;
}

const variantShadow: Record<string, string> = {
  default: 'shadow-card',
  subtle: 'shadow-subtle',
  elevated: 'shadow-elevated',
};

function Card({ children, variant = 'default', hoverable = false, className = '', ...props }: CardProps) {
  return (
    <div
      className={`
        rounded-card bg-white dark:bg-gray-50 p-6 border border-border/50
        ${variantShadow[variant]}
        ${hoverable ? 'transition-card hover:shadow-card-hover' : ''}
        ${className}
      `}
      {...props}
    >
      {children}
    </div>
  );
}

export { Card };
