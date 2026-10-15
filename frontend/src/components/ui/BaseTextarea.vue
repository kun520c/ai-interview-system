<script setup lang="ts">
defineProps<{
  id?: string
  modelValue: string
  placeholder?: string
  disabled?: boolean
  readonly?: boolean
  rows?: number
  invalid?: boolean
  describedBy?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <textarea
    class="base-textarea"
    :id="id"
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :readonly="readonly"
    :rows="rows ?? 8"
    :aria-invalid="invalid ? 'true' : undefined"
    :aria-describedby="describedBy"
    @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
  ></textarea>
</template>

<style scoped>
.base-textarea {
  width: 100%;
  min-height: 180px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text);
  resize: vertical;
}

.base-textarea::placeholder {
  color: var(--placeholder);
}

.base-textarea:focus {
  border-color: var(--primary);
  outline: none;
  box-shadow: 0 0 0 3px var(--primary-soft);
}

.base-textarea:disabled,
.base-textarea:read-only {
  background: var(--surface-subtle);
}
</style>
