import { useCallback, useState } from 'react';
import { Upload } from 'lucide-react';
import { SUPPORTED_FILE_TYPES, MAX_FILE_SIZE } from '../../api/types';

interface DocumentUploaderProps {
  onUpload: (file: File) => void;
  loading?: boolean;
}

function DocumentUploader({ onUpload, loading }: DocumentUploaderProps) {
  const [dragOver, setDragOver] = useState(false);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file && validateFile(file)) onUpload(file);
  }, [onUpload]);

  const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && validateFile(file)) onUpload(file);
  }, [onUpload]);

  const validateFile = (file: File): boolean => {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!SUPPORTED_FILE_TYPES.includes(ext)) {
      alert(`不支持的文件类型。支持: ${SUPPORTED_FILE_TYPES.join(', ')}`);
      return false;
    }
    if (file.size > MAX_FILE_SIZE) {
      alert('文件大小超过 50MB 限制');
      return false;
    }
    return true;
  };

  return (
    <div
      onDragOver={e => { e.preventDefault(); setDragOver(true); }}
      onDragLeave={() => setDragOver(false)}
      onDrop={handleDrop}
      className={`
        relative border-2 border-dashed rounded-card p-12 text-center transition-colors cursor-pointer
        ${dragOver
          ? 'border-accent bg-accent-bg'
          : 'border-accent/30 hover:border-accent/50'
        }
        ${loading ? 'pointer-events-none opacity-50' : ''}
      `}
    >
      <input
        type="file"
        accept={SUPPORTED_FILE_TYPES.join(',')}
        onChange={handleFileChange}
        className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
        disabled={loading}
      />
      <div className="flex flex-col items-center gap-3">
        <div className={`p-4 rounded-card bg-surface-secondary`}>
          <Upload className={`w-8 h-8 ${dragOver ? 'text-accent' : 'text-text-tertiary'}`} />
        </div>
        <div>
          <p className="text-[15px] font-medium text-text-primary">
            {loading ? '上传中...' : '拖拽文件到此处，或点击上传'}
          </p>
          <p className="text-[12px] text-text-tertiary mt-1">
            支持 {SUPPORTED_FILE_TYPES.join('、')}，最大 50MB
          </p>
        </div>
      </div>
    </div>
  );
}

export { DocumentUploader };
