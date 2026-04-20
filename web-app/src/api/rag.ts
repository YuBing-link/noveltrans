import { api } from './client';
import type { RagTranslationRequest, RagTranslationResponse } from './types';

export const ragApi = {
  translate: (data: RagTranslationRequest) =>
    api.post<RagTranslationResponse>('/v1/translate/rag', data),
};
