import { useState, useCallback, useRef, useEffect } from 'react';
import { FileText, Sparkles, ArrowLeftRight, Copy, Trash2, Volume2, Zap } from 'lucide-react';
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
  const [loading, setLoading] = useState(false);
  const [costTime, setCostTime] = useState(0);
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const streamAbortRef = useRef(false);

  useEffect(() => {
    if (localStorage.getItem('authToken')) {
      userApi.getQuota().then(({ data }) => setQuota(data)).catch(() => {});
    }
  }, []);

  const handleTranslate = useCallback(async () => {
    if (!sourceText.trim()) { info('请输入要翻译的文本'); return; }
    streamAbortRef.current = false;
    setLoading(true);
    setTranslatedText('');
    setCostTime(0);
    const startTime = Date.now();

    await translateApi.streamTranslate(
      { text: sourceText, sourceLang, targetLang, engine: 'ai', mode: 'fast' },
      (chunk) => { if (!streamAbortRef.current) setTranslatedText(prev => prev + chunk); },
      () => { setCostTime(Date.now() - startTime); setLoading(false); },
      (err) => { toastError(err); setLoading(false); },
    );
  }, [sourceText, sourceLang, targetLang]);

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

  const remainingChars = quota ? quota.remainingChars : undefined;
  const maxChars = quota ? Math.max(0, quota.remainingChars + sourceText.length) : undefined;

  return (
    <div className="mx-auto px-6">
      {/* Hero */}
      <div className="text-center pt-10 pb-6">
        <h1 className="text-[28px] sm:text-[32px] font-bold text-text-primary tracking-display mb-2">
          智能翻译
        </h1>
        <p className="text-[15px] text-text-secondary">
          支持 13+ 种语言，AI 驱动的高质量翻译
        </p>
      </div>

      {/* Tabs */}
      <div className="flex justify-center mb-6">
        <div className="inline-flex items-center gap-1 p-1 bg-white/80 backdrop-blur-sm rounded-button border border-border/50 shadow-subtle">
          <button className="inline-flex items-center gap-2 px-4 py-2 text-[14px] font-medium text-gradient-brand bg-accent-bg rounded-button">
            <Sparkles className="w-4 h-4" />
            翻译文本
          </button>
          <button className="inline-flex items-center gap-2 px-4 py-2 text-[14px] font-medium text-text-secondary hover:text-text-primary hover:bg-surface-secondary rounded-button transition-colors">
            <FileText className="w-4 h-4" />
            翻译文件
          </button>
          <button className="inline-flex items-center gap-2 px-4 py-2 text-[14px] font-medium text-text-secondary hover:text-text-primary hover:bg-surface-secondary rounded-button transition-colors">
            <Zap className="w-4 h-4" />
            使用 API
          </button>
        </div>
      </div>

      {/* Translation card */}
      <div className="mx-auto max-w-4xl bg-white rounded-card shadow-card overflow-hidden border border-border/50">
        {/* Language bar */}
        <div className="flex items-center justify-center gap-4 py-3 border-b border-border/50">
          <select
            value={sourceLang}
            onChange={e => setSourceLang(e.target.value)}
            className="appearance-none bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors min-w-20 text-center"
          >
            {SUPPORTED_LANGUAGES.map(lang => (
              <option key={lang.code} value={lang.code}>{lang.name}</option>
            ))}
          </select>

          <button
            onClick={handleSwap}
            disabled={sourceLang === 'auto'}
            className={`
              w-9 h-9 rounded-full flex items-center justify-center transition-all
              ${sourceLang === 'auto'
                ? 'text-gray-300 cursor-not-allowed'
                : 'text-text-tertiary hover:text-accent hover:bg-accent-bg hover:scale-110'
              }
            `}
          >
            <ArrowLeftRight className="w-4 h-4" />
          </button>

          <select
            value={targetLang}
            onChange={e => setTargetLang(e.target.value)}
            className="appearance-none bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors min-w-20 text-center"
          >
            {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(lang => (
              <option key={lang.code} value={lang.code}>{lang.name}</option>
            ))}
          </select>
        </div>

        {/* Panels */}
        <div className="flex flex-col lg:flex-row">
          {/* Source */}
          <div className="flex-1 p-6 lg:border-r border-border/50 min-h-[400px]">
            <div className="flex items-center justify-between mb-3">
              <span className="text-[12px] font-semibold uppercase tracking-wider text-text-tertiary">原文</span>
              <div className="flex items-center gap-2">
                {sourceText && (
                  <button onClick={() => setSourceText('')} className="text-[12px] text-text-tertiary hover:text-accent transition-colors">清空</button>
                )}
                <span className="text-[12px] text-text-tertiary font-mono">
                  {sourceText.length.toLocaleString()}
                  {maxChars && <span className="text-text-placeholder"> / {maxChars.toLocaleString()}</span>}
                </span>
              </div>
            </div>
            <textarea
              value={sourceText}
              onChange={e => setSourceText(e.target.value)}
              placeholder="请输入要翻译的文本..."
              maxLength={maxChars}
              className="w-full resize-none bg-transparent text-text-primary text-[17px] leading-relaxed placeholder:text-text-placeholder focus:outline-none min-h-[280px]"
            />
            {remainingChars !== undefined && remainingChars < Infinity && (
              <div className="mt-3 pt-3 border-t border-divider">
                <div className="flex items-center justify-between text-[11px] mb-1">
                  <span className="text-text-tertiary">本月配额</span>
                  <span className="text-text-tertiary font-mono">{remainingChars.toLocaleString()} 剩余</span>
                </div>
                <div className="w-full h-1 rounded-full overflow-hidden bg-gray-100">
                  <div
                    className="h-full rounded-full bg-gradient-brand transition-all duration-500"
                    style={{ width: `${Math.min(100, maxChars ? ((maxChars - remainingChars + sourceText.length) / maxChars) * 100 : 0)}%` }}
                  />
                </div>
              </div>
            )}
          </div>

          {/* Result */}
          <div className="flex-1 p-6 min-h-[400px]">
            <div className="flex items-center justify-between mb-3">
              <span className="text-[12px] font-semibold uppercase tracking-wider text-text-tertiary">译文</span>
              {(translatedText || loading) && (
                <div className="flex items-center gap-0.5">
                  {translatedText && !loading && (
                    <>
                      <button onClick={handleCopy} className="p-1.5 rounded-button text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" title="复制">
                        <Copy className="w-4 h-4" />
                      </button>
                      <button onClick={handleSpeak} className="p-1.5 rounded-button text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" title="朗读">
                        <Volume2 className="w-4 h-4" />
                      </button>
                    </>
                  )}
                  <button onClick={handleClearResult} className="p-1.5 rounded-button text-text-tertiary hover:text-red hover:bg-red-bg transition-colors" title="清空">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
            <div className="relative min-h-[280px]">
              {loading ? (
                <div className="flex items-start gap-2">
                  <div className="flex items-center gap-2 text-accent mt-1">
                    <Sparkles className="w-4 h-4 animate-pulse" />
                    <span className="text-[13px]">翻译中...</span>
                  </div>
                  {translatedText && (
                    <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap mt-2">{translatedText}</p>
                  )}
                </div>
              ) : translatedText ? (
                <>
                  <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap pr-16">{translatedText}</p>
                  {costTime > 0 && (
                    <span className="absolute bottom-0 right-0 text-[11px] text-text-tertiary font-mono">{(costTime / 1000).toFixed(1)}s</span>
                  )}
                </>
              ) : (
                <div className="flex flex-col items-center justify-center h-[280px] text-text-placeholder">
                  <p className="text-[15px]">翻译结果将显示在这里</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Translate button */}
      <div className="flex justify-center mt-5 pb-10">
        <button
          onClick={handleTranslate}
          disabled={!sourceText.trim() || loading}
          className={`
            px-10 py-2.5 rounded-button font-semibold text-[14px]
            bg-gradient-brand text-white
            translate-btn-glow transition-all duration-300
            hover:-translate-y-0.5 hover:scale-[1.02]
            active:scale-95
            disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:translate-y-0 disabled:hover:scale-100
            disabled:shadow-none
            ${loading ? 'animate-pulse' : ''}
          `}
        >
          {loading ? '翻译中...' : '翻译'}
        </button>
      </div>
    </div>
  );
}

export { HomePage };
