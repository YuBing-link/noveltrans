import type { ApiResult } from './types';

const API_BASE = '/api';

export class ApiError extends Error {
  code: string;
  status?: number;
  constructor(code: string, message: string, status?: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
  }
}

// 保存 token 并通知扩展同步认证状态
function saveAuth(token: string) {
  localStorage.setItem('authToken', token);
  window.dispatchEvent(new CustomEvent('userLoggedIn', { detail: { token } }));
}

async function apiFetch<T>(endpoint: string, options?: RequestInit): Promise<ApiResult<T>> {
  const token = localStorage.getItem('authToken');
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options?.headers as Record<string, string> || {}),
  };

  const res = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    if (res.status === 401) {
      localStorage.removeItem('authToken');
      localStorage.removeItem('userInfo');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
      throw new ApiError('401', '认证已过期', res.status);
    }
    const body = await res.json().catch(() => null);
    throw new ApiError(body?.code || String(res.status), body?.message || '请求失败', res.status);
  }

  const data: ApiResult<T> = await res.json();
  if (!data.success) {
    throw new ApiError(data.code || 'ERROR', data.message || '请求失败');
  }

  // Handle token refresh from response
  if (data.token) {
    saveAuth(data.token);
  }

  return data;
}

export const api = {
  get: <T>(endpoint: string) => apiFetch<T>(endpoint, { method: 'GET' }),
  post: <T>(endpoint: string, body?: unknown) =>
    apiFetch<T>(endpoint, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(endpoint: string, body?: unknown) =>
    apiFetch<T>(endpoint, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(endpoint: string) => apiFetch<T>(endpoint, { method: 'DELETE' }),
  upload: async <T>(endpoint: string, formData: FormData) => {
    const token = localStorage.getItem('authToken');
    const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: 'POST',
      headers,
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      throw new ApiError(body?.code || String(res.status), body?.message || '上传失败', res.status);
    }
    const data: ApiResult<T> = await res.json();
    if (!data.success) {
      throw new ApiError(data.code || 'ERROR', data.message || '上传失败');
    }
    if (data.token) saveAuth(data.token);
    return data;
  },
};

// SSE streaming helper for POST-based streaming
export async function streamFetch(
  endpoint: string,
  body: unknown,
  onChunk: (data: string) => void,
  onDone: () => void,
  onError: (error: string) => void,
): Promise<void> {
  const token = localStorage.getItem('authToken');
  const res = await fetch(`${API_BASE}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    onError(`HTTP ${res.status}`);
    return;
  }

  const reader = res.body?.getReader();
  if (!reader) {
    onError('Stream not available');
    return;
  }

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (!line.startsWith('data:')) continue;
        // 只去掉 'data: ' 前缀，保留内部空白（如段落分隔符 \n\n）
        const data = line.length > 6 && line[5] === ' ' ? line.slice(6) : line.slice(5);
        if (data === '[DONE]') {
          onDone();
          return;
        }
        if (data.startsWith('ERROR:')) {
          onError(data.slice(6));
          return;
        }
        onChunk(data);
      }
    }
  } catch (e) {
    onError(e instanceof Error ? e.message : 'Stream error');
  }
}
