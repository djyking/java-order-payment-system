<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { session, isAdmin, logout, notice, refreshIdentity } from './state';
const route = useRoute(),
  router = useRouter();
function signOut() {
  logout();
  void router.push('/');
}
onMounted(() => {
  void refreshIdentity().catch(() => {});
});
</script>
<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="header-inner">
        <RouterLink class="brand" to="/" aria-label="Java 订单支付系统首页">
          <span class="brand-mark">
            J
            <span>·</span>
          </span>
          <span>
            Java
            <b>订单支付系统</b>
            <small>ORDER & PAYMENT</small>
          </span>
        </RouterLink>
        <nav aria-label="主导航">
          <RouterLink
            to="/"
            :class="{ active: route.path === '/' || route.path.startsWith('/products') }"
          >
            商品选购
          </RouterLink>
          <RouterLink
            to="/orders"
            :class="{
              active: route.path.startsWith('/orders') || route.path.startsWith('/checkout'),
            }"
          >
            我的订单
          </RouterLink>
          <RouterLink
            v-if="isAdmin"
            to="/admin/orders"
            :class="{ active: route.path.startsWith('/admin') }"
          >
            管理工作台
          </RouterLink>
        </nav>
        <div class="account">
          <template v-if="session">
            <span class="avatar">{{ session.username.slice(0, 1).toUpperCase() }}</span>
            <span class="username">{{ session.username }}</span>
            <button class="text-button muted" @click="signOut">退出</button>
          </template>
          <RouterLink v-else to="/login" class="button small">登录 / 注册</RouterLink>
        </div>
      </div>
    </header>
    <div v-if="route.meta.admin" class="admin-strip">
      <div>
        <span>管理工作台</span>
        <RouterLink to="/admin/orders">订单处理</RouterLink>
        <RouterLink to="/admin/products">商品与库存</RouterLink>
      </div>
    </div>
    <main><RouterView :key="route.fullPath" /></main>
    <footer>
      <span>Java 订单支付系统</span>
      <span>从选购到交付，每一步清晰可见。</span>
    </footer>
    <Transition name="toast">
      <div v-if="notice" class="toast" role="status">{{ notice }}</div>
    </Transition>
  </div>
</template>
