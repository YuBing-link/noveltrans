import { Copy, Trash2, Volume2, Sparkles } from 'lucide-react';
import { useToast } from '../ui/Toast';
import { useTranslation } from 'react-i18next';

interface TranslationResultProps {
  text: string;
  loading: boolean;
  onClear: () => void;
  costTime?: number;
}

function TranslationResultPane({ text, loading, onClear, costTime }: TranslationResultProps) {
  const { toast } = useToast();
  const { t } = useTranslation();

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      toast('success', t('translationResult.copied'));
    } catch {
      toast('error', t('translationResult.copyFailed'));
    }
  };

  const handleSpeak = () => {
    if (!text || !window.speechSynthesis) return;
    const utterance = new SpeechSynthesisUtterance(text);
    window.speechSynthesis.speak(utterance);
  };

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-[14px] font-semibold text-text-primary">{t('translationResult.title')}</h3>
        {(text || loading) && (
          <div className="flex items-center gap-0.5">
            {text && !loading && (
              <>
                <button onClick={handleCopy} className="p-1.5 rounded-button text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" title={t('translationResult.copy')}>
                  <Copy className="w-4 h-4" />
                </button>
                <button onClick={handleSpeak} className="p-1.5 rounded-button text-text-tertiary hover:text-accent hover:bg-accent-bg transition-colors" title={t('translationResult.speak')}>
                  <Volume2 className="w-4 h-4" />
                </button>
              </>
            )}
            <button onClick={onClear} className="p-1.5 rounded-button text-text-tertiary hover:text-red hover:bg-red-bg transition-colors" title={t('translationResult.clear')}>
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        )}
      </div>

      {/* Content */}
      <div className="flex-1 relative">
        {loading ? (
          <div className="flex items-center gap-2 text-accent">
            <Sparkles className="w-4 h-4 animate-pulse" />
            <span className="text-[14px]">{t('translationResult.translating')}</span>
            {text && (
              <div className="text-[17px] leading-relaxed text-text-primary whitespace-pre-wrap mt-3 streaming-cursor">
                {text}
              </div>
            )}
          </div>
        ) : text ? (
          <>
            <textarea
              readOnly
              value={text}
              className="w-full h-full resize-none bg-transparent text-text-primary text-[17px] leading-relaxed focus:outline-none absolute inset-0"
            />
            {costTime !== undefined && costTime > 0 && (
              <div className="absolute bottom-0 right-0 text-[11px] text-text-tertiary font-mono">
                {(costTime / 1000).toFixed(1)}s
              </div>
            )}
          </>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-text-placeholder">
            <p className="text-[15px] font-medium">{t('home.result.placeholder')}</p>
          </div>
        )}
      </div>
    </div>
  );
}

export { TranslationResultPane };
