<script setup lang="ts">
import { Inbox } from '@lucide/vue'
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
    <span class="empty-state-icon" aria-hidden="true"><Inbox :size="24" :stroke-width="1.5" /></span>
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
  justify-items: center;
  text-align: center;
  gap: 10px;
  padding: 40px 24px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.empty-state-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 8px;
  border: 1px solid var(--border);
  border-radius: 14px;
  color: var(--primary);
  background: var(--surface-subtle);
}

.empty-state h1, .empty-state h2 {
  font-size: 18px;
  font-weight: 650;
}

.empty-state p {
  font-size: 13px;
  max-width: 440px;
}

.empty-state-action {
  margin-top: 8px;
}
</style>
