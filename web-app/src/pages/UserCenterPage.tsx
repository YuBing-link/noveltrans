import { useState, useEffect } from 'react';
import { Routes, Route } from 'react-router-dom';
import { Sidebar } from '../components/layout/Sidebar';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/ui/Toast';
import { userApi } from '../api/user';
import { authApi } from '../api/auth';
import { apiKeyApi } from '../api/apiKeys';
import { preferencesApi } from '../api/preferences';
import { SubscriptionStatusPage } from './SubscriptionStatusPage';
import type { UserStatistics, UserQuota, ApiKeyItem, UserPreferences } from '../api/types';
import { Copy, Eye, EyeOff, Plus, Trash2 } from 'lucide-react';
import { Pagination } from '../components/ui/Pagination';
import { useTranslation } from 'react-i18next';

function UserCenterPage() {
  const { user, refreshUser } = useAuth();
  const { t } = useTranslation();

  return (
    <div className="w-full" style={{ minHeight: 'calc(100vh - 140px)' }}>
      <div className="border border-border/50 rounded-lg overflow-hidden flex flex-col" style={{ minHeight: 'calc(100vh - 140px)' }}>
        {/* User header */}
        <div className="flex items-center gap-4 px-5 py-4 border-b border-border/50 bg-surface-secondary">
          <div className="w-10 h-10 rounded-full bg-accent text-white flex items-center justify-center text-sm font-semibold flex-shrink-0">
            {user?.username?.slice(0, 1).toUpperCase() || 'U'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-[15px] font-semibold text-text-primary truncate">{user?.username || t('common.loading')}</p>
            <p className="text-[12px] text-text-tertiary">{user?.email}</p>
          </div>
          <span className="text-[12px] px-2 py-0.5 bg-surface rounded-full text-text-tertiary uppercase flex-shrink-0">{user?.userLevel || 'FREE'}</span>
        </div>

        <div className="flex flex-col lg:flex-row flex-1 min-h-0">
          {/* Sidebar */}
          <div className="lg:w-48 border-b lg:border-b-0 lg:border-r border-border/50">
            <Sidebar />
          </div>

          {/* Content */}
          <div className="flex-1 min-w-0">
            <Routes>
              <Route index element={<ProfileTab user={user!} refreshUser={refreshUser} />} />
              <Route path="stats" element={<StatsTab />} />
              <Route path="quota" element={<QuotaTab />} />
              <Route path="api-keys" element={<ApiKeysTab />} />
              <Route path="preferences" element={<PreferencesTab />} />
              <Route path="subscription" element={<SubscriptionStatusPage />} />
            </Routes>
          </div>
        </div>
      </div>
    </div>
  );
}

import type { AuthUser } from '../context/AuthContext';

function ProfileTab({ user, refreshUser }: { user: AuthUser; refreshUser: () => void }) {
  const { success, error: toastError } = useToast();
  const { t } = useTranslation();
  const [username, setUsername] = useState(user?.username || '');
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    if (!username.trim()) { toastError(t('userCenter.profile.errors.usernameRequired')); return; }
    setSaving(true);
    try { await authApi.updateProfile({ username }); success(t('userCenter.profile.success')); refreshUser(); }
    catch { toastError(t('userCenter.profile.failed')); }
    finally { setSaving(false); }
  };

  return (
    <div className="p-5 space-y-4">
      <h2 className="text-[15px] font-semibold text-text-primary">{t('userCenter.tabs.profile')}</h2>
      <div className="space-y-3">
        <div>
          <label className="text-[13px] font-medium text-text-secondary block mb-1">{t('userCenter.profile.username')}</label>
          <input value={username} onChange={e => setUsername(e.target.value)} className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
        </div>
        <div>
          <label className="text-[13px] font-medium text-text-secondary block mb-1">{t('userCenter.profile.email')}</label>
          <input value={user?.email || ''} disabled className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-tertiary rounded-input border border-border" />
        </div>
        <button onClick={handleSave} disabled={saving} className="px-5 py-1.5 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover disabled:opacity-30 transition-colors">
          {saving ? t('userCenter.profile.saving') : t('userCenter.profile.save')}
        </button>
      </div>
    </div>
  );
}

