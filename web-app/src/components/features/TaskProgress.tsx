import { Badge } from '../ui/Badge';

interface TaskProgressProps {
  progress: number;
  status: string;
  fileName?: string;
}

const statusLabel: Record<string, string> = {
  pending: '排队中',
  processing: '翻译中',
  completed: '已完成',
  failed: '失败',
  cancelled: '已取消',
};

const statusColor: Record<string, 'blue' | 'green' | 'red' | 'gray'> = {
  pending: 'gray',
  processing: 'blue',
  completed: 'green',
  failed: 'red',
  cancelled: 'gray',
};

function TaskProgress({ progress, status, fileName }: TaskProgressProps) {
  return (
    <div className="space-y-2">
      {fileName && <p className="text-[13px] font-medium text-text-primary truncate">{fileName}</p>}
      <div className="flex items-center justify-between text-[13px]">
        <Badge color={statusColor[status] || 'gray'}>
          {statusLabel[status] || status}
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
