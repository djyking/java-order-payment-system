<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import { api, post, errorMessage } from '../api';
import { money, stateLabel, dateTime } from '../format';
import { notify } from '../state';
import type { Order } from '../types';
import Feedback from '../components/Feedback.vue';
import OrderLines from '../components/OrderLines.vue';
const route = useRoute(),
  admin = computed(() => Boolean(route.meta.admin)),
  order = ref<Order>(),
  loading = ref(true),
  error = ref(''),
  actionError = ref(''),
  busy = ref(false),
  confirmAction = ref('');
const no = encodeURIComponent(String(route.params.no)),
  userQuery = `?userId=${encodeURIComponent(String(route.query.userId || ''))}`;
async function load() {
  loading.value = true;
  error.value = '';
  try {
    order.value = await api<Order>(
      admin.value ? `/admin/order-operations/${no}${userQuery}` : `/orders/${no}`,
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
async function act() {
  busy.value = true;
  actionError.value = '';
  try {
    await post(
      admin.value
        ? `/admin/order-operations/${no}/deliver${userQuery}`
        : `/orders/${no}/${confirmAction.value}`,
    );
    notify('订单已更新。');
    confirmAction.value = '';
    await load();
  } catch (e) {
    actionError.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
const steps = ['WAIT_PAY', 'WAIT_DELIVERY', 'WAIT_RECEIVE', 'COMPLETED'];
onMounted(load);
</script>
<template>
  <RouterLink class="back-link" :to="admin ? '/admin/orders' : '/orders'">
    ← 返回订单列表
  </RouterLink>
  <Feedback :loading="loading" :error="error" @retry="load" />
  <template v-if="order && !loading && !error">
    <div class="page-heading heading-row">
      <div>
        <p class="eyebrow">ORDER DETAILS</p>
        <h1>订单详情</h1>
        <p class="order-number">{{ order.orderNo }}</p>
      </div>
      <button class="button secondary small" @click="load">刷新状态</button>
    </div>
    <section class="order-status-panel">
      <div>
        <span class="badge" :class="order.status.toLowerCase()">
          {{ stateLabel(order.status) }}
        </span>
        <h2>
          {{
            order.status === 'WAIT_PAY'
              ? '订单已创建，等待支付'
              : order.status === 'WAIT_DELIVERY'
                ? '支付已完成，等待商家发货'
                : order.status === 'WAIT_RECEIVE'
                  ? '订单已发货，等待确认收货'
                  : order.status === 'COMPLETED'
                    ? '订单已完成，感谢你的选购'
                    : '订单已取消'
          }}
        </h2>
        <p>创建时间 {{ dateTime(order.createTime) }}</p>
      </div>
      <div class="status-actions">
        <RouterLink
          v-if="!admin && order.status === 'WAIT_PAY'"
          class="button"
          :to="`/checkout/${order.orderNo}`"
        >
          去支付
        </RouterLink>
        <button
          v-if="!admin && order.status === 'WAIT_PAY'"
          class="button secondary"
          @click="confirmAction = 'cancel'"
        >
          取消订单
        </button>
        <button
          v-if="!admin && order.status === 'WAIT_RECEIVE'"
          class="button"
          @click="confirmAction = 'receive'"
        >
          确认收货
        </button>
        <button
          v-if="admin && order.status === 'WAIT_DELIVERY'"
          class="button"
          @click="confirmAction = 'deliver'"
        >
          确认发货
        </button>
      </div>
    </section>
    <div v-if="order.status !== 'CANCELED'" class="order-progress">
      <div
        v-for="(step, index) in steps"
        :key="step"
        :class="{ done: steps.indexOf(order.status) >= index }"
      >
        <i>{{ index + 1 }}</i>
        <span>{{ stateLabel(step) }}</span>
      </div>
    </div>
    <div class="split-layout">
      <section class="panel">
        <h2>商品清单</h2>
        <OrderLines :items="order.items" />
      </section>
      <aside class="panel summary-panel">
        <h2>订单信息</h2>
        <dl>
          <dt>支付状态</dt>
          <dd>{{ order.payStatus === 'PAID' ? '已支付' : '未支付' }}</dd>
          <template v-if="admin">
            <dt>买家编号</dt>
            <dd class="break-word">{{ order.userId }}</dd>
          </template>
          <dt>订单状态</dt>
          <dd>{{ stateLabel(order.status) }}</dd>
        </dl>
        <div class="summary-total">
          <span>订单金额</span>
          <strong>¥{{ money(order.totalAmountCent) }}</strong>
        </div>
      </aside>
    </div>
  </template>
  <div v-if="confirmAction" class="modal-backdrop" @click.self="!busy && (confirmAction = '')">
    <section class="modal compact" role="dialog" aria-modal="true" aria-labelledby="action-title">
      <h2 id="action-title">
        {{
          confirmAction === 'cancel'
            ? '确认取消这笔订单？'
            : confirmAction === 'receive'
              ? '确认已收到商品？'
              : '确认商品已发出？'
        }}
      </h2>
      <p class="muted">
        {{
          confirmAction === 'cancel'
            ? '取消后将释放本次订单的商品库存。'
            : confirmAction === 'receive'
              ? '确认后订单将完成，请在收到商品后操作。'
              : '操作后订单将进入待收货状态，请确认已完成实际发货。'
        }}
      </p>
      <p v-if="actionError" class="inline-error" role="alert">{{ actionError }}</p>
      <div class="modal-actions">
        <button
          class="button secondary"
          :disabled="busy"
          @click="
            confirmAction = '';
            actionError = '';
          "
        >
          返回
        </button>
        <button class="button" :disabled="busy" @click="act">
          {{ busy ? '正在处理…' : '确认操作' }}
        </button>
      </div>
    </section>
  </div>
</template>
