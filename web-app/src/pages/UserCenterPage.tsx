import { useState, useEffect } from 'react';
import { Routes, Route } from 'react-router-dom';
import { PageLayout } from '../components/layout/PageLayout';
import { Sidebar } from '../components/layout/Sidebar';
import { Card } from '../components/ui/Card';
import { Avatar } from '../components/ui/Feedback';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Spinner } from '../components/ui/Feedback';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../components/ui/Toast';
import { userApi } from '../api/user';
import { authApi } from '../api/auth';
import { apiKeyApi } from '../api/apiKeys';
import { preferencesApi } from '../api/preferences';
import type { UserStatistics, UserQuota, ApiKeyItem, UserPreferences } from '../api/types';
import { Copy, Eye, EyeOff, Plus, Trash2 } from 'lucide-react';

function UserCenterPage() {
  const { user, refreshUser } = useAuth();

  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="mb-10">
        <h1 className="text-[28px] sm:text-[32px] font-semibold text-text-primary tracking-display leading-[1.07] mb-2">
          个人中心
        </h1>
        <p className="text-text-secondary text-[15px]">管理您的账号信息和翻译数据</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        <div className="lg:col-span-1">
          <Card variant="subtle">
            <div className="p-6 flex flex-col items-center">
              <Avatar name={user?.username || 'U'} size="lg" />
              <h3 className="mt-3 text-[15px] font-semibold text-text-primary">{user?.username || '用户'}</h3>
              <p className="text-[12px] text-text-tertiary">{user?.email}</p>
            </div>
            <div className="px-4 pb-4">
              <Sidebar />
            </div>
          </Card>
        </div>
        <div className="lg:col-span-3">
          <Routes>
            <Route index element={<ProfileTab user={user} refreshUser={refreshUser} />} />
            <Route path="stats" element={<StatsTab />} />
            <Route path="quota" element={<QuotaTab />} />
            <Route path="api-keys" element={<ApiKeysTab />} />
            <Route path="preferences" element={<PreferencesTab />} />
          </Routes>
        </div>
      </div>
    </PageLayout>
  );
}

function ProfileTab({ user, refreshUser }: { user: any; refreshUser: () => void }) {
  const { success, error: toastError } = useToast();
  const [username, setUsername] = useState(user?.username || '');
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      await authApi.updateProfile({ username });
      success('保存成功');
      refreshUser();
    } catch { toastError('保存失败'); }
    finally { setSaving(false); }
  };

  return (
    <Card>
      <div className="p-6">
        <h2 className="text-[17px] font-semibold text-text-primary mb-6">个人信息</h2>
        <div className="space-y-4">
          <Input label="用户名" value={username} onChange={e => setUsername(e.target.value)} />
          <Input label="邮箱" value={user?.email || ''} disabled />
          <div className="flex items-center gap-4">
            <span className="text-[13px] text-text-tertiary">账号等级:</span>
            <span className="text-[13px] font-medium text-text-primary uppercase">{user?.userLevel || 'FREE'}</span>
          </div>
          <Button onClick={handleSave} loading={saving}>保存修改</Button>
        </div>
      </div>
    </Card>
  );
}

