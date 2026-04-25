import { api } from './client';
import type { ApiKeyItem, PaginatedList } from './types';

export const apiKeyApi = {
  getList: (params?: { page?: number; pageSize?: number }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    const query = qs.toString();
    return api.get<PaginatedList<ApiKeyItem>>(`/user/api-keys${query ? `?${query}` : ''}`);
  },
  create: (name: string) => api.post<ApiKeyItem>('/user/api-keys', { name }),
  delete: (id: number) => api.delete<null>(`/user/api-keys/${id}`),
  reset: (id: number) => api.post<ApiKeyItem>(`/user/api-keys/${id}/reset`),
};
