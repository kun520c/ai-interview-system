<script setup lang="ts">
import { DASHBOARD_INTERVIEW_FLOW } from '@/features/interview/copy/interviewFlow'
</script>

<template>
  <aside class="interview-flow" aria-labelledby="dashboard-flow-title">
    <h2 id="dashboard-flow-title">面试流程</h2>
    <p class="u-muted">一场连续训练，而不是一次聊天。</p>
    <ol class="flow-list">
      <li v-for="item in DASHBOARD_INTERVIEW_FLOW" :key="item.step">
        <span class="flow-step">{{ item.step }}</span>
        <span>{{ item.label }}</span>
      </li>
    </ol>
  </aside>
</template>

<style scoped>
.interview-flow {
  min-width: 0;
  padding: 20px;
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.interview-flow h2 {
  font-size: 16px;
  font-weight: 650;
}

.interview-flow p {
  margin-top: 8px;
  font-size: 13px;
}

.flow-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.flow-list li {
  display: flex;
  align-items: baseline;
  gap: 12px;
  font-size: 14px;
  color: var(--text);
}

.flow-step {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--text-2);
  min-width: 1.6em;
}
</style>
