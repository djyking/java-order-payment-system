<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { api, errorMessage } from '../api';
import { money, states, stateLabel, dateTime } from '../format';
import type { AdminOrder } from '../types';
import Feedback from '../components/Feedback.vue';
const orders = ref<AdminOrder[]>([]),
  page = ref(0),
  status = ref(''),
  loading = ref(true),
  error = ref('');
async function load(reset = false) {
  if (reset) page.value = 0;
  loading.value = true;
  error.value = '';
  try {
    orders.value = await api<AdminOrder[]>(
      `/admin/orders?page=${page.value}&size=20${status.value ? `&status=${status.value}` : ''}`,
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
onMounted(() => load());
</script>
<template>
  <div class="page-heading heading-row">
    <div>
      <p class="eyebrow">OPERATIONS / ORDERS</p>
      <h1>订单处理</h1>
      <p>查看全部订单，跟进支付状态与商品交付。</p>
    </div>
    <button class="button secondary small" :disabled="loading" @click="load()">刷新列表</button>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <div class="filter-field">
        <label for="status">订单状态</label>
        <select id="status" v-model="status" @change="load(true)">
          <option value="">全部状态</option>
          <option v-for="(label, key) in states" :key="key" :value="key">{{ label }}</option>
        </select>
      </div>
      <span class="muted">状态更新后，请刷新查看最新列表。</span>
    </div>
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!orders.length"
      empty-text="暂无符合条件的订单"
      @retry="load()"
    />
    <div v-if="!loading && !error && orders.length" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>订单 / 商品</th>
            <th>买家编号</th>
            <th>金额</th>
            <th>订单状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="order in orders" :key="order.order_no">
            <td>
              <strong>{{ order.order_no }}</strong>
              <small>{{ order.first_item_name || '商品订单' }} · {{ order.item_count }} 件</small>
            </td>
            <td>{{ order.user_id }}</td>
            <td class="nowrap">¥{{ money(order.total_amount_cent) }}</td>
            <td>
              <span class="badge" :class="order.status.toLowerCase()">
                {{ stateLabel(order.status) }}
              </span>
            </td>
            <td class="nowrap muted">{{ dateTime(order.create_time) }}</td>
            <td>
              <RouterLink
                class="text-button nowrap"
                :to="{ path: `/admin/orders/${order.order_no}`, query: { userId: order.user_id } }"
              >
                {{ order.status === 'WAIT_DELIVERY' ? '处理发货' : '查看详情' }} →
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pagination">
      <button
        class="button secondary small"
        :disabled="loading || page === 0"
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
        :disabled="loading || orders.length < 20"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </div>
  </section>
</template>
