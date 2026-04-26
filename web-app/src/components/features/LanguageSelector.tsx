import { ArrowLeftRight, Zap, Brain } from 'lucide-react';
import { LANGUAGE_CODES, TRANSLATION_ENGINES } from '../../api/types';
import { useTranslation } from 'react-i18next';

interface LanguageSelectorProps {
  sourceLang: string;
  targetLang: string;
  onSourceChange: (lang: string) => void;
  onTargetChange: (lang: string) => void;
  onSwap: () => void;
  engine?: string;
  onEngineChange?: (engine: string) => void;
  mode?: string;
  onModeChange?: (mode: string) => void;
}

function LanguageSelector({
  sourceLang, targetLang, onSourceChange, onTargetChange, onSwap,
  engine, onEngineChange, mode, onModeChange,
}: LanguageSelectorProps) {
  const { t } = useTranslation();
  const modeOptions = [
    { value: 'fast', label: t('languageSelector.fast'), icon: Zap },
    { value: 'expert', label: t('languageSelector.expert'), icon: Brain },
  ];

  return (
    <div className="w-full bg-white dark:bg-gray-50 border-b border-gradient-b">
      <div className="max-w-7xl mx-auto px-6 flex items-center justify-between h-14">
        {/* Left: Language selectors + swap */}
        <div className="flex items-center gap-3 flex-1 min-w-0">
          {/* Source language */}
          <select
            value={sourceLang}
            onChange={e => onSourceChange(e.target.value)}
            className="
              appearance-none bg-transparent text-text-primary text-[14px] font-medium
              border border-border rounded-input px-3 py-1.5
              focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/20
              cursor-pointer hover:bg-surface-secondary transition-colors
              min-w-28
            "
          >
            {LANGUAGE_CODES.map(code => (
              <option key={code} value={code}>{t(`common.languages.${code}`)}</option>
            ))}
          </select>

          {/* Swap button */}
          <button
            onClick={onSwap}
            className={`
              flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center
              transition-all duration-200
              ${sourceLang === 'auto'
                ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
                : 'text-text-tertiary hover:text-accent hover:bg-accent-bg hover:scale-110'
              }
            `}
            title={t('languageSelector.switch')}
            disabled={sourceLang === 'auto'}
          >
            <ArrowLeftRight className="w-4 h-4" />
          </button>

          {/* Target language */}
          <select
            value={targetLang}
            onChange={e => onTargetChange(e.target.value)}
            className="
              appearance-none bg-transparent text-text-primary text-[14px] font-medium
              border border-border rounded-input px-3 py-1.5
              focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/20
              cursor-pointer hover:bg-surface-secondary transition-colors
              min-w-28
            "
          >
            {LANGUAGE_CODES.filter(c => c !== 'auto').map(code => (
              <option key={code} value={code}>{t(`common.languages.${code}`)}</option>
            ))}
          </select>
        </div>

        {/* Right: Engine + Mode */}
        <div className="flex items-center gap-3 flex-shrink-0">
          {/* Engine selector */}
          {onEngineChange && engine !== undefined && (
            <select
              value={engine}
              onChange={e => onEngineChange(e.target.value)}
              className="
                appearance-none bg-surface-secondary text-text-secondary text-[13px]
                border border-border rounded-input px-3 py-1.5
                focus:outline-none focus:border-accent
                cursor-pointer hover:text-text-primary transition-colors
              "
            >
              {TRANSLATION_ENGINES.map(eng => (
                <option key={eng.value} value={eng.value}>{t(`engines.${eng.label}`, { defaultValue: eng.label })}</option>
              ))}
            </select>
          )}

          {/* Mode toggle */}
          {onModeChange && mode !== undefined && (
            <div className="inline-flex items-center gap-0.5 bg-surface-secondary rounded-button p-0.5">
              {modeOptions.map(m => {
                const Icon = m.icon;
                return (
                  <button
                    key={m.value}
                    onClick={() => onModeChange(m.value)}
                    className={`
                      inline-flex items-center gap-1.5 px-3 py-1.5 text-[12px] font-semibold
                      rounded-button transition-all duration-200
                      ${mode === m.value
                        ? 'bg-white dark:bg-gray-50 text-text-primary shadow-subtle'
                        : 'text-text-secondary hover:text-text-primary'
                      }
                    `}
                  >
                    <Icon className="w-3.5 h-3.5" />
                    {m.label}
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export { LanguageSelector };
