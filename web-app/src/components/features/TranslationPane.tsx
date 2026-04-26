import { useTranslation } from 'react-i18next';

interface TranslationPaneProps {
  text: string;
  onChange: (text: string) => void;
  onClear: () => void;
  placeholder?: string;
  maxLength?: number;
  remainingChars?: number;
}

function TranslationPane({
  text, onChange, onClear, placeholder,
  maxLength, remainingChars,
}: TranslationPaneProps) {
  const { t } = useTranslation();
  const usagePercent = maxLength && maxLength > 0
    ? Math.min(100, (text.length / maxLength) * 100)
    : 0;
  const isNearLimit = remainingChars !== undefined && remainingChars < Infinity && remainingChars < 1000;

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-[14px] font-semibold text-text-primary">{t('home.title')}</h3>
        <div className="flex items-center gap-3">
          {text && (
            <button
              onClick={onClear}
              className="text-[12px] text-text-tertiary hover:text-accent transition-colors"
            >
              {t('home.actions.clear')}
            </button>
          )}
          <span className="text-[12px] text-text-tertiary font-mono">
            {text.length.toLocaleString()}
            {maxLength && (
              <span className="text-text-placeholder"> / {maxLength.toLocaleString()}</span>
            )}
          </span>
        </div>
      </div>

      {/* Text area */}
      <textarea
        value={text}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder ?? t('home.inputPlaceholder')}
        maxLength={maxLength}
        className="
          flex-1 w-full resize-none bg-transparent
          text-text-primary text-[17px] leading-relaxed
          placeholder:text-text-placeholder
          focus:outline-none
          min-h-[280px]
        "
      />

      {/* Quota bar */}
      {remainingChars !== undefined && remainingChars < Infinity && (
        <div className="mt-3 pt-3 border-t border-divider">
          <div className="w-full h-1 rounded-full overflow-hidden bg-gray-100 dark:bg-gray-200">
            <div
              className="h-full rounded-full bg-gradient-brand transition-all duration-500"
              style={{ width: `${Math.min(100, usagePercent)}%` }}
            />
          </div>
          <p className={`text-[11px] mt-1 font-mono ${isNearLimit ? 'text-yellow' : 'text-text-tertiary'}`}>
            {t('home.quota.remaining')}: {remainingChars.toLocaleString()}
          </p>
        </div>
      )}
    </div>
  );
}

export { TranslationPane };