function StatsTab() {
  const [stats, setStats] = useState<UserStatistics | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    userApi.getStatistics().then(r => setStats(r.data)).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-12"><Spinner /></div>;

  const metrics = stats ? [
    { label: '总翻译次数', value: stats.totalTranslations },
    { label: '文本翻译', value: stats.textTranslations },
    { label: '文档翻译', value: stats.documentTranslations },
    { label: '总字符数', value: stats.totalCharacters.toLocaleString() },
    { label: '本周翻译', value: stats.weekTranslations },
    { label: '本月翻译', value: stats.monthTranslations },
  ] : [];

  return (
    <Card>
      <div className="p-6">
        <h2 className="text-[17px] font-semibold text-text-primary mb-6">统计数据</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          {metrics.map(m => (
            <div key={m.label} className="p-4 rounded-card bg-surface-secondary text-center">
              <p className="text-[24px] font-semibold text-text-primary">{m.value}</p>
              <p className="text-[12px] text-text-tertiary mt-1">{m.label}</p>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}

function QuotaTab() {
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    userApi.getQuota().then(r => setQuota(r.data)).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex justify-center py-12"><Spinner /></div>;
  if (!quota) return null;

  const usagePercent = quota.monthlyChars > 0 ? (quota.usedThisMonth / quota.monthlyChars) * 100 : 0;

  return (
    <Card>
      <div className="p-6">
        <h2 className="text-[17px] font-semibold text-text-primary mb-6">配额用量</h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-[13px] text-text-tertiary">账号等级</span>
            <span className="text-[13px] font-semibold text-text-primary uppercase">{quota.userLevel}</span>
          </div>
          <div>
            <div className="flex items-center justify-between text-[13px] mb-2">
              <span className="text-text-tertiary">本月用量</span>
              <span className="text-text-primary">{quota.usedThisMonth} / {quota.monthlyChars}</span>
            </div>
            <div className="w-full bg-surface-secondary rounded-full h-2 overflow-hidden">
              <div className="h-full bg-accent rounded-full transition-all" style={{ width: `${Math.min(usagePercent, 100)}%` }} />
            </div>
          </div>
          <div className="flex items-center justify-between text-[13px]">
            <span className="text-text-tertiary">剩余配额</span>
            <span className="text-text-primary font-medium">{quota.remainingChars}</span>
          </div>
          <div className="flex items-center justify-between text-[13px]">
            <span className="text-text-tertiary">并发限制</span>
            <span className="text-text-primary">{quota.concurrencyLimit}</span>
          </div>
          <div className="pt-4 border-t border-border/50">
            <p className="text-[13px] text-text-tertiary mb-3">模式等效</p>
            <div className="flex items-center justify-between text-[13px]">
              <span className="text-text-tertiary">快速模式</span>
              <span className="text-text-primary">{quota.fastModeEquivalent}</span>
            </div>
            <div className="flex items-center justify-between text-[13px] mt-2">
              <span className="text-text-tertiary">专家模式</span>
              <span className="text-text-primary">{quota.expertModeEquivalent}</span>
            </div>
            <div className="flex items-center justify-between text-[13px] mt-2">
              <span className="text-text-tertiary">团队模式</span>
              <span className="text-text-primary">{quota.teamModeEquivalent}</span>
            </div>
          </div>
        </div>
      </div>
    </Card>
  );
}

function ApiKeysTab() {
  const { success, error: toastError } = useToast();
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [showKey, setShowKey] = useState<number | null>(null);
  const [newKeyName, setNewKeyName] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadKeys(); }, []);

  const loadKeys = async () => {
    setLoading(true);
    try {
      const { data } = await apiKeyApi.getList();
      setKeys(data || []);
    } catch { /* ignore */ }
    finally { setLoading(false); }
  };

  const handleCreate = async () => {
    if (!newKeyName.trim()) { toastError('请输入名称'); return; }
    try {
      await apiKeyApi.create(newKeyName.trim());
      success('创建成功');
      setNewKeyName('');
      loadKeys();
    } catch { toastError('创建失败'); }
  };

  const handleDelete = async (id: number) => {
    try {
      await apiKeyApi.delete(id);
      success('已删除');
      loadKeys();
    } catch { toastError('删除失败'); }
  };

  const handleCopy = async (key: string) => {
    try {
      await navigator.clipboard.writeText(key);
      success('已复制');
    } catch { toastError('复制失败'); }
  };

  return (
    <Card>
      <div className="p-6">
        <h2 className="text-[17px] font-semibold text-text-primary mb-6">API Keys</h2>
        <div className="flex gap-2 mb-6">
          <Input
            value={newKeyName}
            onChange={e => setNewKeyName(e.target.value)}
            placeholder="Key 名称"
            className="flex-1"
          />
          <Button onClick={handleCreate}>
            <Plus className="w-4 h-4" /> 创建
          </Button>
        </div>
        {loading ? (
          <div className="text-center py-4 text-text-tertiary">加载中...</div>
        ) : keys.length === 0 ? (
          <p className="text-[13px] text-text-tertiary text-center py-4">暂无 API Key</p>
        ) : (
          <div className="space-y-3">
            {keys.map(key => (
              <div key={key.id} className="p-4 rounded-card border border-border/50">
                <div className="flex items-center gap-2 mb-2">
                  <span className={`w-2 h-2 rounded-full flex-shrink-0 ${key.active ? 'bg-green' : 'bg-gray-300'}`} />
                  <span className="text-[13px] font-medium text-text-primary">{key.name}</span>
                  <span className={`text-[12px] px-2 py-0.5 rounded-button ${key.active ? 'bg-green-bg text-green' : 'bg-surface-secondary text-text-tertiary'}`}>
                    {key.active ? '活跃' : '未激活'}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <code className="flex-1 text-[13px] font-mono text-text-primary truncate">
                    {showKey === key.id ? key.apiKey : key.apiKey.slice(0, 8) + '••••'}
                  </code>
                  <span className="text-[12px] text-text-tertiary whitespace-nowrap">
                    使用 {key.totalUsage} 次{key.lastUsedAt ? ` · ${new Date(key.lastUsedAt).toLocaleDateString('zh-CN')}` : ' · 未使用'}
                  </span>
                  <span className="text-[12px] text-text-tertiary whitespace-nowrap">
                    创建于 {new Date(key.createdAt).toLocaleDateString('zh-CN')}
                  </span>
                  <button onClick={() => setShowKey(showKey === key.id ? null : key.id)} className="p-1 text-text-tertiary hover:text-text-primary">
                    {showKey === key.id ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                  <button onClick={() => handleCopy(key.apiKey)} className="p-1 text-text-tertiary hover:text-text-primary">
                    <Copy className="w-4 h-4" />
                  </button>
                  <button onClick={() => handleDelete(key.id)} className="p-1 text-text-tertiary hover:text-red">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}

function PreferencesTab() {
  const { success, error: toastError } = useToast();
  const [prefs, setPrefs] = useState<UserPreferences | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    preferencesApi.get().then(r => setPrefs(r.data)).finally(() => setLoading(false));
  }, []);

  const handleSave = async () => {
    if (!prefs) return;
    setSaving(true);
    try {
      await preferencesApi.update(prefs);
      success('保存成功');
    } catch { toastError('保存失败'); }
    finally { setSaving(false); }
  };

  if (loading) return <div className="flex justify-center py-12"><Spinner /></div>;
  if (!prefs) return null;

  return (
    <Card>
      <div className="p-6">
        <h2 className="text-[17px] font-semibold text-text-primary mb-6">偏好设置</h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-[13px] text-text-primary">启用术语表</span>
            <button
              onClick={() => setPrefs(p => p ? { ...p, enableGlossary: !p.enableGlossary } : null)}
              className={`w-11 h-6 rounded-full transition-colors ${prefs.enableGlossary ? 'bg-accent' : 'bg-gray-200'}`}
            >
              <div className={`w-5 h-5 rounded-full bg-white shadow transition-transform ${prefs.enableGlossary ? 'translate-x-5' : 'translate-x-0.5'}`} />
            </button>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-[13px] text-text-primary">启用缓存</span>
            <button
              onClick={() => setPrefs(p => p ? { ...p, enableCache: !p.enableCache } : null)}
              className={`w-11 h-6 rounded-full transition-colors ${prefs.enableCache ? 'bg-accent' : 'bg-gray-200'}`}
            >
              <div className={`w-5 h-5 rounded-full bg-white shadow transition-transform ${prefs.enableCache ? 'translate-x-5' : 'translate-x-0.5'}`} />
            </button>
          </div>
          <Button onClick={handleSave} loading={saving} className="mt-2">保存设置</Button>
        </div>
      </div>
    </Card>
  );
}

export { UserCenterPage };
