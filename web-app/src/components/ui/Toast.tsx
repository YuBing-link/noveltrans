import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { CheckCircle, XCircle, Info, AlertTriangle } from 'lucide-react';

type ToastType = 'success' | 'error' | 'info' | 'warning';

interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  toast: (type: ToastType, message: string) => void;
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastContextType | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  let nextId = 0;

  const addToast = useCallback((type: ToastType, message: string) => {
    const id = ++nextId;
    setToasts(prev => [...prev, { id, type, message }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
  }, []);

  const removeToast = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  const success = useCallback((m: string) => addToast('success', m), [addToast]);
  const error = useCallback((m: string) => addToast('error', m), [addToast]);
  const info = useCallback((m: string) => addToast('info', m), [addToast]);

  const typeStyles: Record<ToastType, string> = {
    success: 'bg-green-bg text-green border-l-4 border-green',
    error: 'bg-red-bg text-red border-l-4 border-red',
    info: 'bg-accent-bg text-accent border-l-4 border-accent',
    warning: 'bg-yellow-bg text-yellow border-l-4 border-yellow',
  };

  const typeIcons: Record<ToastType, typeof CheckCircle> = {
    success: CheckCircle,
    error: XCircle,
    info: Info,
    warning: AlertTriangle,
  };

  return (
    <ToastContext.Provider value={{ toast: addToast, success, error, info }}>
      {children}
      <div className="fixed top-4 right-4 z-50 flex flex-col gap-2 max-w-sm animate-fade-in">
        {toasts.map(t => {
          const Icon = typeIcons[t.type];
          return (
            <div
              key={t.id}
              className={`flex items-center gap-3 px-4 py-3 rounded-card border shadow-elevated text-sm ${typeStyles[t.type]}`}
            >
              <Icon className={`w-4 h-4 flex-shrink-0 ${
                t.type === 'success' ? 'text-green' :
                t.type === 'error' ? 'text-red' :
                t.type === 'warning' ? 'text-yellow' : 'text-accent'
              }`} />
              <span className="flex-1">{t.message}</span>
              <button onClick={() => removeToast(t.id)} className="ml-1 opacity-60 hover:opacity-100 transition-colors">&times;</button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
