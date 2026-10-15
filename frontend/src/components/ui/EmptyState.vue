<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
    headingTag?: 'h1' | 'h2'
  }>(),
  { headingTag: 'h2' },
)
</script>

<template>
  <div class="empty-state">
    <component :is="headingTag">{{ title }}</component>
    <p v-if="description" class="u-muted">{{ description }}</p>
    <div v-if="$slots.action" class="empty-state-action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: grid;
  gap: 8px;
  padding: 24px 0;
}

.empty-state h1,
.empty-state h2 {
  font-size: 20px;
  font-weight: 650;
}

.empty-state-action {
  margin-top: 8px;
}
</style>
