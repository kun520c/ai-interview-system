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
  overflow-wrap: anywhere;
  border: 1px solid var(--border);
  border-left: 3px solid var(--primary);
}

.is-info {
  background: var(--primary-soft);
  color: var(--text);
}

.is-success {
  border-left-color: var(--success);
  background: var(--success-soft);
  color: var(--success);
}

.is-warning {
  border-left-color: var(--warning);
  background: var(--warning-soft);
  color: var(--warning);
}

.is-danger {
  border-left-color: var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
}
</style>
