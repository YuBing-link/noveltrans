import { useState } from 'react';
import { useTheme } from '../hooks/useTheme';
import { useToast } from '../components/ui/Toast';
import { authApi } from '../api/auth';
import { Moon, Sun, Bell, Mail, Info } from 'lucide-react';

function SettingsPage() {
  const { theme, toggleTheme } = useTheme();
  const { success, error: toastError } = useToast();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [saving, setSaving] = useState(false);

  const handleChangePassword = async () => {
    if (!oldPassword || !newPassword || !confirmPassword) { 
      toastError('请填写完整'); 
      return; 
    }
    if (newPassword !== confirmPassword) {
      toastError('两次输入的密码不一致');
      return;
    }
    if (newPassword.length < 6) { 
      toastError('密码至少6位'); 
      return; 
    }
    
    setSaving(true);
    try {
      await authApi.changePassword({ oldPassword, newPassword });
      success('密码修改成功');
      setOldPassword(''); 
      setNewPassword(''); 
      setConfirmPassword('');
    } catch (err) { 
      toastError(err instanceof Error ? err.message : '密码修改失败'); 
    }
    finally { 
      setSaving(false); 
    }
  };

  return (
    <div className="py-8">
      <h2 className="text-[15px] font-semibold text-text-primary">设置</h2>
      
      <div className="space-y-4">
        {/* Theme */}
        <div className="border border-border/50 rounded-lg overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4">
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 rounded-lg bg-surface-secondary flex items-center justify-center flex-shrink-0">
                {theme === 'dark' ? <Moon className="w-5 h-5 text-accent" /> : <Sun className="w-5 h-5 text-yellow" />}
              </div>
              <div>
                <p className="text-[13px] font-medium text-text-primary">深色模式</p>
                <p className="text-[12px] text-text-tertiary mt-0.5">切换浅色/深色主题，保护您的视力</p>
              </div>
            </div>
            <button
              onClick={toggleTheme}
              className={`relative inline-flex items-center h-6 w-11 rounded-full transition-colors ${
                theme === 'dark' ? 'bg-accent' : 'bg-gray-200'
              }`}
            >
              <span
                className={`inline-block w-5 h-5 transform bg-white rounded-full shadow transition-transform ${
                  theme === 'dark' ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              />
            </button>
          </div>
        </div>

        {/* Password */}
        <div className="border border-border/50 rounded-lg overflow-hidden">
          <div className="px-5 py-4 border-b border-border/50">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-surface-secondary flex items-center justify-center">
                <svg className="w-5 h-5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
              </div>
              <div>
                <h3 className="text-[13px] font-medium text-text-primary">修改密码</h3>
                <p className="text-[12px] text-text-tertiary mt-0.5">定期更换密码以保障账号安全</p>
              </div>
            </div>
          </div>
          
          <div className="px-5 py-4 space-y-3">
            <div>
              <label className="text-[13px] font-medium text-text-secondary block mb-1">当前密码</label>
              <input 
                type="password" 
                value={oldPassword} 
                onChange={e => setOldPassword(e.target.value)} 
                placeholder="输入当前密码" 
                className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" 
              />
            </div>
            
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1">新密码</label>
                <input 
                  type="password" 
                  value={newPassword} 
                  onChange={e => setNewPassword(e.target.value)} 
                  placeholder="至少6位字符" 
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" 
                />
              </div>
              
              <div>
                <label className="text-[13px] font-medium text-text-secondary block mb-1">确认新密码</label>
                <input 
                  type="password" 
                  value={confirmPassword} 
                  onChange={e => setConfirmPassword(e.target.value)} 
                  placeholder="再次输入新密码" 
                  className="w-full px-3 py-2 text-[13px] bg-surface-secondary text-text-primary rounded-input border border-border focus:border-accent focus:outline-none" 
                />
              </div>
            </div>
            
            <div className="pt-1">
              <button
                onClick={handleChangePassword}
                disabled={saving}
                className="px-5 py-1.5 text-[13px] font-medium text-white bg-accent rounded-button hover:bg-accent-hover disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                {saving ? '保存中...' : '修改密码'}
              </button>
            </div>
          </div>
        </div>

        {/* Notifications - Coming Soon */}
        <div className="border border-border/50 rounded-lg overflow-hidden opacity-60">
          <div className="px-5 py-4 border-b border-border/50">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-surface-secondary flex items-center justify-center">
                <Bell className="w-5 h-5 text-text-tertiary" />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <h3 className="text-[13px] font-medium text-text-primary">通知偏好</h3>
                  <span className="px-2 py-0.5 text-[11px] font-medium text-text-tertiary bg-surface-secondary rounded-full">
                    即将推出
                  </span>
                </div>
                <p className="text-[12px] text-text-tertiary mt-0.5">自定义接收通知的方式和内容</p>
              </div>
            </div>
          </div>
          
          <div className="px-5 py-4 space-y-3">
            <div className="flex items-center justify-between py-2">
              <div className="flex items-start gap-3">
                <Bell className="w-4 h-4 text-text-tertiary mt-0.5" />
                <div>
                  <p className="text-[13px] font-medium text-text-primary">翻译完成通知</p>
                  <p className="text-[12px] text-text-tertiary mt-0.5">文档翻译完成时推送通知</p>
                </div>
              </div>
            </div>
            
            <div className="flex items-center justify-between py-2">
              <div className="flex items-start gap-3">
                <Mail className="w-4 h-4 text-text-tertiary mt-0.5" />
                <div>
                  <p className="text-[13px] font-medium text-text-primary">邮件通知</p>
                  <p className="text-[12px] text-text-tertiary mt-0.5">接收翻译报告邮件</p>
                </div>
              </div>
            </div>
            
            <div className="flex items-center justify-between py-2">
              <div className="flex items-start gap-3">
                <Info className="w-4 h-4 text-text-tertiary mt-0.5" />
                <div>
                  <p className="text-[13px] font-medium text-text-primary">系统更新通知</p>
                  <p className="text-[12px] text-text-tertiary mt-0.5">新功能和维护通知</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export { SettingsPage };