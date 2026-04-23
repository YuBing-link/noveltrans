import { useState, useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useToast } from '../components/ui/Toast';
import { Badge } from '../components/ui/Badge';
import { Tabs } from '../components/ui/Tabs';
import type { Tab } from '../components/ui/Tabs';
import { collabApi } from '../api/collab';
import { translateApi } from '../api/translate';
import type { ChapterTaskResponse, CommentResponse } from '../api/types';
import { SUPPORTED_LANGUAGES } from '../api/types';
import {
  ArrowLeft,
  BookOpen,
  Send,
  Sparkles,
  MessageSquare,
  BookMarked,
  Settings,
  Copy,
  Trash2,
  Save,
  Clock,
  Languages,
} from 'lucide-react';

// ==================== Status config ====================
const CHAPTER_STATUS_CONFIG: Record<string, { label: string; color: Parameters<typeof Badge>[0]['color'] }> = {
  UNASSIGNED: { label: '待分配', color: 'gray' },
  TRANSLATING: { label: '翻译中', color: 'blue' },
  SUBMITTED: { label: '已提交', color: 'yellow' },
  REVIEWING: { label: '审核中', color: 'purple' },
  APPROVED: { label: '已批准', color: 'green' },
  REJECTED: { label: '已退回', color: 'red' },
  COMPLETED: { label: '已完成', color: 'green' },
};

function getLangName(code: string): string {
  return SUPPORTED_LANGUAGES.find(l => l.code === code)?.name || code;
}

// ==================== CommentItem (reused from CollabPage) ====================
function CommentItem({ comment, depth }: { comment: CommentResponse; depth: number }) {
  const indentClass = depth > 0 ? 'ml-8 border-l-2 border-border pl-4' : '';

  return (
    <div className={`${indentClass}`}>
      <div className="flex items-start gap-2">
        <div className="w-7 h-7 rounded-full bg-accent/20 text-accent flex items-center justify-center text-xs font-semibold flex-shrink-0">
          {comment.username?.slice(0, 1).toUpperCase() || 'U'}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-text-primary">{comment.username}</span>
            <span className="text-xs text-text-tertiary">{comment.createTime}</span>
            {comment.resolved && (
              <span className="text-xs text-green">已解决</span>
            )}
          </div>
          {comment.sourceText && (
            <div className="mt-1 text-xs text-text-tertiary bg-surface-secondary px-2 py-1 rounded">
              原文: {comment.sourceText}
            </div>
          )}
          <p className="mt-1 text-sm text-text-secondary whitespace-pre-wrap">{comment.content}</p>
        </div>
      </div>
      {comment.replies?.map(reply => (
        <CommentItem key={reply.id} comment={reply} depth={depth + 1} />
      ))}
    </div>
  );
}

