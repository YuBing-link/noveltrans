import { api } from './client';
import type { ExternalTranslateRequest, ExternalTranslateResponse, ExternalBatchTranslateRequest } from './types';

export const externalApi = {
  translate: (data: ExternalTranslateRequest) =>
    api.post<ExternalTranslateResponse>('/v1/external/translate', data),
  batchTranslate: (data: ExternalBatchTranslateRequest) =>
    api.post<ExternalTranslateResponse[]>('/v1/external/batch', data),
  getModels: () =>
    api.get<Array<{ name: string; engine: string; available: boolean }>>('/v1/external/models'),
  downloadTask: async (taskId: string) => {
    const token = localStorage.getItem('authToken');
    const res = await fetch(`/v1/external/task/${taskId}/download`, {
      headers: token ? { 'X-API-Key': token } : {},
    });
    if (!res.ok) throw new Error('Download failed');
    return res.blob();
  },
};
