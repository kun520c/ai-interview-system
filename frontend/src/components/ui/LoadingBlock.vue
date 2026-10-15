<script setup lang="ts">
defineProps<{
  label?: string
}>()
</script>

<template>
  <div class="loading-block" role="status">
    <span class="loading-block-bar" aria-hidden="true"></span>
    <p>{{ label ?? '加载中' }}</p>
  </div>
</template>

<style scoped>
.loading-block {
  display: grid;
  gap: 12px;
  color: var(--text-2);
  font-size: 14px;
}

.loading-block-bar {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}
</style>
