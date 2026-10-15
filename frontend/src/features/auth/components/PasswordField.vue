<script setup lang="ts">
import { Eye, EyeOff } from '@lucide/vue'
import { computed, ref } from 'vue'

import BaseInput from '@/components/ui/BaseInput.vue'

const props = defineProps<{
  id: string
  modelValue: string
  autocomplete?: string
  disabled?: boolean
  invalid?: boolean
  describedBy?: string
  maxlength?: number
  name?: string
  visibilityNoun?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const visible = ref(false)
const inputType = computed(() => (visible.value ? 'text' : 'password'))
const toggleLabel = computed(() => {
  const noun = props.visibilityNoun ?? '密码'
  return visible.value ? `隐藏${noun}` : `显示${noun}`
})

function toggleVisibility(): void {
  visible.value = !visible.value
}
</script>

<template>
  <div class="password-field">
    <BaseInput
      :id="id"
      :model-value="modelValue"
      :type="inputType"
      :name="name ?? 'password'"
      :autocomplete="autocomplete"
      :disabled="disabled"
      :maxlength="maxlength"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-describedby="describedBy"
      @update:model-value="emit('update:modelValue', $event)"
    />
    <button
      class="password-toggle"
      type="button"
      :aria-label="toggleLabel"
      :aria-pressed="visible"
      :disabled="disabled"
      @click="toggleVisibility"
    >
      <EyeOff v-if="visible" :size="18" :stroke-width="1.7" aria-hidden="true" />
      <Eye v-else :size="18" :stroke-width="1.7" aria-hidden="true" />
    </button>
  </div>
</template>

<style scoped>
.password-field {
  position: relative;
}

.password-field :deep(.base-input) {
  padding-right: 48px;
}

.password-toggle {
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 100%;
  min-height: 40px;
  color: var(--text-2);
}

.password-toggle:hover:not(:disabled) {
  color: var(--text);
}

.password-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
