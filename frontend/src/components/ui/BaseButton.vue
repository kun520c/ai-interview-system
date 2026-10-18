<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'danger'
    disabled?: boolean
    loading?: boolean
    type?: 'button' | 'submit'
  }>(),
  {
    variant: 'primary',
    disabled: false,
    loading: false,
    type: 'button',
  },
)
</script>

<template>
  <button
    class="base-button"
    :class="`is-${variant}`"
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading ? 'true' : undefined"
  >
    <span v-if="loading" class="base-button-spinner" aria-hidden="true"></span>
    <slot />
  </button>
</template>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 10px 18px;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  transition: background var(--move), opacity var(--move);
}

.base-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.is-primary {
  background: var(--primary);
  color: #fff;
  box-shadow: var(--shadow-sm);
}

.is-primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.is-ghost {
  background: transparent;
  color: var(--text);
  box-shadow: inset 0 0 0 1px var(--border);
}

.is-danger {
  background: var(--danger);
  color: #fff;
  box-shadow: var(--shadow-sm);
}

.base-button-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }

}

.is-ghost:hover:not(:disabled) {
  background: var(--surface-subtle);
}
</style>
