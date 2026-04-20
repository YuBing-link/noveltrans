import { api } from './client';
import type { UserStatistics, UserQuota, TranslationHistoryItem, PaginatedList } from './types';

export const userApi = {
  getStatistics: () => api.get<UserStatistics>('/user/statistics'),
  getQuota: () => api.get<UserQuota>('/user/quota'),
  getTranslationHistory: (params?: { page?: number; pageSize?: number; type?: string }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    if (params?.type) qs.set('type', params.type);
    const query = qs.toString();
    return api.get<PaginatedList<TranslationHistoryItem>>(`/user/translation-history${query ? `?${query}` : ''}`);
  },
};
