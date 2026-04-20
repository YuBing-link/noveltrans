import { forwardRef, type InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, className = '', ...props }, ref) => {
    return (
      <div className="flex flex-col gap-1.5">
        {label && (
          <label className="text-sm font-medium text-text-secondary">
            {label}
          </label>
        )}
        <input
          ref={ref}
          className={`
            w-full px-4 py-2.5 text-sm
            bg-surface-secondary
            text-text-primary
            placeholder:text-text-placeholder
            border border-border
            rounded-input
            transition-button
            focus:outline-none focus:bg-white focus:border-accent focus:ring-0
            disabled:opacity-50 disabled:cursor-not-allowed
            ${error ? 'border-red bg-red-bg' : ''}
            ${className}
          `}
          {...props}
        />
        {error && <p className="text-xs text-red">{error}</p>}
      </div>
    );
  }
);

Input.displayName = 'Input';
export { Input };
export type { InputProps };
