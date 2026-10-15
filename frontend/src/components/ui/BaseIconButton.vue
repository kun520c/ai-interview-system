<script setup lang="ts">
defineProps<{
  label: string
  disabled?: boolean
}>()
</script>

<template>
  <button class="icon-button" type="button" :aria-label="label" :disabled="disabled">
    <slot />
  </button>
</template>

<style scoped>
.icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
  color: var(--text);
}

.icon-button:hover:not(:disabled) {
  background: var(--surface-subtle);
}

.icon-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