function StatsTab() {
  const { t } = useTranslation();
  const [stats, setStats] = useState<UserStatistics | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => { userApi.getStatistics().then(r => setStats(r.data)).finally(() => setLoading(false)); }, []);

  if (loading) return <div className="flex justify-center py-12 text-text-tertiary text-[13px]">{t('userCenter.stats.loading')}</div>;

  const metrics = stats ? [
    { label: t('userCenter.stats.totalTranslations'), value: stats.totalTranslations },
    { label: t('userCenter.stats.textTranslation'), value: stats.textTranslations },
    { label: t('userCenter.stats.documentTranslation'), value: stats.documentTranslations },
    { label: t('userCenter.stats.totalCharacters'), value: stats.totalCharacters.toLocaleString() },
    { label: t('userCenter.stats.thisWeek'), value: stats.weekTranslations },
    { label: t('userCenter.stats.thisMonth'), value: stats.monthTranslations },
  ] : [];

  return (
    <div className="p-5">
      <h2 className="text-[15px] font-semibold text-text-primary mb-4">{t('userCenter.tabs.stats')}</h2>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        {metrics.map(m => (
          <div key={m.label} className="p-4 rounded-lg bg-surface-secondary text-center">
            <p className="text-[20px] font-semibold text-text-primary">{m.value}</p>
            <p className="text-[12px] text-text-tertiary mt-1">{m.label}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function QuotaTab() {
  const { t } = useTranslation();
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => { userApi.getQuota().then(r => setQuota(r.data)).finally(() => setLoading(false)); }, []);

  if (loading) return <div className="flex justify-center py-12 text-text-tertiary text-[13px]">{t('common.loading')}</div>;
  if (!quota) return null;

  const usagePercent = quota.monthlyChars > 0 ? (quota.usedThisMonth / quota.monthlyChars) * 100 : 0;

  return (
    <div className="p-5 space-y-3">
      <h2 className="text-[15px] font-semibold text-text-primary">{t('userCenter.tabs.quota')}</h2>
      <div className="flex items-center justify-between text-[13px]">
        <span className="text-text-tertiary">{t('userCenter.quota.level')}</span>
        <span className="text-text-primary font-semibold uppercase">{quota.userLevel}</span>
      </div>
      <div>
        <div className="flex items-center justify-between text-[13px] mb-2">
          <span className="text-text-tertiary">{t('userCenter.quota.monthlyUsage')}</span>
          <span className="text-text-primary">{quota.usedThisMonth} / {quota.monthlyChars}</span>
        </div>
        <div className="w-full bg-surface-secondary rounded-full h-2 overflow-hidden">
          <div className="h-full bg-accent rounded-full transition-all" style={{ width: `${Math.min(usagePercent, 100)}%` }} />
        </div>
      </div>
      <div className="flex items-center justify-between text-[13px]">
        <span className="text-text-tertiary">{t('userCenter.quota.remaining')}</span>
        <span className="text-text-primary font-medium">{quota.remainingChars}</span>
      </div>
      <div className="flex items-center justify-between text-[13px]">
        <span className="text-text-tertiary">{t('userCenter.quota.concurrencyLimit')}</span>
        <span className="text-text-primary">{quota.concurrencyLimit}</span>
      </div>
    </div>
  );
}

function ApiKeysTab() {
  const { success, error: toastError } = useToast();
  const { t } = useTranslation();
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [showKey, setShowKey] = useState<number | null>(null);
  const [newKeyName, setNewKeyName] = useState('');
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => { loadKeys(); }, [page]); // eslint-disable-line react-hooks/exhaustive-deps

  const loadKeys = async () => {
    setLoading(true);
    try {
      const { data } = await apiKeyApi.getList({ page, pageSize: 20 });
      setKeys(data.list || []);
      setTotalPages(data.totalPages || 1);
    }
    catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleCreate = async () => {
    if (!newKeyName.trim()) { toastError(t('userCenter.apiKeys.namePlaceholder')); return; }
    try { await apiKeyApi.create(newKeyName.trim()); success(t('userCenter.apiKeys.createSuccess')); setNewKeyName(''); setPage(1); loadKeys(); }
    catch { toastError(t('userCenter.apiKeys.createFailed')); }
  };

  const handleDelete = async (id: number) => {
    try { await apiKeyApi.delete(id); success(t('common.delete')); loadKeys(); }
    catch { toastError(t('userCenter.apiKeys.deleteFailed')); }
  };

  const handleCopy = async (key: string) => {
    try { await navigator.clipboard.writeText(key); success(t('userCenter.apiKeys.copied')); }
    catch { toastError(t('userCenter.apiKeys.copyFailed')); }
  };

  return (
    <div className="p-5">
      <h2 className="text-[15px] font-semibold text-text-primary mb-4">API Keys</h2>
      <div className="flex gap-2 mb-4">
        <input value={newKeyName} onChange={e => setNewKeyName(e.target.value)} placeholder={t('userCenter.apiKeys.namePlaceholder')} className="flex-1 px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
        <button onClick={handleCreate} className="inline-flex items-center gap-1 px-4 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover transition-colors">
          <Plus className="w-4 h-4" /> {t('userCenter.apiKeys.created')}
        </button>
      </div>
      {loading ? (
        <p className="text-[13px] text-text-tertiary text-center py-4">{t('common.loading')}</p>
      ) : keys.length === 0 ? (
        <p className="text-[13px] text-text-tertiary text-center py-4">{t('common.noData')}</p>
      ) : (
        <div className="space-y-2">
          {keys.map(key => (
            <div key={key.id} className="p-3 rounded-lg border border-border/50">
              <div className="flex items-center gap-2 mb-1">
                <span className={`w-2 h-2 rounded-full flex-shrink-0 ${key.active ? 'bg-green' : 'bg-gray-300'}`} />
                <span className="text-[13px] font-medium text-text-primary">{key.name}</span>
                <span className={`text-[12px] px-2 py-0.5 rounded-full ${key.active ? 'bg-green-bg text-green' : 'bg-surface-secondary text-text-tertiary'}`}>
                  {key.active ? t('userCenter.apiKeys.active') : t('userCenter.apiKeys.inactive')}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <code className="flex-1 text-[13px] font-mono text-text-primary truncate">
                  {showKey === key.id ? key.apiKey : key.apiKey.slice(0, 8) + '••••'}
                </code>
                <span className="text-[12px] text-text-tertiary whitespace-nowrap">{t('userCenter.apiKeys.usage', { count: key.totalUsage })}</span>
                <button onClick={() => setShowKey(showKey === key.id ? null : key.id)} className="p-1 text-text-tertiary hover:text-text-primary">
                  {showKey === key.id ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
                <button onClick={() => handleCopy(key.apiKey)} className="p-1 text-text-tertiary hover:text-text-primary"><Copy className="w-4 h-4" /></button>
                <button onClick={() => handleDelete(key.id)} className="p-1 text-text-tertiary hover:text-red"><Trash2 className="w-4 h-4" /></button>
              </div>
            </div>
          ))}
        </div>
      )}
      <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
    </div>
  );
}

function PreferencesTab() {
  const { success, error: toastError } = useToast();
  const { t } = useTranslation();
  const [prefs, setPrefs] = useState<UserPreferences | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => { preferencesApi.get().then(r => setPrefs(r.data)).finally(() => setLoading(false)); }, []);

  const handleSave = async () => {
    if (!prefs) return;
    setSaving(true);
    try { await preferencesApi.update(prefs); success(t('userCenter.profile.success')); }
    catch { toastError(t('userCenter.profile.failed')); }
    finally { setSaving(false); }
  };

  if (loading) return <div className="flex justify-center py-12 text-text-tertiary text-[13px]">{t('common.loading')}</div>;
  if (!prefs) return null;

  return (
    <div className="p-5 space-y-4">
      <div className="bg-surface-secondary rounded-lg p-4 mb-4">
        <h2 className="text-[15px] font-semibold text-text-primary mb-3">{t('userCenter.tabs.preferences')}</h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between py-2">
            <div>
              <span className="text-[13px] font-medium text-text-primary block">{t('userCenter.preferences.enableGlossary')}</span>
              <span className="text-[12px] text-text-tertiary">{t('userCenter.preferences.enableGlossaryDesc')}</span>
            </div>
            <button
              onClick={() => setPrefs(p => p ? { ...p, enableGlossary: !p.enableGlossary } : null)}
              className={`w-11 h-6 rounded-full transition-colors relative ${prefs.enableGlossary ? 'bg-accent' : 'bg-surface-secondary border border-border'}`}
            >
              <div className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${prefs.enableGlossary ? 'translate-x-5' : 'translate-x-0.5'}`} />
            </button>
          </div>
          <div className="border-t border-border/50"></div>
          <div className="flex items-center justify-between py-2">
            <div>
              <span className="text-[13px] font-medium text-text-primary block">{t('userCenter.preferences.enableCache')}</span>
              <span className="text-[12px] text-text-tertiary">{t('userCenter.preferences.enableCacheDesc')}</span>
            </div>
            <button
              onClick={() => setPrefs(p => p ? { ...p, enableCache: !p.enableCache } : null)}
              className={`w-11 h-6 rounded-full transition-colors relative ${prefs.enableCache ? 'bg-accent' : 'bg-surface-secondary border border-border'}`}
            >
              <div className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${prefs.enableCache ? 'translate-x-5' : 'translate-x-0.5'}`} />
            </button>
          </div>
        </div>
      </div>
      <div className="flex justify-end">
        <button
          onClick={handleSave}
          disabled={saving}
          className="px-6 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover disabled:opacity-30 transition-colors shadow-sm"
        >
          {saving ? t('userCenter.profile.saving') : t('userCenter.preferences.save')}
        </button>
      </div>
    </div>
  );
}

export { UserCenterPage };
