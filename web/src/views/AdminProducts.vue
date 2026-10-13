<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { api, post, put, errorMessage } from '../api';
import { money, priceToCents } from '../format';
import { notify } from '../state';
import type { Product, Inventory } from '../types';
import Feedback from '../components/Feedback.vue';
const products = ref<Product[]>([]),
  loading = ref(true),
  error = ref(''),
  page = ref(0),
  keyword = ref(''),
  editing = ref(false),
  editId = ref(''),
  busy = ref(false),
  formError = ref('');
const form = reactive({ name: '', skuName: '', price: '', onShelf: false });
const stockProduct = ref<Product>(),
  inventory = ref<Inventory>(),
  stockTotal = ref(0),
  stockLoading = ref(false),
  stockError = ref('');
async function load(reset = false) {
  if (reset) page.value = 0;
  loading.value = true;
  error.value = '';
  try {
    products.value = await api<Product[]>(
      `/admin/products?page=${page.value}&size=20&keyword=${encodeURIComponent(keyword.value.trim())}`,
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    loading.value = false;
  }
}
function openEditor(product?: Product) {
  editId.value = product?.skuId || '';
  Object.assign(form, {
    name: product?.spuName || '',
    skuName: product?.skuName || '',
    price: product ? (Number(product.priceCent) / 100).toFixed(2) : '',
    onShelf: product?.onShelf || false,
  });
  formError.value = '';
  editing.value = true;
}
async function save() {
  busy.value = true;
  formError.value = '';
  try {
    const body = {
      name: form.name.trim(),
      skuName: form.skuName.trim(),
      priceCent: priceToCents(form.price),
      onShelf: form.onShelf,
    };
    if (!body.name || !body.skuName) throw new Error('请填写商品名称和规格名称。');
    if (editId.value) await put(`/admin/products/${editId.value}`, body);
    else await post('/admin/products', body);
    editing.value = false;
    notify('商品信息已保存。');
    await load();
  } catch (e) {
    formError.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function openStock(product: Product) {
  stockProduct.value = product;
  stockLoading.value = true;
  stockError.value = '';
  inventory.value = undefined;
  try {
    inventory.value = await api<Inventory>(`/admin/inventory/${product.skuId}`);
    stockTotal.value = inventory.value.total;
  } catch (e) {
    stockError.value = errorMessage(e);
  } finally {
    stockLoading.value = false;
  }
}
async function saveStock() {
  busy.value = true;
  stockError.value = '';
  try {
    if (!Number.isInteger(stockTotal.value) || stockTotal.value < 0 || stockTotal.value > 1000000)
      throw new Error('库存总量应为 0 到 1000000 之间的整数。');
    await put(`/admin/inventory/${stockProduct.value!.skuId}`, { total: stockTotal.value });
    notify('库存已更新。');
    await openStock(stockProduct.value!);
  } catch (e) {
    stockError.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
onMounted(() => load());
</script>
<template>
  <div class="page-heading heading-row">
    <div>
      <p class="eyebrow">OPERATIONS / CATALOG</p>
      <h1>商品与库存</h1>
      <p>维护商品信息、销售状态与可售库存。</p>
    </div>
    <button class="button" @click="openEditor()">＋ 新增商品</button>
  </div>
  <section class="panel">
    <div class="table-toolbar">
      <form class="search" @submit.prevent="load(true)">
        <label class="sr-only" for="admin-search">搜索商品</label>
        <input id="admin-search" v-model="keyword" placeholder="搜索商品名称" maxlength="100" />
        <button>搜索</button>
      </form>
      <button class="text-button" :disabled="loading" @click="load()">刷新列表</button>
    </div>
    <Feedback
      :loading="loading"
      :error="error"
      :empty="!products.length"
      empty-text="暂无商品，点击新增商品开始维护"
      @retry="load()"
    />
    <div v-if="!loading && !error && products.length" class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>商品 / 规格</th>
            <th>商品编号</th>
            <th>销售价格</th>
            <th>销售状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="product in products" :key="product.skuId">
            <td>
              <strong>{{ product.spuName }}</strong>
              <small>{{ product.skuName }}</small>
            </td>
            <td>{{ product.skuId }}</td>
            <td class="nowrap">¥{{ money(product.priceCent) }}</td>
            <td>
              <span class="badge" :class="product.onShelf ? 'good' : 'neutral'">
                {{ product.onShelf ? '在售' : '已下架' }}
              </span>
            </td>
            <td>
              <div class="row-actions">
                <button class="text-button" @click="openEditor(product)">编辑</button>
                <button class="text-button" @click="openStock(product)">库存</button>
                <RouterLink
                  v-if="product.onShelf"
                  class="text-button"
                  :to="`/products/${product.skuId}`"
                >
                  预览
                </RouterLink>
              </div>
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
        :disabled="loading || products.length < 20"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </div>
  </section>
  <div v-if="editing" class="modal-backdrop" @click.self="!busy && (editing = false)">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="product-edit-title">
      <div class="modal-heading">
        <h2 id="product-edit-title">{{ editId ? '编辑商品' : '新增商品' }}</h2>
        <button class="close-button" :disabled="busy" @click="editing = false" aria-label="关闭">
          ×
        </button>
      </div>
      <form @submit.prevent="save">
        <label for="spu-name">商品名称</label>
        <input
          id="spu-name"
          v-model="form.name"
          required
          maxlength="128"
          placeholder="例如：便携随行杯"
        />
        <label for="sku-name">规格名称</label>
        <input
          id="sku-name"
          v-model="form.skuName"
          required
          maxlength="128"
          placeholder="例如：便携随行杯 · 雾白 500ml"
        />
        <label for="price">销售价格（元）</label>
        <input id="price" v-model="form.price" inputmode="decimal" required placeholder="0.00" />
        <label class="checkbox-label">
          <input v-model="form.onShelf" type="checkbox" />
          上架销售
        </label>
        <p v-if="!editId" class="muted">新增商品后，请在库存管理中设置库存总量。</p>
        <p v-if="formError" class="inline-error" role="alert">{{ formError }}</p>
        <div class="modal-actions">
          <button class="button secondary" type="button" :disabled="busy" @click="editing = false">
            取消
          </button>
          <button class="button" :disabled="busy">{{ busy ? '正在保存…' : '保存商品' }}</button>
        </div>
      </form>
    </section>
  </div>
  <div v-if="stockProduct" class="modal-backdrop" @click.self="!busy && (stockProduct = undefined)">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="stock-title">
      <div class="modal-heading">
        <h2 id="stock-title">库存管理</h2>
        <button
          class="close-button"
          :disabled="busy"
          @click="stockProduct = undefined"
          aria-label="关闭"
        >
          ×
        </button>
      </div>
      <p class="muted">{{ stockProduct.skuName }}</p>
      <div v-if="stockLoading" class="feedback">
        <span class="spinner"></span>
        正在读取库存…
      </div>
      <template v-else>
        <div v-if="inventory" class="stock-stats">
          <div>
            <span>可售</span>
            <strong>{{ inventory.available }}</strong>
          </div>
          <div>
            <span>订单预留</span>
            <strong>{{ inventory.reserved }}</strong>
          </div>
          <div>
            <span>已售</span>
            <strong>{{ inventory.sold }}</strong>
          </div>
        </div>
        <form @submit.prevent="saveStock">
          <label for="stock-total">库存总量</label>
          <input
            id="stock-total"
            v-model.number="stockTotal"
            type="number"
            min="0"
            max="1000000"
            step="1"
            required
          />
          <p class="muted">总量包含可售、订单预留与已售数量，不能低于预留和已售之和。</p>
          <p v-if="stockError" class="inline-error" role="alert">{{ stockError }}</p>
          <div class="modal-actions">
            <button
              class="button secondary"
              type="button"
              :disabled="busy"
              @click="stockProduct = undefined"
            >
              关闭
            </button>
            <button class="button" :disabled="busy">{{ busy ? '正在保存…' : '更新库存' }}</button>
          </div>
        </form>
      </template>
    </section>
  </div>
</template>
