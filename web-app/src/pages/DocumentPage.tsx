import { useState, useCallback, useEffect } from 'react';
import { useToast } from '../components/ui/Toast';
import { documentApi } from '../api/documents';
import type { DocumentItem } from '../api/types';
import { FileText, Download, Trash2, RefreshCw, XCircle, ChevronDown } from 'lucide-react';
import { SUPPORTED_LANGUAGES } from '../api/types';

function DocumentPage() {
  const { success, error: toastError } = useToast();
  const [sourceLang, setSourceLang] = useState('auto');
  const [targetLang, setTargetLang] = useState('zh');
  const [mode, setMode] = useState('fast');
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [uploading, setUploading] = useState(false);
  const [loading, setLoading] = useState(false);

  const hasActiveTasks = documents.some(
    doc => doc.status === 'pending' || doc.status === 'processing'
  );

  useEffect(() => {
    if (!hasActiveTasks) return;
    const interval = setInterval(loadDocuments, 3000);
    return () => clearInterval(interval);
  }, [hasActiveTasks]);

  useEffect(() => { loadDocuments(); }, []);

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const { data } = await documentApi.getList({ page: 1, pageSize: 20 });
      setDocuments(data.list || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleUpload = useCallback(async (file: File) => {
    setUploading(true);
    try {
      await documentApi.upload(file, { sourceLang, targetLang, mode });
      success('文件上传成功，开始翻译');
      loadDocuments();
    } catch (err) {
      toastError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
  }, [sourceLang, targetLang]);

  const handleDelete = async (docId: number) => {
    try { await documentApi.delete(docId); success('已删除'); loadDocuments(); }
    catch { toastError('删除失败'); }
  };

  const handleDownload = async (docId: number) => {
    try {
      const blob = await documentApi.download(docId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a'); a.href = url; a.download = `translated_${docId}`; a.click();
      URL.revokeObjectURL(url);
      success('下载成功');
    } catch (e) {
      const msg = e instanceof Error ? e.message : '下载失败';
      toastError(msg.includes('401') ? '请先登录' : msg.includes('404') ? '文件不存在' : msg);
    }
  };

  const handleRetry = async (docId: number) => {
    try { await documentApi.retry(docId); success('已重新提交翻译'); loadDocuments(); }
    catch { toastError('重试失败'); }
  };

  const handleCancel = async (docId: number) => {
    try { await documentApi.cancel(docId); success('已取消'); loadDocuments(); }
    catch { toastError('取消失败'); }
  };

  const statusLabel: Record<string, string> = {
    pending: '排队中', processing: '翻译中', completed: '已完成',
    failed: '失败', cancelled: '已取消',
  };

  return (
    <div className="w-full" style={{ minHeight: 'calc(100vh - 200px)' }}>
      <div className="border border-border/50 rounded-lg overflow-hidden flex flex-col" style={{ minHeight: 'calc(100vh - 200px)' }}>
        {/* Language bar */}
        <div className="flex items-center gap-3 px-5 py-3 border-b border-border/50">
          <select
            value={sourceLang}
            onChange={e => setSourceLang(e.target.value)}
            className="appearance-none bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors"
          >
            {SUPPORTED_LANGUAGES.map(lang => (
              <option key={lang.code} value={lang.code}>{lang.name}</option>
            ))}
          </select>
          <select
            value={targetLang}
            onChange={e => setTargetLang(e.target.value)}
            className="appearance-none bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors"
          >
            {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(lang => (
              <option key={lang.code} value={lang.code}>{lang.name}</option>
            ))}
          </select>
          <div className="ml-auto flex items-center gap-2">
            <span className="text-[12px] text-text-tertiary">模式:</span>
            <select
              value={mode}
              onChange={e => setMode(e.target.value)}
              className="appearance-none bg-transparent text-text-primary text-[13px] cursor-pointer hover:text-accent transition-colors"
            >
              <option value="fast">快速翻译</option>
              <option value="expert">专家翻译</option>
              <option value="team">团队协作</option>
            </select>
          </div>
        </div>

        {/* Upload area */}
        <div className="p-6 border-b border-border/50">
          <div
            onDragOver={e => { e.preventDefault(); e.currentTarget.classList.add('border-accent'); }}
            onDragLeave={e => { e.currentTarget.classList.remove('border-accent'); }}
            onDrop={e => { e.preventDefault(); e.currentTarget.classList.remove('border-accent'); if (e.dataTransfer.files[0]) handleUpload(e.dataTransfer.files[0]); }}
            className="border-2 border-dashed border-border/50 rounded-lg p-8 text-center hover:border-accent/50 transition-colors cursor-pointer"
            onClick={() => { const input = document.createElement('input'); input.type = 'file'; input.accept = '.txt,.epub,.docx,.pdf'; input.onchange = () => { if (input.files?.[0]) handleUpload(input.files[0]); }; input.click(); }}
          >
            <p className="text-[14px] text-text-tertiary mb-2">拖拽文件到此处或点击上传</p>
            <p className="text-[12px] text-text-placeholder">支持 TXT、EPUB、DOCX、PDF</p>
          </div>
          {uploading && <p className="text-center text-[13px] text-accent mt-3">上传中...</p>}
        </div>

        {/* Document list */}
        <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="text-center py-12 text-text-tertiary text-[13px]">加载中...</div>
        ) : documents.length === 0 ? (
          <div className="text-center py-12 text-text-tertiary text-[13px]">暂无文档</div>
        ) : (
          <div className="divide-y divide-border/50">
            {documents.map(doc => (
              <div key={doc.id} className="px-5 py-4 flex items-center gap-4">
                <FileText className="w-4 h-4 text-text-tertiary flex-shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-[13px] font-medium text-text-primary truncate">{doc.name}</p>
                  {doc.errorMessage && <p className="text-[12px] text-red mt-0.5">{doc.errorMessage}</p>}
                </div>
                <div className="flex items-center gap-2 flex-shrink-0 text-[12px] text-text-tertiary">
                  {(doc.status === 'pending' || doc.status === 'processing') && (
                    <span className="px-2 py-0.5 bg-accent-bg text-accent rounded-full">{statusLabel[doc.status]}</span>
                  )}
                  {doc.status === 'completed' && (
                    <span className="px-2 py-0.5 bg-green-bg text-green rounded-full">{statusLabel[doc.status]}</span>
                  )}
                  {doc.status === 'failed' && (
                    <span className="px-2 py-0.5 bg-red-bg text-red rounded-full">{statusLabel[doc.status]}</span>
                  )}
                  <span className="text-text-placeholder">{doc.status === 'pending' || doc.status === 'processing' ? `${doc.progress || 0}%` : ''}</span>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  {doc.status === 'completed' && (
                    <button onClick={() => handleDownload(doc.id)} className="p-1 text-text-tertiary hover:text-accent"><Download className="w-4 h-4" /></button>
                  )}
                  {(doc.status === 'pending' || doc.status === 'processing') && (
                    <button onClick={() => handleCancel(doc.id)} className="p-1 text-text-tertiary hover:text-red"><XCircle className="w-4 h-4" /></button>
                  )}
                  {doc.status === 'failed' && (
                    <button onClick={() => handleRetry(doc.id)} className="p-1 text-text-tertiary hover:text-accent"><RefreshCw className="w-4 h-4" /></button>
                  )}
                  <button onClick={() => handleDelete(doc.id)} className="p-1 text-text-tertiary hover:text-red"><Trash2 className="w-4 h-4" /></button>
                </div>
              </div>
            ))}
          </div>
        )}
        </div>

        {/* Bottom bar */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-border/50 bg-surface-secondary">
          <span className="text-[12px] text-text-tertiary">{documents.length} 个文档</span>
        </div>
      </div>
    </div>
  );
}

export { DocumentPage };
