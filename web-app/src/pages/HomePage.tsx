import { useState, useCallback, useRef, useEffect } from 'react';
import { ArrowLeftRight, Copy, Trash2, Volume2, Sparkles, History, Zap } from 'lucide-react';
import { useToast } from '../components/ui/Toast';
import { translateApi } from '../api/translate';
import { userApi } from '../api/user';
import { SUPPORTED_LANGUAGES } from '../api/types';
import type { UserQuota } from '../api/types';

function HomePage() {
  const { error: toastError, info, success } = useToast();
  const [sourceText, setSourceText] = useState('');
  const [translatedText, setTranslatedText] = useState('');
  const [sourceLang, setSourceLang] = useState('auto');
  const [targetLang, setTargetLang] = useState('zh');
  const [engine, setEngine] = useState<'ai' | 'fast'>('ai');
  const [loading, setLoading] = useState(false);
  const [costTime, setCostTime] = useState(0);
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const streamAbortRef = useRef(false);

  useEffect(() => {
    const token = localStorage.getItem('authToken');
    if (token) {
      userApi.getQuota()
        .then(({ data }) => setQuota(data))
        .catch((err) => {
          console.warn('获取配额失败:', err);
          // 即使失败也设置为 null，避免 undefined
          setQuota(null);
        });
    } else {
      // 未登录用户也设置为 null
      setQuota(null);
    }
  }, []);

  const sameLangDisabled = sourceLang !== 'auto' && sourceLang === targetLang;

  const handleTranslate = useCallback(async () => {
    if (sameLangDisabled) { info('源语言和目标语言不能相同'); return; }
    if (!sourceText.trim()) { info('请输入要翻译的文本'); return; }
    streamAbortRef.current = false;
    setLoading(true);
    setTranslatedText('');
    setCostTime(0);
    const startTime = Date.now();

    await translateApi.streamTranslate(
      { text: sourceText, sourceLang, targetLang, engine, mode: engine === 'ai' ? 'expert' : 'fast' },
      (chunk) => { if (!streamAbortRef.current) setTranslatedText(prev => prev + chunk); },
      () => { setCostTime(Date.now() - startTime); setLoading(false); },
      (err) => { toastError(err); setLoading(false); },
    );
  }, [sourceText, sourceLang, targetLang, engine, sameLangDisabled]);

  const handleSwap = useCallback(() => {
    if (sourceLang === 'auto') return;
    setSourceLang(targetLang);
    setTargetLang(sourceLang);
    setSourceText(translatedText);
    setTranslatedText(sourceText);
  }, [sourceLang, targetLang, sourceText, translatedText]);

  const handleCopy = async () => {
    try { await navigator.clipboard.writeText(translatedText); success('已复制'); }
    catch { toastError('复制失败'); }
  };
  const handleSpeak = () => {
    if (!translatedText || !window.speechSynthesis) return;
    window.speechSynthesis.speak(new SpeechSynthesisUtterance(translatedText));
  };
  const handleClearResult = () => setTranslatedText('');
  const handleClearSource = () => setSourceText('');

  // Keyboard shortcut: Ctrl+Enter to translate
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter' && sourceText.trim() && !loading) {
        e.preventDefault();
        handleTranslate();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [sourceText, loading, handleTranslate]);

  const maxChars = quota ? Math.max(0, quota.remainingChars + sourceText.length) : undefined;
  const progressPercent = sourceText.length > 0 && translatedText.length > 0 
    ? Math.min(100, Math.round((translatedText.length / sourceText.length) * 100))
    : 0;

  return (
    <div className="w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Page Header */}
      <div className="mb-8 text-center">
        <h1 className="text-[32px] font-bold text-text-primary mb-2 tracking-tight">
          AI 智能翻译
        </h1>
        <p className="text-[15px] text-text-secondary">
          支持多语言实时互译，精准理解上下文语境
        </p>
      </div>

      {/* Translation Panel */}
      <div className="border border-border rounded-xl shadow-sm bg-surface overflow-hidden">
        <div className="flex flex-col lg:flex-row">
          {/* Source Panel */}
          <div className="flex-1 min-h-[480px] flex flex-col">
            {/* Language selector row */}
            <div className="flex items-center gap-4 px-6 h-[52px] border-b border-border bg-surface-secondary/50">
              <select
                value={sourceLang}
                onChange={(e) => setSourceLang(e.target.value)}
                className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none whitespace-nowrap border-none"
              >
                {SUPPORTED_LANGUAGES.map(lang => (
                  <option key={lang.code} value={lang.code}>{lang.name}</option>
                ))}
              </select>

              <button
                onClick={handleSwap}
                disabled={sourceLang === 'auto'}
                className={`flex-shrink-0 w-8 h-8 flex items-center justify-center rounded-full transition-all ${
                  sourceLang === 'auto'
                    ? 'text-gray-300 cursor-not-allowed'
                    : 'text-text-tertiary hover:text-accent hover:bg-accent-bg'
                }`}
                title="交换语言"
              >
                <ArrowLeftRight className="w-4 h-4" />
              </button>

              <select
                value={targetLang}
                onChange={(e) => setTargetLang(e.target.value)}
                className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none whitespace-nowrap border-none"
              >
                {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(lang => (
                  <option key={lang.code} value={lang.code}>{lang.name}</option>
                ))}
              </select>
            </div>

            {/* Textarea */}
            <div className="flex-1 p-6 flex flex-col">
              <textarea
                value={sourceText}
                onChange={e => setSourceText(e.target.value)}
                placeholder="请输入要翻译的文本...（支持 Ctrl+Enter 快速翻译）"
                maxLength={maxChars}
                className="flex-1 w-full resize-none bg-transparent text-text-primary text-[17px] leading-relaxed placeholder:text-text-placeholder focus:outline-none"
              />
              <div className="flex items-center justify-between pt-4 text-[12px] text-text-tertiary">
                <span className="font-mono">{sourceText.length.toLocaleString()} 字符</span>
                {sourceText && (
                  <button 
                    onClick={handleClearSource} 
                    className="hover:text-accent transition-colors font-medium flex items-center gap-1"
                  >
                    <Trash2 className="w-3 h-3" />
                    清空
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* Divider */}
          <div className="hidden lg:block w-px bg-gradient-to-b from-transparent via-border to-transparent mx-0" />

          {/* Result Panel */}
          <div className="flex-1 min-h-[480px] flex flex-col">
            {/* Result header with engine selector and actions */}
            <div className="flex items-center justify-between px-6 h-[52px] border-b border-border bg-surface-secondary/50">
              <select
                value={engine}
                onChange={(e) => setEngine(e.target.value as 'ai' | 'fast')}
                className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none whitespace-nowrap border-none"
                title="选择翻译引擎"
              >
                <option value="ai">专家模式</option>
                <option value="fast">快速翻译</option>
              </select>
              
              {(translatedText || loading) && (
                <div className="flex items-center gap-1">
                  {translatedText && !loading && (
                    <>
                      <button 
                        onClick={handleCopy} 
                        className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" 
                        title="复制译文 (Ctrl+C)"
                      >
                        <Copy className="w-4 h-4" />
                      </button>
                      <button 
                        onClick={handleSpeak} 
                        className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" 
                        title="朗读译文"
                      >
                        <Volume2 className="w-4 h-4" />
                      </button>
                    </>
                  )}
                  <button 
                    onClick={handleClearResult} 
                    className="p-2 rounded-lg text-text-tertiary hover:text-red hover:bg-red-bg transition-colors" 
                    title="清空译文"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>

            {/* Result content */}
            <div className="flex-1 p-6 relative overflow-y-auto">
              {loading ? (
                <div className="space-y-2">
                  <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap break-words">
                    {translatedText || <span className="text-text-placeholder/40">正在翻译中...</span>}
                    <span className="text-accent inline-block animate-pulse ml-0.5">▋</span>
                  </p>
                  {progressPercent > 0 && (
                    <div className="mt-4">
                      <div className="flex items-center justify-between text-[12px] text-text-tertiary mb-1">
                        <span className="flex items-center gap-1">
                          {engine === 'ai' ? <Sparkles className="w-3 h-3" /> : <Zap className="w-3 h-3" />}
                          翻译进度
                        </span>
                        <span className="font-mono">{progressPercent}%</span>
                      </div>
                      <div className="w-full h-1.5 bg-surface-secondary rounded-full overflow-hidden">
                        <div 
                          className="h-full bg-gradient-brand transition-all duration-300 ease-out"
                          style={{ width: `${progressPercent}%` }}
                        />
                      </div>
                    </div>
                  )}
                </div>
              ) : translatedText ? (
                <div>
                  <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap pr-16">{translatedText}</p>
                  <div className="mt-4 flex items-center gap-2 text-[12px] text-text-tertiary">
                    {engine === 'ai' ? (
                      <>
                        <Sparkles className="w-3.5 h-3.5 text-accent" />
                        <span>专家模式 · 更精准自然</span>
                      </>
                    ) : (
                      <>
                        <Zap className="w-3.5 h-3.5 text-yellow" />
                        <span>快速翻译 · 速度优先</span>
                      </>
                    )}
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center h-full text-center space-y-4">
                  <div className="w-16 h-16 rounded-full bg-surface-secondary flex items-center justify-center">
                    <Sparkles className="w-8 h-8 text-text-tertiary" />
                  </div>
                  <div>
                    <p className="text-[15px] text-text-secondary font-medium mb-1">翻译结果将显示在这里</p>
                    <p className="text-[13px] text-text-tertiary">在左侧输入文本，点击"开始翻译"</p>
                  </div>
                  <div className="flex items-center gap-2 text-[12px] text-text-tertiary">
                    <History className="w-3.5 h-3.5" />
                    <span>提示：可在历史记录中查看过往翻译</span>
                  </div>
                </div>
              )}
              {costTime > 0 && !loading && (
                <span className="absolute bottom-4 right-6 text-[11px] text-text-tertiary font-mono bg-surface-secondary px-2 py-1 rounded flex items-center gap-1">
                  <Zap className="w-3 h-3" />
                  {(costTime / 1000).toFixed(1)}s
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="flex items-center justify-between px-6 py-3 border-t border-border bg-surface-secondary">
          <div className="flex items-center gap-4 text-[12px] text-text-tertiary">
            {quota ? (
              <div className="flex items-center gap-2">
                <span>剩余字符:</span>
                <span className={`font-mono font-medium ${
                  quota.remainingChars < 1000 ? 'text-red' : 'text-text-primary'
                }`}>
                  {quota.remainingChars.toLocaleString()}
                </span>
                <span className="text-text-placeholder">/</span>
                <span className="font-mono text-text-secondary">
                  {quota.monthlyChars.toLocaleString()}
                </span>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <span className="text-text-placeholder">登录后可查看字符配额</span>
                <a 
                  href="/login" 
                  className="text-accent hover:text-accent-hover transition-colors font-medium"
                >
                  立即登录
                </a>
              </div>
            )}
            <span className="hidden sm:inline text-text-placeholder">|</span>
            <span className="hidden sm:inline text-text-placeholder text-[11px]">
              快捷键: Ctrl+Enter 翻译
            </span>
          </div>
          <button
            onClick={handleTranslate}
            disabled={!sourceText.trim() || loading || sameLangDisabled}
            title={sameLangDisabled ? '源语言和目标语言不能相同' : ''}
            className={`
              px-8 py-2 text-[14px] font-semibold text-white rounded-lg
              bg-gradient-brand transition-all duration-200 shadow-sm
              hover:shadow-md hover:opacity-90 active:scale-95
              disabled:opacity-30 disabled:cursor-not-allowed disabled:shadow-none
              ${loading ? 'animate-pulse' : ''}
            `}
          >
            {loading ? '翻译中...' : '开始翻译'}
          </button>
        </div>
      </div>
    </div>
  );
}

export { HomePage };
