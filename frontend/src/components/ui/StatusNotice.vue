<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'info' | 'success' | 'warning' | 'danger'
    role?: 'status' | 'alert'
  }>(),
  { tone: 'info', role: 'status' },
)
</script>

<template>
  <div class="status-notice" :class="`is-${tone}`" :role="role">
    <slot />
  </div>
</template>

<style scoped>
.status-notice {
  padding: 12px 16px;
  border-radius: var(--radius);
  font-size: 14px;
  overflow-wrap: break-word;
}

.is-info {
  background: var(--primary-soft);
  color: var(--text);
}

.is-success {
  background: var(--success-soft);
  color: var(--success);
}

.is-warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.is-danger {
  background: var(--danger-soft);
  color: var(--danger);
}
</style>
