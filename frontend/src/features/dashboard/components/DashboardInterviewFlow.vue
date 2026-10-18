<script setup lang="ts">
import { DASHBOARD_INTERVIEW_FLOW } from '@/features/interview/copy/interviewFlow'
</script>

<template>
  <aside class="interview-flow" aria-labelledby="dashboard-flow-title">
    <h2 id="dashboard-flow-title">面试流程</h2>
    <p class="u-muted">让每一次训练形成完整闭环。</p>
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
  padding: 26px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.interview-flow h2 {
  font-size: 15px;
  font-weight: 650;
}

.interview-flow p {
  margin-top: 6px;
  font-size: 12px;
}

.flow-list {
  display: grid;
  gap: 0;
  margin-top: 22px;
}

.flow-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  font-size: 13px;
  color: var(--text);
}

.flow-step {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: 1px solid var(--border);
  border-radius: 8px;
  font-family: var(--mono);
  font-size: 11px;
  color: var(--primary);
  background: var(--surface-subtle);
}

@media (min-width: 721px) and (max-width: 1100px) {
  .flow-list {
    grid-template-columns: repeat(3,minmax(0,1fr));
    margin-top: 12px;
    gap: 12px;
  }

}
</style>
