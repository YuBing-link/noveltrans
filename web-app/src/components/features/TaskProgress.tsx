import { Badge } from '../ui/Badge';
import { useTranslation } from 'react-i18next';

interface TaskProgressProps {
  progress: number;
  status: string;
  fileName?: string;
}

function TaskProgress({ progress, status, fileName }: TaskProgressProps) {
  const { t } = useTranslation();

  const statusColor: Record<string, 'blue' | 'green' | 'red' | 'gray'> = {
    pending: 'gray',
    processing: 'blue',
    completed: 'green',
    failed: 'red',
    cancelled: 'gray',
  };

  return (
    <div className="space-y-2">
      {fileName && <p className="text-[13px] font-medium text-text-primary truncate">{fileName}</p>}
      <div className="flex items-center justify-between text-[13px]">
        <Badge color={statusColor[status] || 'gray'}>
          {t(`taskProgress.${status}`, { defaultValue: status })}
        </Badge>
        <span className="text-text-tertiary">{Math.round(progress)}%</span>
      </div>
      <div className="w-full bg-gray-100 rounded-full h-2 overflow-hidden">
        <div
          className="h-full bg-accent transition-all duration-300 rounded-full"
          style={{ width: `${progress}%` }}
        />
      </div>
    </div>
  );
}

export { TaskProgress };
