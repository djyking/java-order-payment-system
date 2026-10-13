import type { Cents } from './types';
export function money(value: Cents): string {
  const cents = BigInt(value ?? 0),
    absolute = cents < 0n ? -cents : cents;
  return `${cents < 0n ? '-' : ''}${(absolute / 100n).toLocaleString('zh-CN')}.${(absolute % 100n).toString().padStart(2, '0')}`;
}
export function priceToCents(value: string): number {
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(value)) throw new Error('请输入有效价格，最多两位小数。');
  const [whole, fraction = ''] = value.split('.');
  const result = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  if (result <= 0) throw new Error('商品价格必须大于 0。');
  if (result > 100_000_000) throw new Error('商品价格不能超过 1,000,000 元。');
  return result;
}
export const states: Record<string, string> = {
  WAIT_PAY: '待支付',
  WAIT_DELIVERY: '待发货',
  WAIT_RECEIVE: '待收货',
  COMPLETED: '已完成',
  CANCELED: '已取消',
};
export const stateLabel = (value: string) => states[value] || value;
export const dateTime = (value: string) => (value ? value.replace('T', ' ').slice(0, 19) : '—');
export function safePayUrl(value?: string): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}
export function submitKey(
  userId: string,
  skuId: string,
  quantity: number,
  storage: Storage = sessionStorage,
): string {
  const key = `order-submit:${userId}:${skuId}:${quantity}`;
  let token = storage.getItem(key);
  if (!token) {
    token = crypto.randomUUID();
    storage.setItem(key, token);
  }
  return token;
}
export function clearSubmitKey(userId: string, skuId: string, quantity: number) {
  sessionStorage.removeItem(`order-submit:${userId}:${skuId}:${quantity}`);
}
