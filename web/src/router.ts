import { createRouter, createWebHistory } from 'vue-router';
import { session, isAdmin } from './state';
export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: () => import('./views/Catalog.vue') },
    { path: '/products/:id', component: () => import('./views/ProductDetail.vue') },
    { path: '/login', component: () => import('./views/Login.vue') },
    { path: '/confirm', component: () => import('./views/Confirm.vue'), meta: { auth: true } },
    { path: '/orders', component: () => import('./views/Orders.vue'), meta: { auth: true } },
    {
      path: '/orders/:no',
      component: () => import('./views/OrderDetail.vue'),
      meta: { auth: true },
    },
    {
      path: '/checkout/:no',
      component: () => import('./views/Checkout.vue'),
      meta: { auth: true },
    },
    {
      path: '/admin/products',
      component: () => import('./views/AdminProducts.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/admin/orders',
      component: () => import('./views/AdminOrders.vue'),
      meta: { auth: true, admin: true },
    },
    {
      path: '/admin/orders/:no',
      component: () => import('./views/OrderDetail.vue'),
      meta: { auth: true, admin: true },
    },
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFound.vue') },
  ],
});
router.beforeEach((to) => {
  if (to.meta.auth && !session.value) return { path: '/login', query: { redirect: to.fullPath } };
  if (to.meta.admin && !isAdmin.value) return '/';
});
window.addEventListener('auth-expired', () => {
  session.value = null;
  if (router.currentRoute.value.meta.auth)
    void router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } });
});
