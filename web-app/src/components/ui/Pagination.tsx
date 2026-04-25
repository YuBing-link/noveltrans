import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  windowSize?: number;
}

function Pagination({ page, totalPages, onPageChange, windowSize = 5 }: PaginationProps) {
  if (totalPages <= 1) return null;

  const startPage = Math.max(1, Math.min(page - Math.floor(windowSize / 2), totalPages - windowSize + 1));

  return (
    <div className="flex items-center justify-center gap-2 px-6 py-4 border-t border-border bg-surface-secondary">
      <button
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
        className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
      </button>

      {Array.from({ length: Math.min(windowSize, totalPages) }, (_, i) => {
        const p = startPage + i;
        if (p > totalPages) return null;
        return (
          <button
            key={`page-${p}`}
            onClick={() => onPageChange(p)}
            className={`min-w-[32px] h-8 px-2 text-[13px] font-medium rounded-lg transition-all ${
              p === page
                ? 'bg-accent text-white shadow-sm'
                : 'text-text-tertiary hover:text-text-primary hover:bg-surface-secondary'
            }`}
          >
            {p}
          </button>
        );
      })}

      <button
        disabled={page >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
      >
        <ChevronRight className="w-4 h-4" />
      </button>
    </div>
  );
}

export { Pagination };
export type { PaginationProps };
