import type { SelectHTMLAttributes, ReactNode } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  children: ReactNode;
}

function Select({ label, className = '', children, ...props }: SelectProps) {
  return (
    <div className="flex flex-col gap-1.5">
      {label && <label className="text-sm font-medium text-text-secondary">{label}</label>}
      <select
        className={`
          w-full px-4 py-2.5 text-sm
          bg-surface-secondary
          text-text-primary
          border border-border
          rounded-input
          transition-button
          focus:outline-none focus:bg-white focus:border-accent focus:ring-0
          appearance-none
          ${className}
        `}
        {...props}
      >
        {children}
      </select>
    </div>
  );
}

export { Select };
