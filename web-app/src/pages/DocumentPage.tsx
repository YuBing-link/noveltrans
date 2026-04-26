import { useState, useCallback, useEffect } from 'react';
import { useToast } from '../components/ui/Toast';
import { Pagination } from '../components/ui/Pagination';
import { documentApi } from '../api/documents';
import type { DocumentItem } from '../api/types';
import { FileText, Download, Trash2, RefreshCw, XCircle, Upload, Clock, Languages, HardDrive } from 'lucide-react';
import { SUPPORTED_LANGUAGES } from '../api/types';

function DocumentPage() {
  const { success, error: toastError } = useToast();
  const [sourceLang, setSourceLang] = useState('auto');
  const [targetLang, setTargetLang] = useState('zh');
  const [mode, setMode] = useState('fast');
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [uploading, setUploading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);

  const hasActiveTasks = documents.some(
    doc => doc.status === 'pending' || doc.status === 'processing'
  );

  useEffect(() => {
    if (!hasActiveTasks) return;
    const interval = setInterval(loadDocuments, 3000);
    return () => clearInterval(interval);
  }, [hasActiveTasks]);

  useEffect(() => { loadDocuments(); }, []);
  useEffect(() => { loadDocuments(); }, [page]);
  useEffect(() => { setPage(1); }, [sourceLang, targetLang, mode]);

  const loadDocuments = async () => {
    setLoading(true);
    try {
      const { data } = await documentApi.getList({ page, pageSize: 20 });
      setDocuments(data.list || []);
      setTotalPages(Math.ceil((data.total || 0) / 20) || 1);
      setTotal(data.total || 0);
    } catch (err) {
      console.warn('加载文档列表失败:', err);
    }
    finally { setLoading(false); }
  };

  const handleUpload = useCallback(async (file: File) => {
    // 验证文件类型
    const allowedTypes = ['.txt', '.epub', '.docx', '.pdf'];
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!allowedTypes.includes(ext)) {
      toastError(`不支持的文件类型: ${ext}，仅支持 ${allowedTypes.join(', ')}`);
      return;
    }
    
    // 验证文件大小（限制 50MB）
    const maxSize = 50 * 1024 * 1024;
    if (file.size > maxSize) {
      toastError(`文件过大: ${(file.size / 1024 / 1024).toFixed(1)}MB，最大支持 50MB`);
      return;
    }

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
  }, [sourceLang, targetLang, mode]);

  const handleDelete = async (docId: number) => {
    try { await documentApi.delete(docId); success('已删除'); loadDocuments(); }
    catch { toastError('删除失败'); }
  };

  const handleDownload = async (doc: DocumentItem) => {
    try {
      const blob = await documentApi.download(doc.id);
      const ext = doc.fileType || '.' + (doc.fileName?.split('.').pop() || 'txt');
      const baseName = doc.fileName?.replace(/\.[^.]+$/, '') || `translated_${doc.id}`;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a'); a.href = url; a.download = `${baseName}_translated${ext}`; a.click();
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

  const statusColor: Record<string, string> = {
    pending: 'bg-yellow-bg text-yellow',
    processing: 'bg-accent-bg text-accent',
    completed: 'bg-green-bg text-green',
    failed: 'bg-red-bg text-red',
    cancelled: 'bg-gray-100 text-text-tertiary',
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  };

  const getLangName = (code: string) => {
    const lang = SUPPORTED_LANGUAGES.find(l => l.code === code);
    return lang ? lang.name : code;
  };

  return (
    <div className="py-8">
      <div className="mb-6">
        <h1 className="text-[28px] font-bold text-text-primary mb-2 tracking-tight">
          文档翻译
        </h1>
        <p className="text-[15px] text-text-secondary">
          支持批量上传、多格式文档智能翻译，保留原文排版格式
        </p>
      </div>

      <div className="border border-border rounded-xl shadow-sm bg-surface overflow-hidden">
        {/* Language and mode bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3 px-6 py-4 border-b border-border bg-surface-secondary/50">
          <div className="flex items-center gap-3 flex-1">
            <select
              value={sourceLang}
              onChange={e => setSourceLang(e.target.value)}
              className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none border-none"
            >
              {SUPPORTED_LANGUAGES.map(lang => (
                <option key={lang.code} value={lang.code}>{lang.name}</option>
              ))}
            </select>
            <select
              value={targetLang}
              onChange={e => setTargetLang(e.target.value)}
              className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none border-none"
            >
              {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(lang => (
                <option key={lang.code} value={lang.code}>{lang.name}</option>
              ))}
            </select>
          </div>
          
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <span className="text-[12px] text-text-tertiary whitespace-nowrap">翻译模式:</span>
            <select
              value={mode}
              onChange={e => setMode(e.target.value)}
              className="bg-transparent text-text-primary text-[13px] cursor-pointer hover:text-accent transition-colors focus:outline-none border-none flex-1 sm:flex-initial"
            >
              <option value="fast">快速翻译</option>
              <option value="expert">专家翻译</option>
            </select>
          </div>
        </div>

        {/* Upload area */}
        <div className="p-6 border-b border-border">
          <div
            onDragOver={e => { 
              e.preventDefault(); 
              e.currentTarget.classList.add('border-accent', 'bg-accent-bg'); 
            }}
            onDragLeave={e => { 
              e.preventDefault();
              e.currentTarget.classList.remove('border-accent', 'bg-accent-bg'); 
            }}
            onDrop={e => { 
              e.preventDefault(); 
              e.currentTarget.classList.remove('border-accent', 'bg-accent-bg');
              if (e.dataTransfer.files[0]) handleUpload(e.dataTransfer.files[0]); 
            }}
            onClick={() => { 
              const input = document.createElement('input'); 
              input.type = 'file'; 
              input.accept = '.txt,.epub,.docx,.pdf'; 
              input.onchange = () => { 
                if (input.files?.[0]) handleUpload(input.files[0]); 
              }; 
              input.click(); 
            }}
            className="border-2 border-dashed border-border rounded-xl p-10 text-center hover:border-accent/50 hover:bg-surface-secondary/30 transition-all cursor-pointer group"
          >
            <div className="flex flex-col items-center gap-3">
              <div className="w-16 h-16 rounded-full bg-surface-secondary group-hover:bg-accent-bg transition-colors flex items-center justify-center">
                <Upload className="w-8 h-8 text-text-tertiary group-hover:text-accent transition-colors" />
              </div>
              <div>
                <p className="text-[15px] font-medium text-text-primary mb-1">
                  拖拽文件到此处，或 <span className="text-accent">点击上传</span>
                </p>
                <p className="text-[13px] text-text-tertiary">
                  支持 TXT、EPUB、DOCX、PDF 格式，单个文件最大 50MB
                </p>
              </div>
              <div className="flex items-center gap-4 mt-2">
                <span className="text-[11px] text-text-placeholder px-2 py-1 bg-surface-secondary rounded">TXT</span>
                <span className="text-[11px] text-text-placeholder px-2 py-1 bg-surface-secondary rounded">EPUB</span>
                <span className="text-[11px] text-text-placeholder px-2 py-1 bg-surface-secondary rounded">DOCX</span>
                <span className="text-[11px] text-text-placeholder px-2 py-1 bg-surface-secondary rounded">PDF</span>
              </div>
            </div>
          </div>
          {uploading && (
            <div className="mt-4 flex items-center justify-center gap-2 text-[13px] text-accent">
              <div className="w-4 h-4 border-2 border-accent border-t-transparent rounded-full animate-spin" />
              上传中...
            </div>
          )}
        </div>

        {/* Document list */}
        <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
            <span className="text-[13px] text-text-tertiary">加载中...</span>
          </div>
        ) : documents.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
            <div className="w-16 h-16 rounded-full bg-surface-secondary flex items-center justify-center">
              <FileText className="w-8 h-8 text-text-tertiary" />
            </div>
            <div>
              <p className="text-[15px] font-medium text-text-secondary mb-1">暂无文档</p>
              <p className="text-[13px] text-text-tertiary">上传您的第一个文档开始翻译</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-border/50">
            {documents.map(doc => (
              <div key={doc.id} className="px-6 py-4 hover:bg-surface-secondary/30 transition-colors">
                <div className="flex items-start gap-4">
                  <div className="flex-shrink-0 w-10 h-10 rounded-lg bg-surface-secondary flex items-center justify-center">
                    <FileText className="w-5 h-5 text-text-tertiary" />
                  </div>
                  
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-3 mb-2">
                      <div className="min-w-0 flex-1">
                        <p className="text-[14px] font-medium text-text-primary truncate" title={doc.name}>
                          {doc.name}
                        </p>
                        <div className="flex items-center gap-3 mt-1.5 text-[12px] text-text-tertiary">
                          <span className="flex items-center gap-1">
                            <Languages className="w-3 h-3" />
                            {getLangName(doc.sourceLang)} → {getLangName(doc.targetLang)}
                          </span>
                          <span className="flex items-center gap-1">
                            <HardDrive className="w-3 h-3" />
                            {formatFileSize(doc.fileSize || 0)}
                          </span>
                          <span className="flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {new Date(doc.createTime).toLocaleString('zh-CN', { 
                              month: '2-digit', 
                              day: '2-digit', 
                              hour: '2-digit', 
                              minute: '2-digit' 
                            })}
                          </span>
                        </div>
                      </div>
                      
                      <div className="flex items-center gap-2 flex-shrink-0">
                        {doc.status === 'completed' && (
                          <button 
                            onClick={() => handleDownload(doc)} 
                            className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors"
                            title="下载译文"
                          >
                            <Download className="w-4 h-4" />
                          </button>
                        )}
                        {(doc.status === 'pending' || doc.status === 'processing') && (
                          <button 
                            onClick={() => handleCancel(doc.id)} 
                            className="p-2 rounded-lg text-text-tertiary hover:text-red hover:bg-red-bg transition-colors"
                            title="取消翻译"
                          >
                            <XCircle className="w-4 h-4" />
                          </button>
                        )}
                        {doc.status === 'failed' && (
                          <button 
                            onClick={() => handleRetry(doc.id)} 
                            className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors"
                            title="重新翻译"
                          >
                            <RefreshCw className="w-4 h-4" />
                          </button>
                        )}
                        <button 
                          onClick={() => handleDelete(doc.id)} 
                          className="p-2 rounded-lg text-text-tertiary hover:text-red hover:bg-red-bg transition-colors"
                          title="删除文档"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                    
                    {/* Status and progress */}
                    <div className="flex items-center gap-3">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-[12px] font-medium ${statusColor[doc.status] || 'bg-gray-100 text-text-tertiary'}`}>
                        {statusLabel[doc.status] || doc.status}
                      </span>
                      
                      {(doc.status === 'pending' || doc.status === 'processing') && (
                        <div className="flex-1 max-w-xs">
                          <div className="flex items-center justify-between text-[11px] text-text-tertiary mb-1">
                            <span>进度</span>
                            <span className="font-mono">{doc.progress || 0}%</span>
                          </div>
                          <div className="w-full h-1.5 bg-surface-secondary rounded-full overflow-hidden">
                            <div 
                              className="h-full bg-gradient-brand transition-all duration-500 ease-out"
                              style={{ width: `${doc.progress || 0}%` }}
                            />
                          </div>
                        </div>
                      )}
                      
                      {doc.status === 'failed' && doc.errorMessage && (
                        <span className="text-[12px] text-red flex-1 truncate" title={doc.errorMessage}>
                          {doc.errorMessage}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        </div>

        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

        {/* Bottom bar */}
        <div className="flex items-center justify-between px-6 py-3 border-t border-border bg-surface-secondary">
          <div className="flex items-center gap-4 text-[12px] text-text-tertiary">
            <span>{documents.length} / {total} 个文档</span>
            {documents.length > 0 && (
              <>
                <span className="text-text-placeholder">|</span>
                <span>
                  总计: {formatFileSize(documents.reduce((sum, doc) => sum + (doc.fileSize || 0), 0))}
                </span>
              </>
            )}
          </div>
          <button
            onClick={loadDocuments}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-[13px] text-text-tertiary hover:text-accent transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            刷新
          </button>
        </div>
      </div>
    </div>
  );
}

export { DocumentPage };
