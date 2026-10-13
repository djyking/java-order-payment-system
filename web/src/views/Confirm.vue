<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { post, errorMessage } from '../api';
import { money, submitKey, clearSubmitKey } from '../format';
import { session } from '../state';
import type { Settlement, Order } from '../types';
import Feedback from '../components/Feedback.vue';
import OrderLines from '../components/OrderLines.vue';
const route = useRoute(),
  router = useRouter(),
  settlement = ref<Settlement>(),
  loading = ref(true),
  error = ref(''),
  submitError = ref(''),
  busy = ref(false);
const skuId = String(route.query.skuId || ''),
  quantity = Number(route.query.quantity || 1);
const items = [{ skuId, quantity }];
async function load() {
  loading.value = true;
  error.value = '';
  try {
    if (!/^\d+$/.test(skuId) || !Number.isInteger(quantity) || quantity < 1 || quantity > 999)
      throw new Error('商品或购买数量无效，请重新选择。');
    settlement.value = await post<Settlement>('/orders/settlement', { items });
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
async function submit() {
  if (busy.value) return;
  busy.value = true;
  submitError.value = '';
  try {
    const userId = session.value!.userId;
    const order = await post<Order>(
      '/orders',
      { items },
      { 'X-Idempotency-Token': submitKey(userId, skuId, quantity) },
    );
    clearSubmitKey(userId, skuId, quantity);
    await router.replace(`/checkout/${order.orderNo}`);
  } catch (e) {
    submitError.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <RouterLink class="back-link" :to="`/products/${skuId}`">← 返回商品</RouterLink>
  <div class="page-heading">
    <p class="eyebrow">CHECKOUT / 01</p>
    <h1>确认订单</h1>
    <p>核对商品与金额，再提交你的订单。</p>
  </div>
  <div class="step-bar">
    <span class="current">1 确认订单</span>
    <i></i>
    <span>2 选择支付</span>
    <i></i>
    <span>3 查看进度</span>
  </div>
  <Feedback :loading="loading" :error="error" @retry="load" />
  <div v-if="settlement && !loading && !error" class="split-layout">
    <section class="panel">
      <h2>选购清单</h2>
      <OrderLines :items="settlement.items" />
    </section>
    <aside class="panel summary-panel">
      <h2>订单汇总</h2>
      <dl>
        <dt>商品数量</dt>
        <dd>{{ quantity }} 件</dd>
        <dt>商品金额</dt>
        <dd>¥{{ money(settlement.totalAmountCent) }}</dd>
      </dl>
      <div class="summary-total">
        <span>应付合计</span>
        <strong>¥{{ money(settlement.totalAmountCent) }}</strong>
      </div>
      <p class="muted">订单提交后将为你保留库存，请及时完成支付。最终金额以下单结果为准。</p>
      <p v-if="submitError" class="inline-error" role="alert">{{ submitError }}</p>
      <button class="button full" :disabled="busy" @click="submit">
        {{ busy ? '正在提交…' : '提交订单，选择支付' }}
      </button>
    </aside>
  </div>
</template>
