import { useState, useCallback, useRef, useEffect } from 'react';
import { ArrowLeftRight, Copy, Trash2, Volume2 } from 'lucide-react';
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
    <div className="w-full">
      {/* Flat translation panel */}
      <div className="border border-border/50 rounded-lg overflow-hidden">
        <div className="flex flex-col lg:flex-row">
          {/* Source Panel */}
          <div className="flex-1 min-h-[480px] lg:border-r border-border/50 flex flex-col">
            {/* Language selector row */}
            <div className="flex items-center gap-3 px-5 py-3 border-b border-border/50">
              <select
                value={sourceLang}
                onChange={e => setSourceLang(e.target.value)}
                className="appearance-none bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors"
              >
                {SUPPORTED_LANGUAGES.map(lang => (
                  <option key={lang.code} value={lang.code}>{lang.name}</option>
                ))}
              </select>

              <button
                onClick={handleSwap}
                disabled={sourceLang === 'auto'}
                className={`w-7 h-7 flex items-center justify-center rounded-full transition-all ${
                  sourceLang === 'auto'
                    ? 'text-gray-300 cursor-not-allowed'
                    : 'text-text-tertiary hover:text-accent hover:bg-accent-bg'
                }`}
              >
                <ArrowLeftRight className="w-4 h-4" />
              </button>

              <select
                value={targetLang}
                onChange={e => setTargetLang(e.target.value)}
                className="appearance-none bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors"
              >
                {SUPPORTED_LANGUAGES.filter(l => l.code !== 'auto').map(lang => (
                  <option key={lang.code} value={lang.code}>{lang.name}</option>
                ))}
              </select>
            </div>

            {/* Textarea */}
            <div className="flex-1 p-5 flex flex-col">
              <textarea
                value={sourceText}
                onChange={e => setSourceText(e.target.value)}
                placeholder="请输入文本"
                maxLength={maxChars}
                className="flex-1 w-full resize-none bg-transparent text-text-primary text-[17px] leading-relaxed placeholder:text-text-placeholder/60 focus:outline-none"
              />
              <div className="flex items-center justify-between pt-3 text-[12px] text-text-tertiary">
                <span>{sourceText.length.toLocaleString()} 字符</span>
                {sourceText && (
                  <button onClick={() => setSourceText('')} className="hover:text-accent transition-colors">清空</button>
                )}
              </div>
            </div>
          </div>

          {/* Result Panel */}
          <div className="flex-1 min-h-[480px] flex flex-col">
            {/* Engine selector row */}
            <div className="flex items-center justify-between px-5 py-3 border-b border-border/50">
              <select className="appearance-none bg-transparent text-accent text-[14px] font-medium cursor-pointer hover:text-accent-hover transition-colors">
                <option>AI 大模型翻译</option>
                <option>快速翻译</option>
              </select>
              {(translatedText || loading) && (
                <div className="flex items-center gap-0.5">
                  {translatedText && !loading && (
                    <>
                      <button onClick={handleCopy} className="p-1.5 rounded-full text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" title="复制">
                        <Copy className="w-4 h-4" />
                      </button>
                      <button onClick={handleSpeak} className="p-1.5 rounded-full text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" title="朗读">
                        <Volume2 className="w-4 h-4" />
                      </button>
                    </>
                  )}
                  <button onClick={handleClearResult} className="p-1.5 rounded-full text-text-tertiary hover:text-red hover:bg-red-bg transition-colors" title="清空">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>

            {/* Result content */}
            <div className="flex-1 p-5 relative">
              {loading ? (
                <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap">{translatedText}<span className="text-accent">▋</span></p>
              ) : translatedText ? (
                <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap pr-16">{translatedText}</p>
              ) : (
                <div className="flex items-center justify-center h-full text-text-placeholder/60">
                  <p className="text-[15px]">翻译结果</p>
                </div>
              )}
              {costTime > 0 && !loading && (
                <span className="absolute bottom-2 right-4 text-[11px] text-text-tertiary font-mono">{(costTime / 1000).toFixed(1)}s</span>
              )}
            </div>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="flex justify-end px-5 py-3 border-t border-border/50 bg-surface-secondary">
          <button
            onClick={handleTranslate}
            disabled={!sourceText.trim() || loading}
            className={`
              px-6 py-1.5 text-[13px] font-medium text-white rounded-button
              bg-gradient-brand transition-all duration-200
              hover:opacity-90 active:scale-95
              disabled:opacity-30 disabled:cursor-not-allowed
              ${loading ? 'animate-pulse' : ''}
            `}
          >
            {loading ? '翻译中...' : 'AI翻译'}
          </button>
        </div>
      </div>
    </div>
  );
}

export { HomePage };
