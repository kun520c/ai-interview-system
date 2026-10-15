<script setup lang="ts">
defineProps<{
  id?: string
  modelValue: string
  type?: string
  name?: string
  autocomplete?: string
  placeholder?: string
  disabled?: boolean
  maxlength?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <input
    class="base-input"
    :id="id"
    :name="name"
    :value="modelValue"
    :type="type ?? 'text'"
    :autocomplete="autocomplete"
    :placeholder="placeholder"
    :disabled="disabled"
    :maxlength="maxlength"
    @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
  />
</template>

<style scoped>
.base-input {
  width: 100%;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
}

.base-input::placeholder {
  color: var(--placeholder);
}

.base-input:focus {
  border-color: var(--primary);
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-soft);
}

.base-input:disabled {
  background: var(--surface-subtle);
  cursor: not-allowed;
}

.base-input[aria-invalid='true'] {
  border-color: var(--danger);
}
</style>
