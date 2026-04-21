import { api, streamFetch } from './client';
import type { TaskStatus, TranslationResult, TranslateTextRequest } from './types';

export const translateApi = {
  // Text translation via selection endpoint
  translateText: (data: TranslateTextRequest) =>
    api.post<TranslationResult>('/v1/translate/selection', data),

  // Task operations
  getTaskStatus: (taskId: string) => api.get<TaskStatus>(`/v1/translate/task/${taskId}`),
  cancelTask: (taskId: string) => api.delete<null>(`/v1/translate/task/${taskId}`),
  deleteHistory: (taskId: string) => api.delete<null>(`/v1/translate/history/${taskId}`),
  getTaskResult: (taskId: string) => api.get<TranslationResult>(`/v1/translate/task/${taskId}/result`),
  downloadTaskResult: async (taskId: string) => {
    const token = localStorage.getItem('authToken');
    const res = await fetch(`/v1/translate/task/${taskId}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) throw new Error('Download failed');
    return res.blob();
  },

  // SSE streaming for text translation
  streamTranslate: (
    data: TranslateTextRequest,
    onChunk: (data: string) => void,
    onDone: () => void,
    onError: (error: string) => void,
  ) => streamFetch('/v1/translate/text/stream', data, onChunk, onDone, onError),

  // Reader translation
  translateReader: (data: { content: string; targetLang: string; sourceLang?: string; engine?: string; mode?: string }) =>
    api.post('/v1/translate/reader', data),

  // Premium translation
  premiumTranslateSelection: (data: { text: string; sourceLang: string; targetLang: string; engine?: string; mode?: string; context?: string }) =>
    api.post('/v1/translate/premium-selection', data),
  premiumTranslateReader: (data: { content: string; targetLang: string; sourceLang?: string; engine?: string; mode?: string }) =>
    api.post('/v1/translate/premium-reader', data),

  // Document streaming
  streamDocument: (file: File, params?: { sourceLang?: string; targetLang?: string; mode?: string }) => {
    const formData = new FormData();
    formData.append('file', file);
    if (params?.sourceLang) formData.append('sourceLang', params.sourceLang);
    if (params?.targetLang) formData.append('targetLang', params.targetLang);
    if (params?.mode) formData.append('mode', params.mode);
    return api.upload<{ taskId: string }>('/v1/translate/document/stream', formData);
  },
  streamDocumentById: (docId: number, params?: { targetLang?: string; mode?: string }) => {
    const qs = new URLSearchParams();
    if (params?.targetLang) qs.set('targetLang', params.targetLang);
    if (params?.mode) qs.set('mode', params.mode);
    return streamFetch(`/v1/translate/document/stream/${docId}`, {},
      () => {}, () => {}, () => {});
  },
};
