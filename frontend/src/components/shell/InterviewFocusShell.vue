<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView } from 'vue-router'

import ProductMark from '@/components/ui/ProductMark.vue'
import { useCurrentInterview } from '@/features/interview/composables/useCurrentInterview'
import { progressLabel } from '@/features/interview/utils/interviewProgress'

const { session } = useCurrentInterview()

const headerProgress = computed(() => (session.value ? progressLabel(session.value) : null))
const headerDifficulty = computed(() => session.value?.difficulty ?? null)
</script>

<template>
  <div class="interview-focus-shell">
    <a class="skip-link" href="#interview-main">跳到答题区</a>
    <header class="focus-header">
      <div class="focus-brand">
        <ProductMark :size="20" />
        <div>
          <p class="focus-title">面试训练 <span class="focus-studio">/ STUDIO</span></p>
          <p class="focus-meta">面试进行中</p>
        </div>
      </div>
      <p v-if="headerDifficulty && headerProgress" class="focus-session-meta">
        <span class="mono">{{ headerDifficulty }}</span>
        <span>{{ headerProgress }}</span>
      </p>
      <RouterLink class="focus-exit" :to="{ name: 'dashboard' }">退出到工作台</RouterLink>
    </header>
    <h1 class="u-sr-only">面试进行中</h1>
    <main id="interview-main" class="focus-workspace">
      <RouterView />
    </main>
  </div>
</template>
