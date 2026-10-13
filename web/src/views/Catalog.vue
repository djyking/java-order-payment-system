<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { api, errorMessage } from '../api';
import { money } from '../format';
import type { Product } from '../types';
import Feedback from '../components/Feedback.vue';
const products = ref<Product[]>([]),
  loading = ref(true),
  error = ref(''),
  keyword = ref(''),
  page = ref(0);
async function load(reset = false) {
  if (reset) page.value = 0;
  loading.value = true;
  error.value = '';
  try {
    products.value = await api<Product[]>(
      `/products?page=${page.value}&size=12&keyword=${encodeURIComponent(keyword.value.trim())}`,
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
  <section class="catalog-hero">
    <div>
      <p class="eyebrow">精选商品 · 安心选购</p>
      <h1>
        选你所需，
        <br />
        <span>让好物如期而至。</span>
      </h1>
      <p>浏览商品、查看价格，轻松管理每一笔订单。</p>
      <a class="button" href="#catalog">
        开始选购
        <span>↗</span>
      </a>
    </div>
    <div class="hero-art" aria-hidden="true">
      <div class="art-orbit"></div>
      <div class="art-box box-one">
        <i></i>
        <span>J.</span>
      </div>
      <div class="art-box box-two"><i></i></div>
      <div class="art-caption">
        <span class="status-dot"></span>
        YOUR NEXT GOOD FIND
      </div>
    </div>
  </section>
  <section id="catalog">
    <div class="section-heading">
      <div>
        <p class="eyebrow">THE COLLECTION</p>
        <h2>在售商品</h2>
      </div>
      <form class="search" @submit.prevent="load(true)">
        <label class="sr-only" for="product-search">搜索商品</label>
        <input id="product-search" v-model="keyword" placeholder="搜索商品名称" maxlength="100" />
        <button type="submit" aria-label="搜索商品">搜索</button>
      </form>
    </div>
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!products.length"
      empty-text="没有找到符合条件的商品"
      @retry="load()"
    />
    <div v-if="!loading && !error && products.length" class="product-grid">
      <RouterLink
        v-for="(product, index) in products"
        :key="product.skuId"
        :to="`/products/${product.skuId}`"
        class="product-card"
      >
        <div class="product-visual" :class="`tone-${index % 4}`">
          <span class="visual-watermark">{{ product.skuName.slice(0, 1) }}</span>
          <span class="product-index">{{ String(page * 12 + index + 1).padStart(2, '0') }}</span>
          <span class="product-cta">查看商品 ↗</span>
        </div>
        <div class="product-copy">
          <p>{{ product.spuName }}</p>
          <h3>{{ product.skuName }}</h3>
          <div>
            <strong>¥{{ money(product.priceCent) }}</strong>
            <span>在售</span>
          </div>
        </div>
      </RouterLink>
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
        :disabled="products.length < 12"
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