// ==================== Main Component ====================
function CollabWorkspace() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { success, error: toastError } = useToast();

  const chapterId = Number(searchParams.get('chapterId'));
  const targetLang = searchParams.get('targetLang') || 'zh';
  const [chapter, setChapter] = useState<ChapterTaskResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // Editor state
  const [sourceText, setSourceText] = useState('');
  const [editorText, setEditorText] = useState('');
  const [isDirty, setIsDirty] = useState(false);
  const [lastSaved, setLastSaved] = useState<Date | null>(null);
  const [saving, setSaving] = useState(false);

  // AI translation state
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamBuffer, setStreamBuffer] = useState('');
  const streamAbortRef = useRef(false);

  // Sidebar state
  const [sidebarTab, setSidebarTab] = useState<'comments' | 'reference' | 'settings'>('comments');
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [commentInput, setCommentInput] = useState('');
  const [anchoredSourceText, setAnchoredSourceText] = useState<string | null>(null);

  // Load chapter data
  useEffect(() => {
    if (!chapterId) {
      navigate('/collab');
      return;
    }
    loadChapter();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chapterId]);

  const loadChapter = async () => {
    setLoading(true);
    try {
      const res = await collabApi.getChapter(chapterId);
      setChapter(res.data);
      // Pre-fill source text
      const initialSource = res.data.sourceText || '';
      setSourceText(initialSource);
      // Pre-fill editor from chapter translatedText
      const initialText = res.data.translatedText || '';
      setEditorText(initialText);
      // Load draft from localStorage
      const draft = localStorage.getItem(`collab_draft_${chapterId}`);
      if (draft && !initialText) {
        setEditorText(draft);
      }
      // Load comments
      loadComments();
    } catch {
      toastError('加载章节失败');
      navigate('/collab');
    } finally {
      setLoading(false);
    }
  };

  const loadComments = async () => {
    try {
      const res = await collabApi.listComments(chapterId);
      setComments(res.data);
    } catch {
      // Silent fail — comments may not exist yet
    }
  };

  // Draft auto-save (every 30 seconds)
  useEffect(() => {
    if (!chapterId || !isDirty) return;
    const interval = setInterval(() => {
      localStorage.setItem(`collab_draft_${chapterId}`, editorText);
      setLastSaved(new Date());
      setIsDirty(false);
    }, 30000);
    return () => clearInterval(interval);
  }, [chapterId, isDirty, editorText]);

  // Save draft on unmount
  useEffect(() => {
    return () => {
      if (chapterId && isDirty && editorText) {
        localStorage.setItem(`collab_draft_${chapterId}`, editorText);
      }
    };
  }, [chapterId, isDirty, editorText]);

  // Save draft on beforeunload
  useEffect(() => {
    const handler = () => {
      if (chapterId && isDirty && editorText) {
        localStorage.setItem(`collab_draft_${chapterId}`, editorText);
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [chapterId, isDirty, editorText]);

  // Ctrl+Enter shortcut for AI translation
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        handleAiTranslate();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [chapter, editorText, isStreaming]);

  // ==================== Handlers ====================

  const handleAiTranslate = async () => {
    if (!sourceText.trim()) {
      toastError('原文内容为空，请先粘贴原文');
      return;
    }
    if (isStreaming) return;

    setIsStreaming(true);
    setStreamBuffer('');
    streamAbortRef.current = false;

    try {
      await translateApi.streamTranslate(
        {
          text: sourceText,
          sourceLang: 'auto',
          targetLang: targetLang,
          mode: 'ai',
        },
        (chunk) => {
          if (!streamAbortRef.current) {
            setStreamBuffer(prev => prev + chunk);
          }
        },
        () => {
          setIsStreaming(false);
          // Merge stream buffer into editor text
          setEditorText(prev => prev + streamBuffer);
          setStreamBuffer('');
          setIsDirty(true);
        },
        (err) => {
          setIsStreaming(false);
          setStreamBuffer('');
          toastError(err || '翻译失败');
        }
      );
    } catch {
      setIsStreaming(false);
      setStreamBuffer('');
      toastError('翻译请求失败');
    }
  };

  const handleStopStreaming = () => {
    streamAbortRef.current = true;
    setIsStreaming(false);
    if (streamBuffer) {
      setEditorText(prev => prev + streamBuffer);
      setStreamBuffer('');
      setIsDirty(true);
    }
  };

  const handleEditorChange = (value: string) => {
    setEditorText(value);
    setIsDirty(true);
    // If user edits during streaming, stop streaming and merge
    if (isStreaming) {
      handleStopStreaming();
    }
  };

  const handleSaveDraft = () => {
    if (!chapterId) return;
    localStorage.setItem(`collab_draft_${chapterId}`, editorText);
    setLastSaved(new Date());
    setIsDirty(false);
    success('草稿已保存');
  };

  const handleSubmit = async () => {
    if (!chapter) return;
    if (!editorText.trim()) {
      toastError('译文不能为空');
      return;
    }
    setSaving(true);
    try {
      await collabApi.submitChapter(chapter.id, { translatedText: editorText });
      success('翻译已提交');
      // Clear draft
      localStorage.removeItem(`collab_draft_${chapterId}`);
      // Refresh and go back
      await loadChapter();
    } catch (e) {
      toastError(e instanceof Error ? e.message : '提交失败');
    } finally {
      setSaving(false);
    }
  };

  const handleCopyEditor = async () => {
    try {
      await navigator.clipboard.writeText(editorText);
      success('已复制到剪贴板');
    } catch {
      toastError('复制失败');
    }
  };

  const handleClearEditor = () => {
    setEditorText('');
    setIsDirty(true);
  };

  const handleSourceSelect = () => {
    const sel = window.getSelection();
    if (!sel) return;
    const text = sel.toString().trim();
    if (text.length >= 3 && text.length <= 200) {
      setAnchoredSourceText(text);
      setSidebarTab('comments');
    }
  };

  const handleClearAnchor = () => {
    setAnchoredSourceText(null);
    window.getSelection()?.removeAllRanges();
  };

  const handleSendComment = async () => {
    if (!commentInput.trim() || !chapter) return;
    try {
      await collabApi.createComment(chapter.id, {
        content: commentInput,
        sourceText: anchoredSourceText || undefined,
      });
      setCommentInput('');
      setAnchoredSourceText(null);
      loadComments();
      success('评论已发送');
    } catch (e) {
      toastError(e instanceof Error ? e.message : '评论失败');
    }
  };

  const handleCopySource = async () => {
    if (!sourceText) return;
    try {
      await navigator.clipboard.writeText(sourceText);
      success('原文已复制');
    } catch {
      toastError('复制失败');
    }
  };

  // ==================== Derived values ====================
  const displayText = editorText + streamBuffer;
  const sourceChars = sourceText.length;
  const translatedChars = editorText.length;
  const progressPercent = sourceChars > 0
    ? Math.min(100, Math.round((translatedChars / sourceChars) * 100))
    : 0;
  const sourceWords = sourceText.split(/\s+/).filter(Boolean).length;
  const translatedWords = editorText.split(/\s+/).filter(Boolean).length;

  const sidebarTabs: Tab[] = [
    { key: 'comments', label: '评论' },
    { key: 'reference', label: '参考' },
    { key: 'settings', label: '设置' },
  ];

  if (loading || !chapter) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="text-text-tertiary">加载中...</div>
      </div>
    );
  }

  return (
    <div className="flex flex-col" style={{ minHeight: 'calc(100vh - 140px)' }}>
      {/* ==================== Top Toolbar ==================== */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border bg-surface-secondary">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/collab')}
            className="p-1.5 rounded-full hover:bg-surface-secondary transition-button text-text-secondary"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="flex items-center gap-2">
            <BookOpen className="w-4 h-4 text-text-tertiary" />
            <span className="text-sm font-medium text-text-primary">
              第{chapter.chapterNumber}章
            </span>
            {chapter.title && (
              <span className="text-sm text-text-secondary truncate max-w-64">
                {chapter.title}
              </span>
            )}
          </div>
          <Badge color={CHAPTER_STATUS_CONFIG[chapter.status]?.color || 'gray'}>
            {CHAPTER_STATUS_CONFIG[chapter.status]?.label || chapter.status}
          </Badge>
        </div>

        <div className="flex items-center gap-4">
          <div className="hidden sm:flex items-center gap-2 text-xs text-text-tertiary">
            <Languages className="w-3.5 h-3.5" />
            <span>自动检测 → {getLangName(targetLang)}</span>
          </div>
          <div className="hidden sm:flex items-center gap-3 text-xs text-text-tertiary">
            <span>原文: {sourceText.length.toLocaleString()} 字符</span>
            <span>译文: {editorText.length.toLocaleString()} 字符</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleSaveDraft}
              disabled={!isDirty}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Save className="w-3.5 h-3.5" /> 保存
            </button>
            <button
              onClick={handleSubmit}
              disabled={saving || !editorText.trim()}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Send className="w-3.5 h-3.5" /> {saving ? '提交中...' : '提交'}
            </button>
          </div>
        </div>
      </div>

      {/* ==================== Main 3-Panel Layout ==================== */}
      <div className="flex flex-1 overflow-hidden">
        {/* ===== Left Panel: Source Text ===== */}
        <div className="hidden md:flex md:flex-col md:w-[35%] lg:w-[35%] border-r border-border bg-surface overflow-hidden">
          <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-surface-secondary/50">
            <span className="text-xs font-medium text-text-secondary">原文</span>
            <button
              onClick={handleCopySource}
              className="p-1 rounded text-text-tertiary hover:text-text-primary transition-button"
              title="复制原文"
            >
              <Copy className="w-3.5 h-3.5" />
            </button>
          </div>

          <div
            className="flex-1 overflow-y-auto px-4 py-3"
            onMouseUp={handleSourceSelect}
          >
            <textarea
              value={sourceText}
              onChange={e => setSourceText(e.target.value)}
              placeholder="粘贴原文内容，或等待所有者添加..."
              className="w-full h-full resize-none bg-transparent text-sm text-text-primary placeholder:text-text-placeholder focus:outline-none whitespace-pre-wrap font-sans leading-relaxed select-text"
              style={{ minHeight: '200px' }}
            />
          </div>

          {/* Source panel footer */}
          <div className="px-4 py-2 border-t border-border bg-surface-secondary/50">
            <div className="flex items-center justify-between text-xs text-text-tertiary mb-2">
              <span>{sourceText.length.toLocaleString()} 字符 · {sourceText.split(/\s+/).filter(Boolean).length.toLocaleString()} 词</span>
            </div>
            <div className="flex gap-2">
              <button
                onClick={handleAiTranslate}
                disabled={isStreaming || !sourceText.trim()}
                className="flex-1 flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isStreaming ? (
                  <>
                    <div className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    翻译中...
                  </>
                ) : (
                  <>
                    <Sparkles className="w-3.5 h-3.5" /> AI 翻译
                  </>
                )}
              </button>
              {isStreaming && (
                <button
                  onClick={handleStopStreaming}
                  className="px-3 py-1.5 text-xs font-medium text-text-primary border border-border rounded-button hover:bg-surface-secondary transition-button"
                >
                  停止
                </button>
              )}
            </div>
          </div>
        </div>

        {/* ===== Center Panel: Translation Editor ===== */}
        <div className="flex flex-col flex-1 overflow-hidden bg-surface">
          <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-surface-secondary/50">
            <span className="text-xs font-medium text-text-secondary">译文</span>
            <div className="flex items-center gap-2">
              {isDirty && (
                <span className="text-xs text-yellow flex items-center gap-1">
                  <Clock className="w-3 h-3" /> 未保存
                </span>
              )}
              {lastSaved && (
                <span className="text-xs text-text-tertiary">
                  已保存 {lastSaved.toLocaleTimeString()}
                </span>
              )}
              <button
                onClick={handleCopyEditor}
                disabled={!editorText}
                className="p-1 rounded text-text-tertiary hover:text-text-primary transition-button disabled:opacity-30"
                title="复制译文"
              >
                <Copy className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={handleClearEditor}
                disabled={!editorText}
                className="p-1 rounded text-text-tertiary hover:text-red transition-button disabled:opacity-30"
                title="清空"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Editor textarea */}
          <div className="flex-1 relative overflow-hidden">
            <textarea
              value={displayText}
              onChange={e => handleEditorChange(e.target.value)}
              placeholder="输入翻译结果，或使用 AI 翻译辅助..."
              className="w-full h-full resize-none bg-transparent text-text-primary text-[15px] leading-relaxed placeholder:text-text-placeholder focus:outline-none px-4 py-3"
              style={{ fontFamily: 'var(--font-sans)' }}
            />
            {/* Streaming cursor overlay */}
            {isStreaming && (
              <div className="absolute bottom-3 right-4 flex items-center gap-2 text-xs text-accent">
                <span className="streaming-cursor" />
                <span>AI 正在翻译...</span>
              </div>
            )}
          </div>

          {/* Editor footer */}
          <div className="px-4 py-2 border-t border-border bg-surface-secondary/50">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4 text-xs text-text-tertiary">
                <span>{translatedChars.toLocaleString()} 字符 · {translatedWords.toLocaleString()} 词</span>
                {sourceChars > 0 && (
                  <span>进度 {progressPercent}%</span>
                )}
              </div>
              <div className="flex items-center gap-2 text-xs text-text-tertiary">
                <span className="hidden sm:inline">Ctrl+Enter AI 翻译</span>
                <button
                  onClick={handleAiTranslate}
                  disabled={isStreaming || !chapter.sourceText}
                  className="sm:hidden flex items-center gap-1 px-2 py-1 text-xs text-accent hover:text-accent-hover transition-button disabled:opacity-50"
                >
                  <Sparkles className="w-3 h-3" /> AI 翻译
                </button>
              </div>
            </div>
            {/* Progress bar */}
            {sourceChars > 0 && (
              <div className="mt-2 w-full h-1 bg-surface-secondary rounded-full overflow-hidden">
                <div
                  className="h-full bg-gradient-brand transition-all duration-300"
                  style={{ width: `${progressPercent}%` }}
                />
              </div>
            )}
          </div>
        </div>

        {/* ===== Right Panel: Sidebar (Comments/Reference/Settings) ===== */}
        <div className="hidden md:flex md:flex-col md:w-[25%] lg:w-[20%] border-l border-border bg-surface overflow-hidden">
          {/* Tab switcher */}
          <div className="px-3 pt-3 border-b border-border">
            <Tabs tabs={sidebarTabs} activeTab={sidebarTab} onChange={v => setSidebarTab(v as typeof sidebarTab)} />
          </div>

          {/* Anchored source text indicator */}
          {anchoredSourceText && (
            <div className="px-3 py-2 border-b border-border bg-accent-bg">
              <div className="flex items-center justify-between">
                <span className="text-xs text-accent">已锚定原文</span>
                <button onClick={handleClearAnchor} className="text-xs text-text-tertiary hover:text-text-primary">
                  清除
                </button>
              </div>
              <p className="text-xs text-text-secondary mt-1 line-clamp-2">{anchoredSourceText}</p>
            </div>
          )}

          {/* Tab content */}
          <div className="flex-1 overflow-y-auto">
            {/* Comments Tab */}
            {sidebarTab === 'comments' && (
              <div className="flex flex-col h-full">
                <div className="flex-1 overflow-y-auto px-3 py-3 space-y-3">
                  {comments.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-12 text-text-tertiary">
                      <MessageSquare className="w-8 h-8 mb-2" />
                      <p className="text-xs">暂无评论</p>
                      <p className="text-xs mt-1">选中原文可锚定评论</p>
                    </div>
                  ) : (
                    comments.map(comment => (
                      <CommentItem key={comment.id} comment={comment} depth={0} />
                    ))
                  )}
                </div>
                {/* Comment input */}
                <div className="px-3 py-2 border-t border-border">
                  <div className="flex gap-2">
                    <input
                      value={commentInput}
                      onChange={e => setCommentInput(e.target.value)}
                      placeholder="输入评论..."
                      className="flex-1 px-2 py-1.5 text-xs border border-border rounded bg-white focus:outline-none focus:border-accent"
                      onKeyDown={e => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          handleSendComment();
                        }
                      }}
                    />
                    <button
                      onClick={handleSendComment}
                      disabled={!commentInput.trim()}
                      className="px-2 py-1.5 text-xs font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-button disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      发送
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Reference Tab */}
            {sidebarTab === 'reference' && (
              <div className="px-3 py-4">
                <div className="flex flex-col items-center justify-center py-12 text-text-tertiary">
                  <BookMarked className="w-8 h-8 mb-2" />
                  <p className="text-xs">术语参考</p>
                  <p className="text-xs mt-1 text-center">项目术语表将显示在这里</p>
                </div>
              </div>
            )}

            {/* Settings Tab */}
            {sidebarTab === 'settings' && (
              <div className="px-3 py-4 space-y-4">
                {/* Language info */}
                <div>
                  <h4 className="text-xs font-medium text-text-primary mb-2">语言设置</h4>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">源语言</span>
                      <span className="text-text-primary">自动检测</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">目标语言</span>
                      <span className="text-text-primary">{getLangName(targetLang)}</span>
                    </div>
                  </div>
                </div>

                {/* Progress */}
                <div>
                  <h4 className="text-xs font-medium text-text-primary mb-2">翻译进度</h4>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">状态</span>
                      <Badge color={CHAPTER_STATUS_CONFIG[chapter.status]?.color || 'gray'}>
                        {CHAPTER_STATUS_CONFIG[chapter.status]?.label || chapter.status}
                      </Badge>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">完成度</span>
                      <span className="text-text-primary font-mono">{progressPercent}%</span>
                    </div>
                    <div className="w-full h-1.5 bg-surface-secondary rounded-full overflow-hidden">
                      <div
                        className="h-full bg-accent transition-all duration-300"
                        style={{ width: `${progressPercent}%` }}
                      />
                    </div>
                  </div>
                </div>

                {/* Stats */}
                <div>
                  <h4 className="text-xs font-medium text-text-primary mb-2">统计</h4>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">原文字符</span>
                      <span className="text-text-primary font-mono">{sourceChars.toLocaleString()}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">原文词数</span>
                      <span className="text-text-primary font-mono">{sourceWords.toLocaleString()}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">译文字符</span>
                      <span className="text-text-primary font-mono">{translatedChars.toLocaleString()}</span>
                    </div>
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-text-tertiary">译文词数</span>
                      <span className="text-text-primary font-mono">{translatedWords.toLocaleString()}</span>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ==================== Mobile Bottom Tab Bar ==================== */}
      <div className="md:hidden flex items-center border-t border-border bg-surface-secondary">
        {sidebarTabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setSidebarTab(tab.key as typeof sidebarTab)}
            className={`flex-1 flex flex-col items-center gap-0.5 py-2 text-xs transition-colors ${
              sidebarTab === tab.key ? 'text-accent' : 'text-text-tertiary'
            }`}
          >
            {tab.key === 'comments' && <MessageSquare className="w-4 h-4" />}
            {tab.key === 'reference' && <BookMarked className="w-4 h-4" />}
            {tab.key === 'settings' && <Settings className="w-4 h-4" />}
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      {/* ==================== Mobile Sidebar Overlay ==================== */}
      <div className="md:hidden fixed bottom-0 left-0 right-0 z-50 bg-surface border-t border-border"
        style={{ display: sidebarTab ? 'block' : 'none' }}
      >
        {/* Mobile comment input area shown when comments tab is active */}
        {sidebarTab === 'comments' && (
          <div className="px-3 py-2">
            <div className="flex gap-2">
              <input
                value={commentInput}
                onChange={e => setCommentInput(e.target.value)}
                placeholder="输入评论..."
                className="flex-1 px-2 py-1.5 text-xs border border-border rounded bg-white focus:outline-none focus:border-accent"
                onKeyDown={e => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    handleSendComment();
                  }
                }}
              />
              <button
                onClick={handleSendComment}
                disabled={!commentInput.trim()}
                className="px-2 py-1.5 text-xs font-medium text-white bg-accent rounded-button disabled:opacity-50"
              >
                发送
              </button>
            </div>
            {/* Comment list */}
            <div className="max-h-48 overflow-y-auto mt-2 space-y-2">
              {comments.length === 0 ? (
                <p className="text-center text-xs text-text-tertiary py-4">暂无评论</p>
              ) : (
                comments.map(comment => (
                  <CommentItem key={comment.id} comment={comment} depth={0} />
                ))
              )}
            </div>
          </div>
        )}

        {sidebarTab === 'reference' && (
          <div className="px-3 py-6 text-center text-xs text-text-tertiary">
            <BookMarked className="w-6 h-6 mx-auto mb-2" />
            术语参考功能即将上线
          </div>
        )}

        {sidebarTab === 'settings' && (
          <div className="px-3 py-4 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="text-text-tertiary">源语言</span>
              <span className="text-text-primary">自动检测</span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-text-tertiary">目标语言</span>
              <span className="text-text-primary">{getLangName(targetLang)}</span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-text-tertiary">状态</span>
              <Badge color={CHAPTER_STATUS_CONFIG[chapter.status]?.color || 'gray'}>
                {CHAPTER_STATUS_CONFIG[chapter.status]?.label || chapter.status}
              </Badge>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-text-tertiary">进度</span>
              <span className="text-text-primary font-mono">{progressPercent}%</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export { CollabWorkspace };
