<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <h1>{{ title }}</h1>
    <p v-if="description" class="u-muted">{{ description }}</p>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  position: relative;
  margin-bottom: 24px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px 24px;
  align-items: start;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -.035em;
}

.page-header p {
  grid-column: 1;
  font-size: 13px;
}

.page-header-actions {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-self: center;
}

@media (max-width: 720px) {
  .page-header {
    grid-template-columns: minmax(0,1fr);
  }

  .page-header h1 {
    font-size: 25px;
  }

  .page-header-actions {
    grid-column: 1;
    grid-row: auto;
  }

}
</style>
