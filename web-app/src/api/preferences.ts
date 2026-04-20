import { api } from './client';
import type { UserPreferences } from './types';

export const preferencesApi = {
  get: () => api.get<UserPreferences>('/user/preferences'),
  update: (data: Partial<UserPreferences>) => api.put<UserPreferences>('/user/preferences', data),
};
