<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <h1>{{ title }}</h1>
    <p v-if="description" class="u-muted">{{ description }}</p>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
}

.page-header p {
  margin-top: 8px;
  font-size: 14px;
}

.page-header-actions {
  margin-top: 16px;
}
</style>
