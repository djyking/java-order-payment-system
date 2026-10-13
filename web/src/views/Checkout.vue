<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import QRCode from 'qrcode';
import { api, post, errorMessage } from '../api';
import { money, stateLabel, safePayUrl, dateTime } from '../format';
import type { Order, PaymentMethod, Checkout } from '../types';
import Feedback from '../components/Feedback.vue';
const route = useRoute(),
  order = ref<Order>(),
  methods = ref<PaymentMethod[]>([]),
  selected = ref(''),
  checkout = ref<Checkout>(),
  qrImage = ref(''),
  loading = ref(true),
  error = ref(''),
  actionError = ref(''),
  busy = ref(false),
  checking = ref(false);
const no = encodeURIComponent(String(route.params.no)),
  available = computed(() => methods.value.filter((m) => m.available)),
  payLink = computed(() => safePayUrl(checkout.value?.payUrl));
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [value, channels] = await Promise.all([
      api<Order>(`/orders/${no}`),
      api<PaymentMethod[]>('/payments/methods'),
    ]);
    order.value = value;
    methods.value = channels;
    selected.value = channels.find((m) => m.available)?.code || '';
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
async function start() {
  if (!selected.value || busy.value) return;
  busy.value = true;
  actionError.value = '';
  try {
    checkout.value = await post<Checkout>(`/orders/${no}/pay`, { methodCode: selected.value });
    qrImage.value = checkout.value.qrCode
      ? await QRCode.toDataURL(checkout.value.qrCode, { width: 240, margin: 2 })
      : '';
  } catch (e) {
    actionError.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function refresh() {
  checking.value = true;
  actionError.value = '';
  try {
    order.value = await api<Order>(`/orders/${no}`);
    if (checkout.value)
      checkout.value = await api<Checkout>(
        `/payments/${encodeURIComponent(checkout.value.payOrderNo)}`,
      );
    if (order.value.payStatus !== 'PAID') actionError.value = '尚未确认付款，请稍后刷新。';
  } catch (e) {
    actionError.value = errorMessage(e);
  } finally {
    checking.value = false;
  }
}
onMounted(load);
</script>
<template>
  <RouterLink class="back-link" :to="`/orders/${route.params.no}`">← 查看订单详情</RouterLink>
  <div class="page-heading">
    <p class="eyebrow">CHECKOUT / 02</p>
    <h1>选择支付方式</h1>
    <p>订单已保存，可随时从「我的订单」返回这里。</p>
  </div>
  <Feedback :loading="loading" :error="error" @retry="load" />
  <div v-if="order && !loading && !error" class="split-layout checkout-layout">
    <section class="panel">
      <div class="section-heading compact-heading">
        <h2>支付方式</h2>
        <span class="muted">以实际可用方式为准</span>
      </div>
      <template v-if="order.status === 'WAIT_PAY'">
        <div v-if="!available.length" class="info-box">
          <strong>暂时没有可用的支付方式</strong>
          <p>当前订单尚未付款。请稍后查看，或取消订单后重新选购。</p>
        </div>
        <div class="payment-methods">
          <label
            v-for="method in methods"
            :key="method.code"
            class="payment-method"
            :class="{ selected: selected === method.code, unavailable: !method.available }"
          >
            <input
              v-model="selected"
              type="radio"
              name="payment-method"
              :value="method.code"
              :disabled="!method.available || busy || !!checkout"
            />
            <span
              class="method-icon"
              :class="
                method.code.includes('WECHAT')
                  ? 'wechat'
                  : method.code.includes('ALIPAY')
                    ? 'alipay'
                    : ''
              "
            >
              {{ method.name.slice(0, 1) }}
            </span>
            <span class="method-text">
              <strong>{{ method.name }}</strong>
              <small v-if="!method.available">暂未开放，请稍后查看</small>
              <small v-else>
                {{
                  method.actionType === 'QR_CODE'
                    ? '扫码付款'
                    : method.actionType === 'REDIRECT'
                      ? '跳转收银台付款'
                      : '按支付指引完成付款'
                }}
              </small>
            </span>
            <span class="badge" :class="method.available ? 'good' : 'neutral'">
              {{ method.available ? '可用' : '暂不可用' }}
            </span>
          </label>
        </div>
        <p v-if="!methods.length" class="muted">暂无支付方式。</p>
        <div v-if="checkout" class="checkout-action">
          <h3>支付指引</h3>
          <p class="muted">支付单号 {{ checkout.payOrderNo }}</p>
          <img v-if="qrImage" :src="qrImage" alt="付款二维码" width="240" height="240" />
          <a
            v-if="payLink"
            class="button"
            :href="payLink"
            target="_blank"
            rel="noopener noreferrer"
          >
            打开支付收银台 ↗
          </a>
          <p v-else-if="!qrImage" class="muted">当前支付方式未提供付款入口，请联系管理员。</p>
          <p v-if="checkout.expiresAt" class="muted">有效期至 {{ dateTime(checkout.expiresAt) }}</p>
          <button class="button secondary" :disabled="checking" @click="refresh">
            {{ checking ? '正在查询…' : '我已付款，查询结果' }}
          </button>
        </div>
        <p v-if="actionError" class="inline-error" role="alert">{{ actionError }}</p>
        <button v-if="!checkout" class="button full" :disabled="!selected || busy" @click="start">
          {{ busy ? '正在准备支付…' : '确认支付方式' }}
        </button>
      </template>
      <div v-else class="feedback">
        <span class="badge" :class="order.status.toLowerCase()">
          {{ stateLabel(order.status) }}
        </span>
        <h3>{{ order.payStatus === 'PAID' ? '付款已确认' : '当前订单无需支付' }}</h3>
        <RouterLink class="button" :to="`/orders/${order.orderNo}`">查看订单</RouterLink>
      </div>
    </section>
    <aside class="panel summary-panel">
      <h2>订单摘要</h2>
      <p class="order-number muted">{{ order.orderNo }}</p>
      <div v-for="item in order.items" :key="item.skuId" class="summary-item">
        <span>
          {{ item.skuName }}
          <small>× {{ item.quantity }}</small>
        </span>
        <strong>¥{{ money(item.amountCent) }}</strong>
      </div>
      <div class="summary-total">
        <span>应付金额</span>
        <strong>¥{{ money(order.totalAmountCent) }}</strong>
      </div>
      <p class="muted">付款结果以订单状态为准。完成付款后，请点击查询结果确认最新进度。</p>
    </aside>
  </div>
</template>
