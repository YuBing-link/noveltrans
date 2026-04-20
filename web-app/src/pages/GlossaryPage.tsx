import { useState, useEffect, useCallback } from 'react';
import { useToast } from '../components/ui/Toast';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { glossaryApi } from '../api/glossaries';
import type { GlossaryItem } from '../api/types';
import { Plus, Pencil, Trash2, Search, BookOpen } from 'lucide-react';

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
    try {
      const { data } = await glossaryApi.getList();
      setTerms(data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleAdd = useCallback(() => {
    setEditingTerm(null);
    setSourceWord('');
    setTargetWord('');
    setRemark('');
    setModalOpen(true);
  }, []);

  const handleEdit = useCallback((term: GlossaryItem) => {
    setEditingTerm(term);
    setSourceWord(term.sourceWord);
    setTargetWord(term.targetWord);
    setRemark(term.remark || '');
    setModalOpen(true);
  }, []);

  const handleSave = async () => {
    if (!sourceWord || !targetWord) { toastError('请填写原文和译文'); return; }
    try {
      if (editingTerm) {
        await glossaryApi.update(editingTerm.id, { sourceWord, targetWord, remark });
        success('更新成功');
      } else {
        await glossaryApi.create({ sourceWord, targetWord, remark });
        success('添加成功');
      }
      setModalOpen(false);
      loadTerms();
    } catch (err) {
      toastError(err instanceof Error ? err.message : '操作失败');
    }
  };

  const handleDelete = async (term: GlossaryItem) => {
    try {
      await glossaryApi.delete(term.id);
      success('已删除');
      loadTerms();
    } catch { toastError('删除失败'); }
  };

  const filtered = terms.filter(t =>
    !search || t.sourceWord.includes(search) || t.targetWord.includes(search)
  );

  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="mb-10 text-center">
        <h1 className="text-[28px] sm:text-[32px] font-semibold text-text-primary tracking-display leading-[1.07] mb-2">
          术语表
        </h1>
        <p className="text-text-secondary text-[15px]">
          管理专业翻译术语，提升翻译一致性
        </p>
      </div>

      <Card>
        <div className="p-6">
          <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center mb-6">
            <div className="relative flex-1 max-w-xs">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
              <input
                type="text"
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder="搜索术语..."
                className="w-full pl-9 pr-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none transition-colors"
              />
            </div>
            <Button onClick={handleAdd} className="ml-auto">
              <Plus className="w-4 h-4" /> 添加术语
            </Button>
          </div>

          {loading ? (
            <div className="text-center py-8 text-text-tertiary">加载中...</div>
          ) : filtered.length === 0 ? (
            <div className="text-center py-12">
              <BookOpen className="w-12 h-12 text-text-tertiary mx-auto mb-4" />
              <p className="text-[13px] text-text-tertiary">
                {search ? '未找到匹配的术语' : '暂无术语，点击"添加术语"开始创建'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-[13px]">
                <thead>
                  <tr className="border-b border-border/50">
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">原文</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium">译文</th>
                    <th className="text-left py-3 px-4 text-text-tertiary font-medium hidden sm:table-cell">备注</th>
                    <th className="text-right py-3 px-4 text-text-tertiary font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(term => (
                    <tr key={term.id} className="border-b border-border/50 last:border-0">
                      <td className="py-3 px-4 text-text-primary font-medium">{term.sourceWord}</td>
                      <td className="py-3 px-4 text-text-primary">{term.targetWord}</td>
                      <td className="py-3 px-4 text-text-tertiary hidden sm:table-cell">{term.remark || '-'}</td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button variant="ghost" onClick={() => handleEdit(term)} className="px-2 py-1">
                            <Pencil className="w-4 h-4" />
                          </Button>
                          <Button variant="ghost" onClick={() => handleDelete(term)} className="px-2 py-1 text-red">
                            <Trash2 className="w-4 h-4" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editingTerm ? '编辑术语' : '添加术语'}>
        <div className="p-6 space-y-4">
          <Input label="原文" value={sourceWord} onChange={e => setSourceWord(e.target.value)} placeholder="原文词汇" />
          <Input label="译文" value={targetWord} onChange={e => setTargetWord(e.target.value)} placeholder="译文词汇" />
          <Input label="备注（可选）" value={remark} onChange={e => setRemark(e.target.value)} placeholder="备注说明" />
          <div className="flex gap-2 justify-end pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>取消</Button>
            <Button onClick={handleSave}>保存</Button>
          </div>
        </div>
      </Modal>
    </PageLayout>
  );
}

export { GlossaryPage };
