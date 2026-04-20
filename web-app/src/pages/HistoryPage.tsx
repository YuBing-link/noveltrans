import { useState, useEffect } from 'react';
import { useToast } from '../components/ui/Toast';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/Card';
import { Tabs } from '../components/ui/Tabs';
import { Button } from '../components/ui/Button';
import { userApi } from '../api/user';
import { translateApi } from '../api/translate';
import type { TranslationHistoryItem } from '../api/types';
import { Trash2, RefreshCw, Clock, FileText } from 'lucide-react';

const filterTabs = [
  { key: 'all', label: '全部' },
  { key: 'completed', label: '已完成' },
  { key: 'processing', label: '进行中' },
  { key: 'failed', label: '失败' },
];

function HistoryPage() {
  const { success, error: toastError } = useToast();
  const [items, setItems] = useState<TranslationHistoryItem[]>([]);
  const [filter, setFilter] = useState('all');
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => { loadHistory(); }, [filter, page]);

  useEffect(() => { setPage(1); }, [filter]);

  const loadHistory = async () => {
    setLoading(true);
    try {
      const { data } = await userApi.getTranslationHistory({
        page,
        pageSize: 20,
        type: filter === 'all' ? undefined : filter,
      });
      setItems(data.list || []);
      setTotalPages(data.totalPages || 1);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleDelete = async (taskId: string) => {
    try {
      await translateApi.cancelTask(taskId);
      success('已删除');
      loadHistory();
    } catch { toastError('删除失败'); }
  };

  const handleRetranslate = async (item: TranslationHistoryItem) => {
    try {
      await translateApi.getTaskResult(item.taskId);
      success('已重新获取结果');
    } catch { toastError('重新翻译失败'); }
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('zh-CN');
  };

  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="mb-10 text-center">
        <h1 className="text-[28px] sm:text-[32px] font-semibold text-text-primary tracking-display leading-[1.07] mb-2">
          翻译历史
        </h1>
        <p className="text-text-secondary text-[15px]">
          查看和管理您的所有翻译记录
        </p>
      </div>

      {/* Filters */}
      <Card className="mb-6">
        <div className="p-6">
          <Tabs tabs={filterTabs} activeTab={filter} onChange={setFilter} />
        </div>
      </Card>

      {/* List */}
      {loading ? (
        <div className="text-center py-12 text-text-tertiary">加载中...</div>
      ) : items.length === 0 ? (
        <Card>
          <div className="p-6 text-center py-12">
            <div className="p-4 rounded-card bg-surface-secondary inline-flex mx-auto mb-4">
              <Clock className="w-12 h-12 text-text-tertiary" />
            </div>
            <p className="text-[13px] text-text-tertiary">暂无翻译记录</p>
          </div>
        </Card>
      ) : (
        <div className="space-y-3">
          {items.map(item => (
            <Card key={item.id} variant="subtle" className="flex flex-col sm:flex-row sm:items-center gap-4">
              <div className="p-4 flex items-center gap-4 flex-1 min-w-0">
                <div className="p-2 rounded-card bg-surface-secondary flex-shrink-0">
                  <FileText className="w-4 h-4 text-text-tertiary" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-[13px] font-medium text-text-primary truncate">
                    {item.filename || item.taskId}
                  </p>
                  <p className="text-[12px] text-text-tertiary">
                    {item.sourceTextPreview?.slice(0, 60)}...
                  </p>
                  <p className="text-[12px] text-text-tertiary mt-1">
                    {formatDate(item.createTime)}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-1 flex-shrink-0 px-4 pb-4">
                <Button variant="ghost" onClick={() => handleRetranslate(item)} className="px-2 py-1">
                  <RefreshCw className="w-4 h-4" />
                </Button>
                <Button variant="ghost" onClick={() => handleDelete(item.taskId)} className="px-2 py-1 text-red">
                  <Trash2 className="w-4 h-4" />
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-8">
          <Button
            variant="secondary"
            disabled={page <= 1}
            onClick={() => setPage(p => p - 1)}
            className="px-4 py-2"
          >
            上一页
          </Button>
          {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
            const p = i + 1;
            return (
              <Button
                key={p}
                variant={p === page ? 'primary' : 'secondary'}
                onClick={() => setPage(p)}
                className="px-4 py-2 min-w-10"
              >
                {p}
              </Button>
            );
          })}
          <Button
            variant="secondary"
            disabled={page >= totalPages}
            onClick={() => setPage(p => p + 1)}
            className="px-4 py-2"
          >
            下一页
          </Button>
        </div>
      )}
    </PageLayout>
  );
}

export { HistoryPage };
