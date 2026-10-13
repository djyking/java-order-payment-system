import { computed, ref } from 'vue';
import { api, getSession, SESSION_KEY } from './api';
import type { Session, User } from './types';
export const session = ref<Session | null>(getSession());
export const isAdmin = computed(() => session.value?.role === 'ADMIN');
export function saveSession(value: Session) {
  session.value = value;
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
}
export function logout() {
  session.value = null;
  sessionStorage.removeItem(SESSION_KEY);
}
export async function refreshIdentity() {
  if (!session.value) return;
  const accessToken = session.value.accessToken;
  const user = await api<User>('/users/me');
  if (session.value?.accessToken === accessToken) saveSession({ ...user, accessToken });
}
export const notice = ref('');
let timer: ReturnType<typeof setTimeout>;
export function notify(text: string) {
  notice.value = text;
  clearTimeout(timer);
  timer = setTimeout(() => (notice.value = ''), 4500);
}
