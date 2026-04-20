import { api } from './client';
import type { GlossaryItem } from './types';

export const glossaryApi = {
  getList: () => api.get<GlossaryItem[]>('/user/glossaries'),
  getDetail: (id: number) => api.get<GlossaryItem>(`/user/glossaries/${id}`),
  create: (data: { sourceWord: string; targetWord: string; remark: string }) =>
    api.post<GlossaryItem>('/user/glossaries', data),
  update: (id: number, data: { sourceWord: string; targetWord: string; remark: string }) =>
    api.put<GlossaryItem>(`/user/glossaries/${id}`, data),
  delete: (id: number) => api.delete<null>(`/user/glossaries/${id}`),
};
