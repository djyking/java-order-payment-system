<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { api, errorMessage } from '../api';
import { money, stateLabel, dateTime } from '../format';
import type { Order } from '../types';
import Feedback from '../components/Feedback.vue';
const orders = ref<Order[]>([]),
  page = ref(0),
  loading = ref(true),
  error = ref('');
async function load() {
  loading.value = true;
  error.value = '';
  try {
    orders.value = await api<Order[]>(`/orders?page=${page.value}&size=10`);
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading heading-row">
    <div>
      <p class="eyebrow">MY ORDERS</p>
      <h1>我的订单</h1>
      <p>从下单到收货，随时掌握最新进度。</p>
    </div>
    <button class="button secondary small" :disabled="loading" @click="load">刷新订单</button>
  </div>
  <Feedback
    :loading="loading"
    :error="error"
    :empty="!orders.length"
    empty-text="还没有订单，去发现喜欢的商品吧"
    @retry="load"
  >
    <RouterLink class="button small" to="/">去选购</RouterLink>
  </Feedback>
  <div v-if="!loading && !error" class="order-list">
    <article v-for="order in orders" :key="order.orderNo" class="order-card">
      <header>
        <span>订单 {{ order.orderNo }}</span>
        <time>{{ dateTime(order.createTime) }}</time>
        <span class="badge" :class="order.status.toLowerCase()">
          {{ stateLabel(order.status) }}
        </span>
      </header>
      <div class="order-card-body">
        <span class="mini-product">{{ order.items[0]?.skuName.slice(0, 1) || '单' }}</span>
        <div class="order-card-name">
          <h3>{{ order.items[0]?.skuName || '商品订单' }}</h3>
          <p>共 {{ order.items.reduce((sum, item) => sum + item.quantity, 0) }} 件商品</p>
        </div>
        <strong class="order-card-amount">¥{{ money(order.totalAmountCent) }}</strong>
        <div class="order-card-actions">
          <RouterLink
            v-if="order.status === 'WAIT_PAY'"
            class="button small"
            :to="`/checkout/${order.orderNo}`"
          >
            去支付
          </RouterLink>
          <RouterLink class="button secondary small" :to="`/orders/${order.orderNo}`">
            订单详情
          </RouterLink>
        </div>
      </div>
    </article>
  </div>
  <div v-if="!loading && !error" class="pagination">
    <button
      class="button secondary small"
      :disabled="page === 0"
      @click="
        page--;
        load();
      "
    >
      上一页
    </button>
    <span>第 {{ page + 1 }} 页</span>
    <button
      class="button secondary small"
      :disabled="orders.length < 10"
      @click="
        page++;
        load();
      "
    >
      下一页
    </button>
  </div>
</template>
