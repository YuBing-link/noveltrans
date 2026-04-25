import { useState, useEffect } from 'react';
import { useToast } from '../components/ui/Toast';
import { userApi } from '../api/user';
import { translateApi } from '../api/translate';
import type { TranslationHistoryItem } from '../api/types';
import { Trash2, ChevronLeft, ChevronRight, Clock, Languages, FileText } from 'lucide-react';
import { SUPPORTED_LANGUAGES } from '../api/types';

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
    } catch (err) {
      console.warn('加载历史记录失败:', err);
    }
    finally { setLoading(false); }
  };

  const handleDelete = async (taskId: string) => {
    try { await translateApi.deleteHistory(taskId); success('已删除'); loadHistory(); }
    catch { toastError('删除失败'); }
  };

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes} 分钟前`;
    if (hours < 24) return `${hours} 小时前`;
    if (days < 7) return `${days} 天前`;
    return date.toLocaleString('zh-CN', { 
      year: 'numeric', 
      month: '2-digit', 
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getLangName = (code: string) => {
    const lang = SUPPORTED_LANGUAGES.find(l => l.code === code);
    return lang ? lang.name : code;
  };

  return (
    <div className="py-8">
      {/* Page Header */}
      <div className="mb-6">
        <h1 className="text-[28px] font-bold text-text-primary mb-2 tracking-tight">
          翻译历史
        </h1>
        <p className="text-[15px] text-text-secondary">
          查看和管理您的所有翻译记录
        </p>
      </div>

      <div className="border border-border rounded-xl shadow-sm bg-surface overflow-hidden">
        {/* Filter tabs */}
        <div className="flex items-center gap-2 px-6 py-4 border-b border-border bg-surface-secondary/30">
          {filterTabs.map(tab => (
            <button
              key={tab.key}
              onClick={() => setFilter(tab.key)}
              className={`px-3 py-1.5 text-[13px] font-medium rounded-md transition-all ${
                filter === tab.key
                  ? 'bg-accent/10 text-accent'
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
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
            <span className="text-[13px] text-text-tertiary">加载中...</span>
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
            <div className="w-16 h-16 rounded-full bg-surface-secondary flex items-center justify-center">
              <Clock className="w-8 h-8 text-text-tertiary" />
            </div>
            <div>
              <p className="text-[15px] font-medium text-text-secondary mb-1">暂无翻译记录</p>
              <p className="text-[13px] text-text-tertiary">开始您的第一次翻译，历史记录将显示在这里</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-border/30">
            {items.map(item => (
              <div key={item.id} className="px-6 py-4 hover:bg-surface-secondary/20 transition-colors">
                <div className="flex items-center gap-3">
                  <div className="flex-shrink-0">
                    <FileText className="w-5 h-5 text-text-tertiary" />
                  </div>
                  
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-3 mb-1.5">
                      <div className="min-w-0 flex-1">
                        <p className="text-[14px] font-medium text-text-primary truncate" title={item.filename || item.taskId}>
                          {item.filename || `翻译任务 #${item.taskId}`}
                        </p>
                      </div>
                      
                      <button 
                        onClick={() => handleDelete(item.taskId)} 
                        className="p-1.5 rounded-md text-text-tertiary hover:text-red hover:bg-red-bg transition-colors flex-shrink-0"
                        title="删除记录"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                    
                    <div className="flex items-center gap-3 text-[12px] text-text-tertiary">
                      <span className="flex items-center gap-1">
                        <Languages className="w-3 h-3" />
                        {getLangName(item.sourceLang)} → {getLangName(item.targetLang)}
                      </span>
                      <span className="text-text-placeholder">·</span>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        {formatDate(item.createTime)}
                      </span>
                    </div>
                    
                    {/* Preview text */}
                    {item.sourceTextPreview && (
                      <div className="mt-2 text-[13px] text-text-secondary line-clamp-2">
                        {item.sourceTextPreview}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 px-6 py-4 border-t border-border bg-surface-secondary">
            <button
              disabled={page <= 1}
              onClick={() => setPage(p => p - 1)}
              className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              const p = Math.max(1, Math.min(page - 2, totalPages - 4)) + i;
              if (p > totalPages) return null;
              return (
                <button
                  key={`page-${p}`}
                  onClick={() => setPage(p)}
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
              onClick={() => setPage(p => p + 1)}
              className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
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
