import { useState, useCallback, useEffect } from 'react';
import { useToast } from '../components/ui/Toast';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { DocumentUploader } from '../components/features/DocumentUploader';
import { TaskProgress } from '../components/features/TaskProgress';
import { LanguageSelector } from '../components/features/LanguageSelector';
import { documentApi } from '../api/documents';
import type { DocumentItem } from '../api/types';
import { FileText, Download, Trash2, RefreshCw, XCircle } from 'lucide-react';

function DocumentPage() {
  const { success, error: toastError } = useToast();
  const [sourceLang, setSourceLang] = useState('auto');
  const [targetLang, setTargetLang] = useState('zh');
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
      await documentApi.upload(file, { sourceLang, targetLang });
      success('文件上传成功，开始翻译');
      loadDocuments();
    } catch (err) {
      toastError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setUploading(false);
    }
  }, [sourceLang, targetLang]);

  const handleDelete = async (docId: number) => {
    try {
      await documentApi.delete(docId);
      success('已删除');
      loadDocuments();
    } catch { toastError('删除失败'); }
  };

  const handleDownload = async (docId: number) => {
    try {
      const blob = await documentApi.download(docId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `translated_${docId}`;
      a.click();
      URL.revokeObjectURL(url);
      success('下载成功');
    } catch { toastError('下载失败'); }
  };

  const handleRetry = async (docId: number) => {
    try {
      await documentApi.retry(docId);
      success('已重新提交翻译');
      loadDocuments();
    } catch { toastError('重试失败'); }
  };

  const handleCancel = async (docId: number) => {
    try {
      await documentApi.cancel(docId);
      success('已取消');
      loadDocuments();
    } catch { toastError('取消失败'); }
  };

  const statusLabel: Record<string, string> = {
    pending: '排队中', processing: '翻译中', completed: '已完成',
    failed: '失败', cancelled: '已取消',
  };

  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="mb-10 text-center">
        <h1 className="text-[28px] sm:text-[32px] font-semibold text-text-primary tracking-display leading-[1.07] mb-2">
          文档翻译
        </h1>
        <p className="text-text-secondary text-[15px]">
          上传文档，支持 TXT、EPUB、DOCX、PDF 格式
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Upload Section */}
        <div className="lg:col-span-2 space-y-4">
          <Card>
            <div className="p-6">
              <DocumentUploader onUpload={handleUpload} loading={uploading} />
            </div>
          </Card>

          {/* Document List */}
          <Card>
            <div className="p-6">
              <h3 className="text-[15px] font-semibold text-text-primary mb-4">我的文档</h3>
              {loading ? (
                <div className="text-center py-8 text-text-tertiary">加载中...</div>
              ) : documents.length === 0 ? (
                <div className="text-center py-8 text-text-tertiary text-[13px]">暂无文档</div>
              ) : (
                <div className="space-y-3">
                  {documents.map(doc => (
                    <div key={doc.id} className="p-4 rounded-card border border-border/50">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3 min-w-0 flex-1">
                          <FileText className="w-5 h-5 text-text-tertiary flex-shrink-0" />
                          <div className="min-w-0">
                            <p className="text-[13px] font-medium text-text-primary truncate">{doc.name}</p>
                            {doc.errorMessage && (
                              <p className="text-[12px] text-red mt-0.5">{doc.errorMessage}</p>
                            )}
                          </div>
                        </div>
                        <div className="flex items-center gap-1 flex-shrink-0">
                          {doc.status === 'completed' && (
                            <Button variant="ghost" onClick={() => handleDownload(doc.id)} className="px-2 py-1">
                              <Download className="w-4 h-4" />
                            </Button>
                          )}
                          {(doc.status === 'pending' || doc.status === 'processing') && (
                            <Button variant="ghost" onClick={() => handleCancel(doc.id)} className="px-2 py-1">
                              <XCircle className="w-4 h-4" />
                            </Button>
                          )}
                          {doc.status === 'failed' && (
                            <Button variant="ghost" onClick={() => handleRetry(doc.id)} className="px-2 py-1">
                              <RefreshCw className="w-4 h-4" />
                            </Button>
                          )}
                          <Button variant="ghost" onClick={() => handleDelete(doc.id)} className="px-2 py-1 text-red">
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </div>
                      </div>
                      {doc.status === 'pending' || doc.status === 'processing' ? (
                        <div className="mt-3">
                          <TaskProgress progress={doc.progress || 0} status={doc.status} />
                        </div>
                      ) : (
                        <p className="text-[12px] text-text-tertiary mt-2">
                          {statusLabel[doc.status] || doc.status}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </Card>
        </div>

        {/* Settings Sidebar */}
        <div className="space-y-4">
          <Card>
            <div className="p-6">
              <h3 className="text-[15px] font-semibold text-text-primary mb-4">翻译设置</h3>
              <LanguageSelector
                sourceLang={sourceLang}
                targetLang={targetLang}
                onSourceChange={setSourceLang}
                onTargetChange={setTargetLang}
                onSwap={() => {
                  if (sourceLang !== 'auto') {
                    setSourceLang(targetLang);
                    setTargetLang(sourceLang);
                  }
                }}
              />
            </div>
          </Card>
        </div>
      </div>
    </PageLayout>
  );
}

export { DocumentPage };
