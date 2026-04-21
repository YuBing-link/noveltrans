import { useState, useEffect } from 'react';
import { useToast } from '../components/ui/Toast';
import { userApi } from '../api/user';
import { translateApi } from '../api/translate';
import type { TranslationHistoryItem } from '../api/types';
import { Trash2, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';

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
        page, pageSize: 20, type: filter === 'all' ? undefined : filter,
      });
      setItems(data.list || []);
      setTotalPages(data.totalPages || 1);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleDelete = async (taskId: string) => {
    try { await translateApi.deleteHistory(taskId); success('已删除'); loadHistory(); }
    catch { toastError('删除失败'); }
  };

  const formatDate = (dateStr: string) => new Date(dateStr).toLocaleString('zh-CN');

  return (
    <div className="w-full" style={{ minHeight: 'calc(100vh - 200px)' }}>
      <div className="border border-border/50 rounded-lg overflow-hidden flex flex-col" style={{ minHeight: 'calc(100vh - 200px)' }}>
        {/* Filter tabs */}
        <div className="flex items-center gap-1 px-5 py-3 border-b border-border/50">
          {filterTabs.map(tab => (
            <button
              key={tab.key}
              onClick={() => setFilter(tab.key)}
              className={`px-3 py-1 text-[13px] rounded-button transition-colors ${
                filter === tab.key
                  ? 'bg-accent text-white'
                  : 'text-text-tertiary hover:text-text-primary hover:bg-surface-secondary'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* List */}
        <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="text-center py-12 text-text-tertiary text-[13px]">加载中...</div>
        ) : items.length === 0 ? (
          <div className="text-center py-12 text-text-tertiary text-[13px]">暂无翻译记录</div>
        ) : (
          <div className="divide-y divide-border/50">
            {items.map(item => (
              <div key={item.id} className="px-5 py-4 flex items-center gap-4">
                <div className="flex-1 min-w-0">
                  <p className="text-[13px] font-medium text-text-primary truncate">{item.filename || item.taskId}</p>
                  <p className="text-[12px] text-text-tertiary mt-0.5">{item.sourceTextPreview?.slice(0, 60)}...</p>
                  <p className="text-[12px] text-text-placeholder mt-0.5">{formatDate(item.createTime)}</p>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                  <button onClick={() => handleDelete(item.taskId)} className="p-1 text-text-tertiary hover:text-red"><Trash2 className="w-4 h-4" /></button>
                </div>
              </div>
            ))}
          </div>
        )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 px-5 py-3 border-t border-border/50 bg-surface-secondary">
            <button
              disabled={page <= 1}
              onClick={() => setPage(p => p - 1)}
              className="p-1.5 text-text-tertiary hover:text-text-primary disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              const p = i + 1;
              return (
                <button
                  key={p}
                  onClick={() => setPage(p)}
                  className={`w-8 h-8 text-[13px] rounded-button transition-colors ${
                    p === page ? 'bg-accent text-white' : 'text-text-tertiary hover:text-text-primary'
                  }`}
                >
                  {p}
                </button>
              );
            })}
            <button
              disabled={page >= totalPages}
              onClick={() => setPage(p => p + 1)}
              className="p-1.5 text-text-tertiary hover:text-text-primary disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export { HistoryPage };
