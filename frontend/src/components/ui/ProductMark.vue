<script setup lang="ts">
defineProps<{
  size?: number
  decorative?: boolean
}>()
</script>

<template>
  <svg
    class="product-mark"
    xmlns="http://www.w3.org/2000/svg"
    :width="size ?? 22"
    :height="size ?? 22"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    :aria-hidden="decorative !== false ? 'true' : undefined"
    :role="decorative === false ? 'img' : undefined"
    :aria-label="decorative === false ? '面试训练' : undefined"
  >
    <rect x="2.5" y="5.5" width="7.5" height="13" rx="1.8" />
    <path d="M12 12 H15.2" />
    <path d="M13.6 10.2 L15.8 12 L13.6 13.8" />
    <rect x="16.5" y="5.5" width="5" height="13" rx="1.8" />
  </svg>
</template>

<style scoped>
.product-mark {
  color: var(--primary);
  flex-shrink: 0;
}
</style>
