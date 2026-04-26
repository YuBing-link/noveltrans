import { useState, useEffect, useCallback, useRef } from 'react';
import { useToast } from '../components/ui/Toast';
import { Pagination } from '../components/ui/Pagination';
import { glossaryApi } from '../api/glossaries';
import type { GlossaryItem } from '../api/types';
import { Plus, Pencil, Trash2, Search, Upload, Download, BookOpen } from 'lucide-react';
import { useTranslation } from 'react-i18next';

function GlossaryPage() {
  const { t } = useTranslation();
  const { success, error: toastError } = useToast();
  const [terms, setTerms] = useState<GlossaryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingTerm, setEditingTerm] = useState<GlossaryItem | null>(null);
  const [sourceWord, setSourceWord] = useState('');
  const [targetWord, setTargetWord] = useState('');
  const [remark, setRemark] = useState('');
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => { loadTerms(); }, [page]);
  useEffect(() => { setPage(1); }, [search]);

  const loadTerms = async () => {
    setLoading(true);
    try {
      const { data } = await glossaryApi.getList({ page, pageSize: 20, search: search || undefined });
      setTerms(data.list || []);
      setTotalPages(data.totalPages || 1);
      setTotal(data.total || 0);
    }
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
    if (!sourceWord || !targetWord) { toastError(t('glossary.messages.fillRequired')); return; }
    try {
      if (editingTerm) await glossaryApi.update(editingTerm.id, { sourceWord, targetWord, remark });
      else await glossaryApi.create({ sourceWord, targetWord, remark });
      success(editingTerm ? t('glossary.messages.updateSuccess') : t('glossary.messages.addSuccess'));
      setModalOpen(false); loadTerms();
    } catch (err) { toastError(err instanceof Error ? err.message : t('glossary.messages.operationFailed')); }
  };

  const handleDelete = async (term: GlossaryItem) => {
    try { await glossaryApi.delete(term.id); success(t('glossary.messages.deleteSuccess')); setSearch(''); setPage(1); loadTerms(); }
    catch { toastError(t('glossary.messages.deleteFailed')); }
  };

  const handleExport = async () => {
    try { await glossaryApi.exportGlossary(); success(t('glossary.messages.exportSuccess')); }
    catch { toastError(t('glossary.messages.exportFailed')); }
  };

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    try {
      const { data } = await glossaryApi.importGlossary(file);
      success(`${t('glossary.messages.importSuccess')} ${data} ${t('glossary.count')}`);
      setSearch(''); loadTerms();
    } catch (err) { toastError(err instanceof Error ? err.message : t('glossary.messages.importFailed')); }
    finally { setImporting(false); if (fileInputRef.current) fileInputRef.current.value = ''; }
  };


  return (
    <div className="py-8">
      <div className="mb-6">
        <h1 className="text-[28px] font-bold text-text-primary mb-2 tracking-tight">
          {t('glossary.title')}
        </h1>
        <p className="text-[15px] text-text-secondary">
          {t('glossary.subtitle')}
        </p>
      </div>

      <div className="border border-border rounded-xl shadow-sm bg-surface overflow-hidden">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 px-6 py-4 border-b border-border bg-surface-secondary/50">
          <div className="relative flex-1 w-full sm:max-w-xs">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-tertiary" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder={t('glossary.search')}
              style={{ paddingLeft: '3rem' }}
              className="w-full pr-3 py-2 text-[13px] bg-surface text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors"
            />
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto">
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv"
              onChange={handleImport}
              className="hidden"
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={importing}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium text-text-secondary bg-surface border border-border rounded-lg hover:bg-surface-secondary transition-colors disabled:opacity-30"
              title={t('glossary.actions.import')}
            >
              <Upload className="w-4 h-4" />
              <span className="hidden sm:inline">{importing ? t('glossary.actions.importing') : t('glossary.actions.import')}</span>
            </button>
            <button
              onClick={handleExport}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium text-text-secondary bg-surface border border-border rounded-lg hover:bg-surface-secondary transition-colors"
              title={t('glossary.actions.export')}
            >
              <Download className="w-4 h-4" />
              <span className="hidden sm:inline">{t('glossary.actions.export')}</span>
            </button>
            <button
              onClick={handleAdd}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-lg hover:bg-accent-hover transition-colors shadow-sm"
            >
              <Plus className="w-4 h-4" />
              <span>{t('glossary.actions.add')}</span>
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-auto">
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
            <span className="text-[13px] text-text-tertiary">{t('common.loading')}</span>
          </div>
        ) : terms.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
            <div className="w-16 h-16 rounded-full bg-surface-secondary flex items-center justify-center">
              <BookOpen className="w-8 h-8 text-text-tertiary" />
            </div>
            <div>
              <p className="text-[15px] font-medium text-text-secondary mb-1">
                {search ? t('glossary.empty.notFound') : t('glossary.empty.title')}
              </p>
              <p className="text-[13px] text-text-tertiary">
                {search ? t('glossary.empty.subtitle') : t('glossary.empty.noGlossary')}
              </p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead>
                <tr className="border-b border-border/50 bg-surface-secondary/50">
                  <th className="text-left py-3 px-6 text-text-tertiary font-medium">{t('glossary.fields.source')}</th>
                  <th className="text-left py-3 px-6 text-text-tertiary font-medium">{t('glossary.fields.target')}</th>
                  <th className="text-left py-3 px-6 text-text-tertiary font-medium hidden md:table-cell">{t('glossary.fields.notes')}</th>
                  <th className="text-right py-3 px-6 text-text-tertiary font-medium">{t('glossary.fields.action')}</th>
                </tr>
              </thead>
              <tbody>
                {terms.map(term => (
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
                          title={t('glossary.actions.edit')}
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => handleDelete(term)}
                          className="p-2 rounded-lg text-text-tertiary hover:text-red hover:bg-red-bg transition-colors"
                          title={t('glossary.actions.delete')}
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

        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />

        <div className="flex items-center justify-between px-6 py-4 border-t border-border bg-surface-secondary">
          <span className="text-[12px] text-text-tertiary">{total} {t('glossary.count')}</span>
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
                {editingTerm ? t('glossary.actions.edit') : t('glossary.actions.add')}
              </h3>
            </div>

            <div className="px-6 py-5 space-y-4">
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1.5">{t('glossary.form.source')} <span className="text-red">*</span></label>
                <input
                  value={sourceWord}
                  onChange={e => setSourceWord(e.target.value)}
                  placeholder={t('glossary.form.sourcePlaceholder')}
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors"
                />
              </div>

              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1.5">{t('glossary.form.target')} <span className="text-red">*</span></label>
                <input
                  value={targetWord}
                  onChange={e => setTargetWord(e.target.value)}
                  placeholder={t('glossary.form.targetPlaceholder')}
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors"
                />
              </div>

              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1.5">{t('glossary.form.notes')}</label>
                <textarea
                  value={remark}
                  onChange={e => setRemark(e.target.value)}
                  placeholder={t('glossary.form.notesPlaceholder')}
                  rows={3}
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-lg border border-border focus:border-accent focus:outline-none transition-colors resize-none"
                />
              </div>

              <div className="flex gap-3 justify-end pt-2">
                <button
                  onClick={() => setModalOpen(false)}
                  className="px-4 py-2 text-[13px] font-medium text-text-secondary hover:text-text-primary rounded-lg border border-border hover:bg-surface-secondary transition-colors"
                >
                  {t('glossary.buttons.cancel')}
                </button>
                <button
                  onClick={handleSave}
                  className="px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-lg hover:bg-accent-hover transition-colors shadow-sm"
                >
                  {t('glossary.buttons.save')}
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
