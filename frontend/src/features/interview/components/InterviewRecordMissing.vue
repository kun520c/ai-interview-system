<script setup lang="ts">
import { RouterLink } from 'vue-router'

import EmptyState from '@/components/ui/EmptyState.vue'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <EmptyState :title="title" :description="description">
    <template #action>
      <RouterLink class="missing-action" :to="{ name: 'interview-history' }">返回历史</RouterLink>
    </template>
  </EmptyState>
</template>

<style scoped>
.missing-action {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  color: var(--primary);
  font-weight: 600;
}
</style>
