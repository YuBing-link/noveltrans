import { useState, useEffect, useCallback } from 'react';
import { useToast } from '../components/ui/Toast';
import { glossaryApi } from '../api/glossaries';
import type { GlossaryItem } from '../api/types';
import { Plus, Pencil, Trash2, Search } from 'lucide-react';

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
    catch { /* ignore */ }
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
    <div className="w-full" style={{ minHeight: 'calc(100vh - 200px)' }}>
      <div className="border border-border/50 rounded-lg overflow-hidden flex flex-col" style={{ minHeight: 'calc(100vh - 200px)' }}>
        {/* Header bar */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-border/50">
          <div className="relative flex-1 max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="搜索术语..."
              className="w-full pl-9 pr-3 py-1.5 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none transition-colors"
            />
          </div>
          <button
            onClick={handleAdd}
            className="inline-flex items-center gap-1 px-4 py-1.5 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors"
          >
            <Plus className="w-4 h-4" /> 添加术语
          </button>
        </div>

        {/* Table */}
        <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="text-center py-8 text-text-tertiary text-[13px]">加载中...</div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-12 text-text-tertiary text-[13px]">
            {search ? '未找到匹配的术语' : '暂无术语，点击"添加术语"开始创建'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border/50 bg-surface-secondary">
                  <th className="text-left py-3 px-5 text-text-tertiary font-medium">原文</th>
                  <th className="text-left py-3 px-5 text-text-tertiary font-medium">译文</th>
                  <th className="text-left py-3 px-5 text-text-tertiary font-medium hidden sm:table-cell">备注</th>
                  <th className="text-right py-3 px-5 text-text-tertiary font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(term => (
                  <tr key={term.id} className="border-b border-border/50 last:border-0 hover:bg-surface-secondary/50">
                    <td className="py-3 px-5 text-text-primary font-medium">{term.sourceWord}</td>
                    <td className="py-3 px-5 text-text-primary">{term.targetWord}</td>
                    <td className="py-3 px-5 text-text-tertiary hidden sm:table-cell">{term.remark || '-'}</td>
                    <td className="py-3 px-5 text-right">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => handleEdit(term)} className="p-1 text-text-tertiary hover:text-accent"><Pencil className="w-4 h-4" /></button>
                        <button onClick={() => handleDelete(term)} className="p-1 text-text-tertiary hover:text-red"><Trash2 className="w-4 h-4" /></button>
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
        <div className="flex items-center justify-between px-5 py-3 border-t border-border/50 bg-surface-secondary">
          <span className="text-[12px] text-text-tertiary">{terms.length} 个术语</span>
        </div>
      </div>

      {/* Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-overlay-backdrop/50" onClick={() => setModalOpen(false)}>
          <div className="bg-white dark:bg-gray-50 rounded-card shadow-elevated w-full max-w-md mx-4" onClick={e => e.stopPropagation()}>
            <h3 className="text-[15px] font-semibold text-text-primary px-6 pt-5 pb-3">{editingTerm ? '编辑术语' : '添加术语'}</h3>
            <div className="px-6 pb-5 space-y-3">
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1">原文</label>
                <input value={sourceWord} onChange={e => setSourceWord(e.target.value)} placeholder="原文词汇" className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
              </div>
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1">译文</label>
                <input value={targetWord} onChange={e => setTargetWord(e.target.value)} placeholder="译文词汇" className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
              </div>
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1">备注（可选）</label>
                <input value={remark} onChange={e => setRemark(e.target.value)} placeholder="备注说明" className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
              </div>
              <div className="flex gap-2 justify-end pt-2">
                <button onClick={() => setModalOpen(false)} className="px-4 py-1.5 text-[13px] text-text-tertiary hover:text-text-primary rounded-button border border-border transition-colors">取消</button>
                <button onClick={handleSave} className="px-4 py-1.5 text-[13px] text-white bg-accent rounded-button hover:bg-accent-hover transition-colors">保存</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export { GlossaryPage };
