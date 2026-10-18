<script setup lang="ts">
defineProps<{
  questionLabel?: string
  answerLabel?: string
}>()
</script>

<template>
  <section class="session-workspace">
    <div class="session-scroll">
      <slot name="header" />
      <div class="session-split">
        <section class="session-pane session-pane-question">
          <p class="pane-kicker"><span class="pane-step">01</span>{{ questionLabel ?? '当前题目' }}</p>
          <slot name="question" />
        </section>
        <section class="session-pane session-pane-answer">
          <p class="pane-kicker"><span class="pane-step">02</span>{{ answerLabel ?? '作答 / 反馈' }}</p>
          <div class="session-answer-body">
            <slot name="answer" />
          </div>
        </section>
      </div>
    </div>
    <div class="session-dock">
      <slot name="dock" />
    </div>
  </section>
</template>

<style scoped>
.pane-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  font-weight: 650;
  color: var(--text-2);
  padding-bottom: 18px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border);
}

.pane-step {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  font: 11px var(--mono);
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: var(--radius-sm);
}

.session-answer-body {
  flex: 1;
  min-height: 0;
}
</style>
