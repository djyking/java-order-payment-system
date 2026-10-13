<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string; empty?: boolean; emptyText?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="loading" class="feedback" role="status">
    <span class="spinner"></span>
    正在加载，请稍候…
  </div>
  <div v-else-if="error" class="feedback error-state" role="alert">
    <strong>暂时无法完成操作</strong>
    <p>{{ error }}</p>
    <button class="button secondary small" @click="$emit('retry')">重新尝试</button>
  </div>
  <div v-else-if="empty" class="feedback">
    <span class="empty-mark">—</span>
    <strong>{{ emptyText || '暂无内容' }}</strong>
    <slot />
  </div>
</template>
