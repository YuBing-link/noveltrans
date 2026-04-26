import { api } from './client';
import type { GlossaryItem, PaginatedList } from './types';

export const glossaryApi = {
  getList: (params?: { page?: number; pageSize?: number; search?: string }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    if (params?.search) qs.set('search', params.search);
    const query = qs.toString();
    return api.get<PaginatedList<GlossaryItem>>(`/user/glossaries${query ? `?${query}` : ''}`);
  },
  getDetail: (id: number) => api.get<GlossaryItem>(`/user/glossaries/${id}`),
  create: (data: { sourceWord: string; targetWord: string; remark: string }) =>
    api.post<GlossaryItem>('/user/glossaries', data),
  update: (id: number, data: { sourceWord: string; targetWord: string; remark: string }) =>
    api.put<GlossaryItem>(`/user/glossaries/${id}`, data),
  delete: (id: number) => api.delete<null>(`/user/glossaries/${id}`),

  // 导出术语表为 CSV
  exportGlossary: async () => {
    const token = localStorage.getItem('authToken');
    const res = await fetch('/api/user/glossaries/export', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error('导出失败');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'glossary.csv';
    a.click();
    URL.revokeObjectURL(url);
  },

  // 导入术语表 CSV
  importGlossary: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.upload<null>('/user/glossaries/import', formData);
  },
};
