import { createContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import { authApi } from '../api/auth';

export interface AuthUser {
  id: number;
  email: string;
  username: string;
  avatar: string;
  userLevel: string;
  createTime: string;
}

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, code: string, username?: string) => Promise<void>;
  logout: () => Promise<void>;
  sendCode: (email: string) => Promise<void>;
  sendResetCode: (email: string) => Promise<void>;
  refreshUser: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('authToken');
    const info = localStorage.getItem('userInfo');
    if (token && info) {
      try {
        const parsed = JSON.parse(info);
        setUser(parsed);
        // 验证 token 是否仍然有效
        refreshUser().finally(() => setLoading(false));
      } catch {
        localStorage.removeItem('authToken');
        localStorage.removeItem('userInfo');
        setLoading(false);
      }
    } else {
      setLoading(false);
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const refreshUser = useCallback(async () => {
    try {
      const { data } = await authApi.getProfile();
      setUser(data);
      localStorage.setItem('userInfo', JSON.stringify(data));
    } catch {
      setUser(null);
      localStorage.removeItem('authToken');
      localStorage.removeItem('userInfo');
    }
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const result = await authApi.login({ email, password, from: 'web' });
    const token = result.token;
    if (!token) throw new Error('登录失败：未获取到 token');
    localStorage.setItem('authToken', token);
    localStorage.setItem('userInfo', JSON.stringify(result.data));
    setUser(result.data);
  }, []);

  const register = useCallback(async (email: string, password: string, code: string, username?: string) => {
    const result = await authApi.register({ email, password, code, username });
    const token = result.token;
    if (!token) throw new Error('注册失败：未获取到 token');
    localStorage.setItem('authToken', token);
    localStorage.setItem('userInfo', JSON.stringify(result.data));
    setUser(result.data);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch { /* ignore */ }
    localStorage.removeItem('authToken');
    localStorage.removeItem('userInfo');
    setUser(null);
  }, []);

  const sendCode = useCallback(async (email: string) => {
    await authApi.sendCode(email);
  }, []);

  const sendResetCode = useCallback(async (email: string) => {
    await authApi.sendResetCode(email);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, loading, login, register, logout, sendCode, sendResetCode, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}
