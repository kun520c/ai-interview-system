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
          <p class="pane-kicker">{{ questionLabel ?? '当前题目' }}</p>
          <slot name="question" />
        </section>
        <section class="session-pane session-pane-answer">
          <p class="pane-kicker">{{ answerLabel ?? '作答 / 反馈' }}</p>
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
  font-size: 12px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 12px;
}

.session-answer-body {
  flex: 1;
  min-height: 0;
}
</style>
