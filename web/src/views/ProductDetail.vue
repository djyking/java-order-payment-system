<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, errorMessage } from '../api';
import { money } from '../format';
import type { Product } from '../types';
import Feedback from '../components/Feedback.vue';
const route = useRoute(),
  router = useRouter(),
  product = ref<Product>(),
  loading = ref(true),
  error = ref(''),
  quantity = ref(1);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    product.value = await api<Product>(`/skus/${encodeURIComponent(String(route.params.id))}`);
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
function buy() {
  void router.push({
    path: '/confirm',
    query: { skuId: product.value!.skuId, quantity: String(quantity.value) },
  });
}
onMounted(load);
</script>
<template>
  <RouterLink class="back-link" to="/">← 返回商品列表</RouterLink>
  <Feedback :loading="loading" :error="error" @retry="load" />
  <section v-if="product && !loading && !error" class="product-detail">
    <div class="detail-visual product-visual tone-0">
      <span class="visual-watermark">{{ product.skuName.slice(0, 1) }}</span>
      <span class="detail-visual-label">THE COLLECTION / {{ product.skuId }}</span>
    </div>
    <div class="detail-copy">
      <p class="eyebrow">{{ product.spuName }}</p>
      <h1>{{ product.skuName }}</h1>
      <span class="badge" :class="product.onShelf ? 'good' : 'neutral'">
        {{ product.onShelf ? '正在销售' : '已下架' }}
      </span>
      <div class="detail-price">
        <small>商品价格</small>
        <strong>¥{{ money(product.priceCent) }}</strong>
      </div>
      <form @submit.prevent="buy">
        <label for="quantity">购买数量</label>
        <div class="quantity-control">
          <button type="button" :disabled="quantity <= 1" @click="quantity--" aria-label="减少数量">
            −
          </button>
          <input
            id="quantity"
            v-model.number="quantity"
            type="number"
            min="1"
            max="999"
            step="1"
            required
          />
          <button
            type="button"
            :disabled="quantity >= 999"
            @click="quantity++"
            aria-label="增加数量"
          >
            +
          </button>
        </div>
        <button class="button full" :disabled="!product.onShelf">
          确认选购
          <span>→</span>
        </button>
      </form>
      <p class="muted detail-note">
        确认订单时将核对最新价格与商品状态，提交订单后可在「我的订单」中查看进度。
      </p>
    </div>
  </section>
</template>
