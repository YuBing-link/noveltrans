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

  // 导出术语表为 CSV
  exportGlossary: async () => {
    const token = localStorage.getItem('authToken');
    const res = await fetch('/user/glossaries/export', {
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
  importGlossary: async (file: File) => {
    const token = localStorage.getItem('authToken');
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch('/user/glossaries/import', {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    });
    if (!res.ok) throw new Error('导入失败');
    return res.json();
  },
};
