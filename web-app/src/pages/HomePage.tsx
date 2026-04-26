import { useState, useCallback, useRef, useEffect } from 'react';
import { ArrowLeftRight, Copy, Trash2, Volume2, Sparkles, History, Zap } from 'lucide-react';
import { useToast } from '../components/ui/Toast';
import { translateApi } from '../api/translate';
import { userApi } from '../api/user';
import { LANGUAGE_CODES } from '../api/types';
import type { UserQuota } from '../api/types';
import { useTranslation } from 'react-i18next';

function HomePage() {
  const { t } = useTranslation();
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
          setQuota(null);
        });
    } else {
      setQuota(null);
    }
  }, []);

  const sameLangDisabled = sourceLang !== 'auto' && sourceLang === targetLang;

  const handleTranslate = useCallback(async () => {
    if (sameLangDisabled) { info(t('home.errors.sameLanguage')); return; }
    if (!sourceText.trim()) { info(t('home.errors.noText')); return; }
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
  }, [sourceText, sourceLang, targetLang, engine, sameLangDisabled, t]);

  const handleSwap = useCallback(() => {
    if (sourceLang === 'auto') return;
    setSourceLang(targetLang);
    setTargetLang(sourceLang);
    if (translatedText) {
      setSourceText(translatedText);
      setTranslatedText(sourceText);
    }
  }, [sourceLang, targetLang, sourceText, translatedText]);

  const handleCopy = async () => {
    try { await navigator.clipboard.writeText(translatedText); success(t('translationResult.copied')); }
    catch { toastError(t('translationResult.copyFailed')); }
  };
  const handleSpeak = () => {
    if (!translatedText || !window.speechSynthesis) return;
    window.speechSynthesis.speak(new SpeechSynthesisUtterance(translatedText));
  };
  const handleClearResult = () => setTranslatedText('');
  const handleClearSource = () => setSourceText('');

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

  const maxChars = quota ? quota.remainingChars : undefined;
  const progressPercent = loading && translatedText.length > 0
    ? Math.min(100, Math.round((translatedText.length / Math.max(1, sourceText.length)) * 100))
    : 0;

  return (
    <div className="py-12 pb-20">
      <div className="mb-8 text-center">
        <h1 className="text-[32px] font-bold text-text-primary mb-2 tracking-tight">
          NovelTrans {t('home.title')}
        </h1>
        <p className="text-[15px] text-text-secondary">
          {t('home.subtitle')}
        </p>
      </div>

      <div className="border border-border rounded-xl shadow-sm bg-surface overflow-hidden">
        <div className="flex flex-col lg:flex-row">
          {/* Source Panel */}
          <div className="flex-1 min-h-[480px] flex flex-col">
            <div className="flex items-center gap-4 px-6 h-[52px] border-b border-border bg-surface-secondary/50">
              <select
                value={sourceLang}
                onChange={(e) => setSourceLang(e.target.value)}
                className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none whitespace-nowrap border-none"
                title={t('home.language.selectSource')}
              >
                {LANGUAGE_CODES.map(code => (
                  <option key={code} value={code}>{t(`common.languages.${code}`)}</option>
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
                title={t('home.actions.swapLanguages')}
              >
                <ArrowLeftRight className="w-4 h-4" />
              </button>

              <select
                value={targetLang}
                onChange={(e) => setTargetLang(e.target.value)}
                className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none whitespace-nowrap border-none"
                title={t('home.language.selectTarget')}
              >
                {LANGUAGE_CODES.filter(c => c !== 'auto').map(code => (
                  <option key={code} value={code}>{t(`common.languages.${code}`)}</option>
                ))}
              </select>
            </div>

            <div className="flex-1 p-6 flex flex-col">
              <textarea
                value={sourceText}
                onChange={e => setSourceText(e.target.value)}
                placeholder={t('home.inputPlaceholder')}
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
                    {t('home.actions.clear')}
                  </button>
                )}
              </div>
            </div>
          </div>

          <div className="hidden lg:block w-px bg-gradient-to-b from-transparent via-border to-transparent mx-0" />

          {/* Result Panel */}
          <div className="flex-1 min-h-[480px] flex flex-col">
            <div className="flex items-center justify-between px-6 h-[52px] border-b border-border bg-surface-secondary/50">
              <select
                value={engine}
                onChange={(e) => setEngine(e.target.value as 'ai' | 'fast')}
                className="bg-transparent text-text-primary text-[14px] font-medium cursor-pointer hover:text-accent transition-colors focus:outline-none whitespace-nowrap border-none"
                title={t('home.language.selectEngine')}
              >
                <option value="ai">{t('languageSelector.expert')}</option>
                <option value="fast">{t('languageSelector.fast')}</option>
              </select>

              {(translatedText || loading) && (
                <div className="flex items-center gap-1">
                  {translatedText && !loading && (
                    <>
                      <button
                        onClick={handleCopy}
                        className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors"
                        title={t('home.resultActions.copy')}
                      >
                        <Copy className="w-4 h-4" />
                      </button>
                      <button
                        onClick={handleSpeak}
                        className="p-2 rounded-lg text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors"
                        title={t('home.resultActions.speak')}
                      >
                        <Volume2 className="w-4 h-4" />
                      </button>
                    </>
                  )}
                  <button
                    onClick={handleClearResult}
                    className="p-2 rounded-lg text-text-tertiary hover:text-red hover:bg-red-bg transition-colors"
                    title={t('home.resultActions.clear')}
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>

            <div className="flex-1 p-6 relative overflow-y-auto">
              {loading ? (
                <div className="space-y-2">
                  <p className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap break-words">
                    {translatedText || <span className="text-text-placeholder/40">{t('home.actions.translating')}</span>}
                    <span className="text-accent inline-block animate-pulse ml-0.5">▋</span>
                  </p>
                  {progressPercent > 0 && (
                    <div className="mt-4">
                      <div className="flex items-center justify-between text-[12px] text-text-tertiary mb-1">
                        <span className="flex items-center gap-1">
                          {engine === 'ai' ? <Sparkles className="w-3 h-3" /> : <Zap className="w-3 h-3" />}
                          {t('home.progress')}
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
                        <span>{t('home.qualities.accurate')}</span>
                      </>
                    ) : (
                      <>
                        <Zap className="w-3.5 h-3.5 text-yellow" />
                        <span>{t('home.qualities.fast')}</span>
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
                    <p className="text-[15px] text-text-secondary font-medium mb-1">{t('home.result.placeholder')}</p>
                    <p className="text-[13px] text-text-tertiary">{t('home.result.hint')}</p>
                  </div>
                  <div className="flex items-center gap-2 text-[12px] text-text-tertiary">
                    <History className="w-3.5 h-3.5" />
                    <span>{t('home.notification')}</span>
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

        <div className="flex items-center justify-between px-6 py-3 border-t border-border bg-surface-secondary">
          <div className="flex items-center gap-4 text-[12px] text-text-tertiary">
            {quota ? (
              <div className="flex items-center gap-2">
                <span>{t('home.quota.remaining')}:</span>
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
                <span className="text-text-placeholder">{t('home.quota.loginHint')}</span>
                <a
                  href="/login"
                  className="text-accent hover:text-accent-hover transition-colors font-medium"
                >
                  {t('home.quota.loginNow')}
                </a>
              </div>
            )}
            <span className="hidden sm:inline text-text-placeholder">|</span>
            <span className="hidden sm:inline text-text-placeholder text-[11px]">
              {t('home.shortcut')}
            </span>
          </div>
          <button
            onClick={handleTranslate}
            disabled={!sourceText.trim() || loading || sameLangDisabled}
            title={sameLangDisabled ? t('home.errors.sameLanguage') : ''}
            className={`
              px-8 py-2 text-[14px] font-semibold text-white rounded-lg
              bg-gradient-brand transition-all duration-200 shadow-sm
              hover:shadow-md hover:opacity-90 active:scale-95
              disabled:opacity-30 disabled:cursor-not-allowed disabled:shadow-none
              ${loading ? 'animate-pulse' : ''}
            `}
          >
            {loading ? t('home.actions.translating') : t('home.actions.startTranslate')}
          </button>
        </div>
      </div>
    </div>
  );
}

export { HomePage };
