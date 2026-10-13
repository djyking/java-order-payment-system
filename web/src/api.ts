import type { Session } from './types';
export const SESSION_KEY = 'order-platform-session';
export class ApiError extends Error {
  constructor(
    message: string,
    public code: string,
    public traceId?: string,
  ) {
    super(message);
  }
}
export function getSession(): Session | null {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
  } catch {
    return null;
  }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  const token = getSession()?.accessToken;
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { ...options, headers });
  } catch {
    throw new ApiError('网络连接失败，请稍后重试。', 'NETWORK_ERROR');
  }
  let payload: { code: string; message: string; data: T; traceId?: string };
  try {
    payload = await response.json();
  } catch {
    throw new ApiError('服务暂时不可用，请稍后重试。', 'SERVICE_UNAVAILABLE');
  }
  if (response.status === 401 && token) {
    sessionStorage.removeItem(SESSION_KEY);
    window.dispatchEvent(new Event('auth-expired'));
  }
  if (!response.ok || payload.code !== 'OK')
    throw new ApiError(payload.message || '操作失败，请稍后重试。', payload.code, payload.traceId);
  return payload.data;
}
export const post = <T>(path: string, body?: unknown, headers?: HeadersInit) =>
  api<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
    headers,
  });
export const put = <T>(path: string, body: unknown) =>
  api<T>(path, { method: 'PUT', body: JSON.stringify(body) });
export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试。';
}
