import { api } from './client';
import type { ApiKeyItem } from './types';

export const apiKeyApi = {
  getList: () => api.get<ApiKeyItem[]>('/user/api-keys'),
  create: (name: string) => api.post<ApiKeyItem>('/user/api-keys', { name }),
  delete: (id: number) => api.delete<null>(`/user/api-keys/${id}`),
  reset: (id: number) => api.post<ApiKeyItem>(`/user/api-keys/${id}/reset`),
};
