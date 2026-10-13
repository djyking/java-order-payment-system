import { beforeEach, describe, it, expect, vi } from 'vitest';
import { api, post, SESSION_KEY } from './api';
const values = new Map<string, string>();
beforeEach(() => {
  values.clear();
  vi.stubGlobal('sessionStorage', {
    getItem: (k: string) => values.get(k) || null,
    removeItem: (k: string) => values.delete(k),
  });
  vi.stubGlobal('window', { dispatchEvent: vi.fn() });
  vi.stubGlobal('fetch', vi.fn());
});
describe('API contract', () => {
  it('preserves string IDs and forwards authenticated idempotent requests', async () => {
    values.set(SESSION_KEY, JSON.stringify({ accessToken: 'session-token' }));
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ code: 'OK', data: { userId: '90071992547409939' } })),
    );
    const value = await post<{ userId: string }>(
      '/orders',
      { items: [{ skuId: '90071992547409939', quantity: 1 }] },
      { 'X-Idempotency-Token': 'stable-token' },
    );
    const init = vi.mocked(fetch).mock.calls[0][1]!;
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer session-token');
    expect(new Headers(init.headers).get('X-Idempotency-Token')).toBe('stable-token');
    expect(init.body).toContain('"skuId":"90071992547409939"');
    expect(value.userId).toBe('90071992547409939');
  });
  it('surfaces business failures even with HTTP 200', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ code: 'FORBIDDEN', message: '无权访问', data: null })),
    );
    await expect(api('/admin/orders')).rejects.toMatchObject({
      code: 'FORBIDDEN',
      message: '无权访问',
    });
  });
  it('clears expired sessions and notifies router on 401', async () => {
    values.set(SESSION_KEY, JSON.stringify({ accessToken: 'expired' }));
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: '登录已失效' }), {
        status: 401,
      }),
    );
    await expect(api('/orders')).rejects.toThrow('登录已失效');
    expect(values.has(SESSION_KEY)).toBe(false);
    expect(window.dispatchEvent).toHaveBeenCalled();
  });
  it('gives a usable network failure rather than claiming a successful order', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(post('/orders', {})).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
  });
});
