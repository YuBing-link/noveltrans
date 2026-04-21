import { api } from './client';
import type { LoginRequest, RegisterRequest, LoginResponse, UserProfile } from './types';

export const authApi = {
  login: (data: LoginRequest) => api.post<LoginResponse>('/user/login', data),
  register: (data: RegisterRequest) => api.post<LoginResponse>('/user/register', data),
  sendCode: (email: string) => api.post<null>('/user/send-code', { email }),
  sendResetCode: (email: string) => api.post<null>('/user/send-reset-code', { email }),
  getProfile: () => api.get<UserProfile>('/user/profile'),
  updateProfile: (data: Partial<UserProfile>) => api.put<UserProfile>('/user/profile', data),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    api.post<null>('/user/change-password', data),
  resetPassword: (data: { email: string; code: string; newPassword: string }) =>
    api.post<null>('/user/reset-password', data),
  refreshToken: (refreshToken: string) =>
    api.post<{ token: string }>('/user/refresh-token', { refreshToken }),
  logout: () => api.post<null>('/user/logout'),
};
