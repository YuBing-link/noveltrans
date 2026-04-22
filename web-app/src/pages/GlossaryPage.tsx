import { useState, useEffect, useCallback } from 'react';
import { useToast } from '../components/ui/Toast';
import { glossaryApi } from '../api/glossaries';
import type { GlossaryItem } from '../api/types';
import { Plus, Pencil, Trash2, Search, Upload, Download, BookOpen } from 'lucide-react';

function GlossaryPage() {
  const { success, error: toastError } = useToast();
  const [terms, setTerms] = useState<GlossaryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingTerm, setEditingTerm] = useState<GlossaryItem | null>(null);
  const [sourceWord, setSourceWord] = useState('');
  const [targetWord, setTargetWord] = useState('');
  const [remark, setRemark] = useState('');

  useEffect(() => { loadTerms(); }, []);

  const loadTerms = async () => {
    setLoading(true);
    try { const { data } = await glossaryApi.getList(); setTerms(data || []); }
    catch (err) { 
      console.warn('加载术语表失败:', err);
    }
    finally { setLoading(false); }
  };

  const handleAdd = useCallback(() => {
    setEditingTerm(null); setSourceWord(''); setTargetWord(''); setRemark(''); setModalOpen(true);
  }, []);

  const handleEdit = useCallback((term: GlossaryItem) => {
    setEditingTerm(term); setSourceWord(term.sourceWord); setTargetWord(term.targetWord); setRemark(term.remark || ''); setModalOpen(true);
  }, []);

  const handleSave = async () => {
    if (!sourceWord || !targetWord) { toastError('请填写原文和译文'); return; }
    try {
      if (editingTerm) await glossaryApi.update(editingTerm.id, { sourceWord, targetWord, remark });
      else await glossaryApi.create({ sourceWord, targetWord, remark });
      success(editingTerm ? '更新成功' : '添加成功');
      setModalOpen(false); loadTerms();
    } catch (err) { toastError(err instanceof Error ? err.message : '操作失败'); }
  };

  const handleDelete = async (term: GlossaryItem) => {
    try { await glossaryApi.delete(term.id); success('已删除'); loadTerms(); }
    catch { toastError('删除失败'); }
  };

  const filtered = terms.filter(t =>
    !search || t.sourceWord.includes(search) || t.targetWord.includes(search)
  );

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Page Header */}
      <div className="mb-6">
        <h1 className="text-[28px] font-bold text-text-primary mb-2 tracking-tight">
          术语表管理
        </h1>
        <p className="text-[15px] text-text-secondary">
          管理专业术语，确保翻译一致性和准确性
        </p>
      </div>

      <div className="border border-border rounded-xl shadow-sm bg-surface overflow-hidden">
        {/* Header bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 px-6 py-4 border-b border-border bg-surface-secondary/50">
          <div className="relative flex-1 w-full sm:max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="搜索术语..."
              className="w-full pl-9 pr-3 py-2 text-[13px] bg-surface text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors"
            />
          </div>
          
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <button
              onClick={() => { /* TODO: 实现导入功能 */ }}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium text-text-secondary bg-surface border border-border rounded-lg hover:bg-surface-secondary transition-colors"
              title="即将推出"
            >
              <Upload className="w-4 h-4" /> 
              <span className="hidden sm:inline">导入</span>
            </button>
            <button
              onClick={() => { /* TODO: 实现导出功能 */ }}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium text-text-secondary bg-surface border border-border rounded-lg hover:bg-surface-secondary transition-colors"
              title="即将推出"
            >
              <Download className="w-4 h-4" />
              <span className="hidden sm:inline">导出</span>
            </button>
            <button
              onClick={handleAdd}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-lg hover:bg-accent-hover transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" /> 
              <span>添加术语</span>
            </button>
          </div>
        </div>

        {/* Table */}
        <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
            <span className="text-[13px] text-text-tertiary">加载中...</span>
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
            <div className="w-16 h-16 rounded-full bg-surface-secondary flex items-center justify-center">
              <BookOpen className="w-8 h-8 text-text-tertiary" />
            </div>
            <div>
              <p className="text-[15px] font-medium text-text-secondary mb-1">
                {search ? '未找到匹配的术语' : '暂无术语'}
              </p>
              <p className="text-[13px] text-text-tertiary">
                {search ? '尝试其他关键词' : '点击"添加术语"开始创建您的术语库'}
              </p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border/50 bg-surface-secondary/50">
                  <th className="text-left py-3 px-6 text-text-tertiary font-medium">原文</th>
                  <th className="text-left py-3 px-6 text-text-tertiary font-medium">译文</th>
                  <th className="text-left py-3 px-6 text-text-tertiary font-medium hidden md:table-cell">备注</th>
                  <th className="text-right py-3 px-6 text-text-tertiary font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(term => (
                  <tr key={term.id} className="border-b border-border/50 last:border-0 hover:bg-surface-secondary/30 transition-colors">
                    <td className="py-4 px-6 text-text-primary font-medium">{term.sourceWord}</td>
                    <td className="py-4 px-6 text-text-primary">{term.targetWord}</td>
                    <td className="py-4 px-6 text-text-tertiary hidden md:table-cell max-w-xs truncate" title={term.remark}>
                      {term.remark || '-'}
                    </td>
                    <td className="py-4 px-6 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button 
                          onClick={() => handleEdit(term)} 
                          className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors"
                          title="编辑"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button 
                          onClick={() => handleDelete(term)} 
                          className="p-2 rounded-lg text-text-tertiary hover:text-red hover:bg-red-bg transition-colors"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        </div>

        {/* Bottom bar */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-border bg-surface-secondary">
          <span className="text-[12px] text-text-tertiary">{terms.length} 个术语</span>
        </div>
      </div>

      {/* Modal */}
      {modalOpen && (
        <div 
          className="fixed inset-0 z-50 flex items-center justify-center bg-overlay-backdrop" 
          onClick={() => setModalOpen(false)}
        >
          <div 
            className="bg-surface rounded-xl shadow-modal w-full max-w-md mx-4 animate-fade-in" 
            onClick={e => e.stopPropagation()}
          >
            <div className="px-6 pt-6 pb-4 border-b border-border">
              <h3 className="text-[16px] font-semibold text-text-primary">
                {editingTerm ? '编辑术语' : '添加术语'}
              </h3>
            </div>
            
            <div className="px-6 py-5 space-y-4">
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1.5">原文 <span className="text-red">*</span></label>
                <input 
                  value={sourceWord} 
                  onChange={e => setSourceWord(e.target.value)} 
                  placeholder="输入原文词汇" 
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors" 
                />
              </div>
              
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1.5">译文 <span className="text-red">*</span></label>
                <input 
                  value={targetWord} 
                  onChange={e => setTargetWord(e.target.value)} 
                  placeholder="输入译文词汇" 
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors" 
                />
              </div>
              
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1.5">备注（可选）</label>
                <textarea 
                  value={remark} 
                  onChange={e => setRemark(e.target.value)} 
                  placeholder="添加备注说明，例如使用场景、词性等" 
                  rows={3}
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors resize-none" 
                />
              </div>
              
              <div className="flex gap-3 justify-end pt-2">
                <button 
                  onClick={() => setModalOpen(false)} 
                  className="px-4 py-2 text-[13px] font-medium text-text-secondary hover:text-text-primary rounded-lg border border-border hover:bg-surface-secondary transition-colors"
                >
                  取消
                </button>
                <button 
                  onClick={handleSave} 
                  className="px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-lg hover:bg-accent-hover transition-colors shadow-sm"
                >
                  保存
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export { GlossaryPage };
