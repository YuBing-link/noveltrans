import { useState } from 'react';
import { useTheme } from '../hooks/useTheme';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { useToast } from '../components/ui/Toast';
import { authApi } from '../api/auth';
import { preferencesApi } from '../api/preferences';
import { Moon, Sun, Bell, Mail, Volume2 } from 'lucide-react';

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
      setOldPassword('');
      setNewPassword('');
    } catch { toastError('密码修改失败'); }
    finally { setSaving(false); }
  };

  const handleThemeToggle = async () => {
    toggleTheme();
    try {
      const newTheme = theme === 'dark' ? 'light' : 'dark';
      await preferencesApi.update({ themeMode: newTheme });
    } catch { /* ignore - local toggle still works */ }
  };

  return (
    <PageLayout className="py-8 min-h-[calc(100vh-3.5rem)]">
      <div className="mb-10 text-center">
        <h1 className="text-[28px] sm:text-[32px] font-semibold text-text-primary tracking-display leading-[1.07] mb-2">
          设置
        </h1>
        <p className="text-text-secondary text-[15px]">管理您的账号安全和外观偏好</p>
      </div>

      <div className="max-w-2xl mx-auto space-y-6">
        {/* Theme */}
        <Card>
          <div className="p-6">
            <h2 className="text-[17px] font-semibold text-text-primary mb-4">外观</h2>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-[13px] font-medium text-text-primary">深色模式</p>
                <p className="text-[12px] text-text-tertiary mt-0.5">切换浅色/深色主题</p>
              </div>
              <button
                onClick={handleThemeToggle}
                className={`flex items-center gap-2 px-4 py-2 rounded-button text-[13px] font-medium transition-colors ${
                  theme === 'dark'
                    ? 'bg-text-primary text-white'
                    : 'bg-surface-secondary text-text-primary'
                }`}
              >
                {theme === 'dark' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
                {theme === 'dark' ? '深色' : '浅色'}
              </button>
            </div>
          </div>
        </Card>

        {/* Password */}
        <Card>
          <div className="p-6">
            <h2 className="text-[17px] font-semibold text-text-primary mb-4">修改密码</h2>
            <div className="space-y-4">
              <Input
                label="当前密码"
                type="password"
                value={oldPassword}
                onChange={e => setOldPassword(e.target.value)}
                placeholder="输入当前密码"
              />
              <Input
                label="新密码"
                type="password"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                placeholder="输入新密码（至少6位）"
              />
              <Button onClick={handleChangePassword} loading={saving}>
                修改密码
              </Button>
            </div>
          </div>
        </Card>

        {/* Notifications */}
        <Card>
          <div className="p-6">
            <h2 className="text-[17px] font-semibold text-text-primary mb-4">通知偏好</h2>
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <Bell className="w-4 h-4 text-text-tertiary" />
                  <div>
                    <p className="text-[13px] font-medium text-text-primary">翻译完成通知</p>
                    <p className="text-[12px] text-text-tertiary mt-0.5">文档翻译完成时推送通知</p>
                  </div>
                </div>
                <ToggleSwitch defaultOn />
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <Mail className="w-4 h-4 text-text-tertiary" />
                  <div>
                    <p className="text-[13px] font-medium text-text-primary">邮件通知</p>
                    <p className="text-[12px] text-text-tertiary mt-0.5">接收翻译报告邮件</p>
                  </div>
                </div>
                <ToggleSwitch />
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <Volume2 className="w-4 h-4 text-text-tertiary" />
                  <div>
                    <p className="text-[13px] font-medium text-text-primary">系统更新通知</p>
                    <p className="text-[12px] text-text-tertiary mt-0.5">新功能和维护通知</p>
                  </div>
                </div>
                <ToggleSwitch defaultOn />
              </div>
            </div>
          </div>
        </Card>
      </div>
    </PageLayout>
  );
}

function ToggleSwitch({ defaultOn = false }: { defaultOn?: boolean }) {
  const [on, setOn] = useState(defaultOn);
  return (
    <button
      onClick={() => setOn(!on)}
      className={`w-11 h-6 rounded-full transition-colors ${on ? 'bg-accent' : 'bg-surface-secondary'}`}
    >
      <div className={`w-5 h-5 rounded-full bg-white shadow transition-transform ${on ? 'translate-x-5' : 'translate-x-0.5'}`} />
    </button>
  );
}

export { SettingsPage };
