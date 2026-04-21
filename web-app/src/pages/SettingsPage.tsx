import { useState } from 'react';
import { useTheme } from '../hooks/useTheme';
import { useToast } from '../components/ui/Toast';
import { authApi } from '../api/auth';
import { preferencesApi } from '../api/preferences';
import { Moon, Sun } from 'lucide-react';

function SettingsPage() {
  const { theme, toggleTheme } = useTheme();
  const { success, error: toastError } = useToast();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [saving, setSaving] = useState(false);

  const handleChangePassword = async () => {
    if (!oldPassword || !newPassword) { toastError('请填写完整'); return; }
    if (newPassword.length < 6) { toastError('密码至少6位'); return; }
    setSaving(true);
    try {
      await authApi.changePassword({ oldPassword, newPassword });
      success('密码修改成功');
      setOldPassword(''); setNewPassword('');
    } catch { toastError('密码修改失败'); }
    finally { setSaving(false); }
  };

  return (
    <div className="w-full" style={{ minHeight: 'calc(100vh - 200px)' }}>
      <div className="border border-border/50 rounded-lg overflow-hidden flex flex-col" style={{ minHeight: 'calc(100vh - 200px)' }}>
        {/* Theme */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border/50">
          <div>
            <p className="text-[14px] font-medium text-text-primary">深色模式</p>
            <p className="text-[12px] text-text-tertiary mt-0.5">切换浅色/深色主题</p>
          </div>
          <button
            onClick={toggleTheme}
            className="flex items-center gap-2 px-4 py-2 rounded-button text-[13px] font-medium transition-colors border border-border hover:bg-surface-secondary"
          >
            {theme === 'dark' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
            {theme === 'dark' ? '深色' : '浅色'}
          </button>
        </div>

        {/* Password */}
        <div className="px-5 py-4 border-b border-border/50">
          <h2 className="text-[14px] font-semibold text-text-primary mb-3">修改密码</h2>
          <div className="flex flex-col sm:flex-row gap-3 items-end">
            <div className="flex-1">
              <input type="password" value={oldPassword} onChange={e => setOldPassword(e.target.value)} placeholder="当前密码" className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
            </div>
            <div className="flex-1">
              <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder="新密码（至少6位）" className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" />
            </div>
            <button
              onClick={handleChangePassword}
              disabled={saving}
              className="px-5 py-2 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              {saving ? '保存中...' : '修改'}
            </button>
          </div>
        </div>

        {/* Notifications */}
        <div className="px-5 py-4 border-b border-border/50">
          <h2 className="text-[14px] font-semibold text-text-primary mb-3">通知偏好</h2>
          <div className="space-y-3">
            <ToggleRow label="翻译完成通知" description="文档翻译完成时推送通知" defaultOn />
            <ToggleRow label="邮件通知" description="接收翻译报告邮件" />
            <ToggleRow label="系统更新通知" description="新功能和维护通知" defaultOn />
          </div>
        </div>

        {/* Bottom bar */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-border/50 bg-surface-secondary">
          <span className="text-[12px] text-text-tertiary">管理您的账号安全和外观偏好</span>
        </div>
      </div>
    </div>
  );
}

function ToggleRow({ label, description, defaultOn = false }: { label: string; description: string; defaultOn?: boolean }) {
  const [on, setOn] = useState(defaultOn);
  return (
    <div className="flex items-center justify-between">
      <div>
        <p className="text-[13px] font-medium text-text-primary">{label}</p>
        <p className="text-[12px] text-text-tertiary mt-0.5">{description}</p>
      </div>
      <button onClick={() => setOn(!on)} className={`w-11 h-6 rounded-full transition-colors ${on ? 'bg-accent' : 'bg-surface-secondary'}`}>
        <div className={`w-5 h-5 rounded-full bg-white shadow transition-transform ${on ? 'translate-x-5' : 'translate-x-0.5'}`} />
      </button>
    </div>
  );
}

export { SettingsPage };
