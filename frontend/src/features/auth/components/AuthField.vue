<script setup lang="ts">
defineProps<{
  label: string
  forId: string
  error?: string | null
}>()
</script>

<template>
  <div class="auth-field">
    <label class="auth-field-label" :for="forId">{{ label }}</label>
    <slot
      :invalid="Boolean(error)"
      :described-by="error ? `${forId}-error` : undefined"
    />
    <p v-if="error" :id="`${forId}-error`" class="auth-field-error" role="alert">
      {{ error }}
    </p>
  </div>
</template>

<style scoped>
.auth-field {
  display: grid;
  gap: 8px;
}

.auth-field-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.auth-field-error {
  font-size: 13px;
  color: var(--danger);
}
</style>
