import { api } from './client';
import type { DocumentItem } from './types';

export const documentApi = {
  getList: (params?: { page?: number; pageSize?: number; status?: string }) => {
    const qs = new URLSearchParams();
    if (params?.page) qs.set('page', String(params.page));
    if (params?.pageSize) qs.set('pageSize', String(params.pageSize));
    if (params?.status) qs.set('status', params.status);
    const query = qs.toString();
    return api.get<{ list: DocumentItem[]; total: number; page: number; pageSize: number }>(`/user/documents${query ? `?${query}` : ''}`);
  },
  getDetail: (docId: number) => api.get<DocumentItem>(`/user/documents/${docId}`),
  delete: (docId: number) => api.delete<null>(`/user/documents/${docId}`),
  cancel: (docId: number) => api.post<null>(`/user/documents/${docId}/cancel`),
  retry: (docId: number) => api.post<null>(`/user/documents/${docId}/retry`),
  upload: (file: File, params?: { sourceLang?: string; targetLang?: string; mode?: string }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (params?.sourceLang) formData.append('sourceLang', params.sourceLang);
    if (params?.targetLang) formData.append('targetLang', params.targetLang);
    if (params?.mode) formData.append('mode', params.mode);
    return api.upload<DocumentItem>('/user/documents/upload', formData);
  },
  download: async (docId: number) => {
    const token = localStorage.getItem('authToken');
    const res = await fetch(`/user/documents/${docId}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error('Download failed');
    return res.blob();
  },
};
