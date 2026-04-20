interface Tab {
  key: string;
  label: string;
}

interface TabsProps {
  tabs: Tab[];
  activeTab: string;
  onChange: (key: string) => void;
}

function Tabs({ tabs, activeTab, onChange }: TabsProps) {
  return (
    <div className="inline-flex gap-1 bg-surface-secondary rounded-button p-1">
      {tabs.map(tab => (
        <button
          key={tab.key}
          onClick={() => onChange(tab.key)}
          className={`
            rounded-button transition-button
            ${tab.key === activeTab
              ? 'bg-white text-text-primary shadow-subtle px-4 py-2 text-sm font-semibold'
              : 'text-text-secondary hover:text-text-primary px-4 py-2 text-sm font-medium'
            }
          `}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

export { Tabs };
export type { Tab };
