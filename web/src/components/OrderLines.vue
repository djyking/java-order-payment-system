<script setup lang="ts">
import type { Line } from '../types';
import { money } from '../format';
defineProps<{ items: Line[] }>();
</script>
<template>
  <div class="order-lines">
    <div class="line-heading">
      <span>商品信息</span>
      <span>单价 / 数量</span>
      <span>小计</span>
    </div>
    <div v-for="item in items" :key="item.skuId" class="order-line">
      <div class="line-product">
        <span class="mini-product">{{ item.skuName.slice(0, 1) }}</span>
        <span>
          {{ item.skuName }}
          <small>商品编号 {{ item.skuId }}</small>
        </span>
      </div>
      <span class="line-unit">
        ¥{{ money(item.priceCent) }}
        <small>× {{ item.quantity }}</small>
      </span>
      <strong>¥{{ money(item.amountCent) }}</strong>
    </div>
  </div>
</template>
