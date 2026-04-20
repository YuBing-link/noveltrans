import { api } from './client';
import type { PlatformStats } from './types';

export const platformApi = {
  getStats: () => api.get<PlatformStats>('/platform/stats'),
};
