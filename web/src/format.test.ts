import { describe, it, expect } from 'vitest';
import { money, priceToCents, safePayUrl, submitKey } from './format';
describe('cent amounts and payment links', () => {
  it('formats amounts without losing cent precision', () => {
    expect(money('90071992547409931')).toBe('900,719,925,474,099.31');
    expect(money(109)).toBe('1.09');
    expect(money(-9)).toBe('-0.09');
  });
  it('converts decimal input to integer cents without floating point rounding', () => {
    expect(priceToCents('0.29')).toBe(29);
    expect(priceToCents('12.3')).toBe(1230);
    expect(priceToCents('1000000.00')).toBe(100000000);
  });
  it('rejects invalid or server-disallowed product prices', () => {
    for (const price of ['0', '-1', '1.001', '1e3', '1000000.01', 'abc'])
      expect(() => priceToCents(price)).toThrow();
  });
  it('never offers executable or malformed payment links', () => {
    expect(safePayUrl('javascript:alert(1)')).toBeNull();
    expect(safePayUrl('data:text/html,test')).toBeNull();
    expect(safePayUrl('/fake-success')).toBeNull();
    expect(safePayUrl('https://pay.example.com/checkout?id=1')).toBe(
      'https://pay.example.com/checkout?id=1',
    );
  });
});
describe('order submission retry identity', () => {
  it('keeps the same token after retry/reload while isolating users and baskets', () => {
    const values = new Map<string, string>();
    const storage: Storage = {
      getItem: (key) => values.get(key) || null,
      setItem: (key, value) => {
        values.set(key, value);
      },
      removeItem: (key) => {
        values.delete(key);
      },
      clear: () => values.clear(),
      key: (index) => [...values.keys()][index] || null,
      get length() {
        return values.size;
      },
    };
    const first = submitKey('90071992547409931', '90071992547409939', 2, storage);
    expect(submitKey('90071992547409931', '90071992547409939', 2, storage)).toBe(first);
    expect(submitKey('90071992547409932', '90071992547409939', 2, storage)).not.toBe(first);
    expect(submitKey('90071992547409931', '90071992547409939', 3, storage)).not.toBe(first);
    expect(first).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
  });
});
