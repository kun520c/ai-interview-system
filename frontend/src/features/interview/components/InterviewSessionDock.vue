<script setup lang="ts">
import BaseButton from '@/components/ui/BaseButton.vue'
import { continueLabel, SESSION_COPY } from '@/features/interview/copy/sessionCopy'
import { SessionPhase } from '@/features/interview/types/sessionPhase'
import type { DecisionAction } from '@/types/enums'

defineProps<{
  phase: SessionPhase
  canSubmit: boolean
  submitLocked: boolean
  checkingCurrent: boolean
  nextAction?: DecisionAction | null
}>()

const emit = defineEmits<{
  submit: []
  retry: []
  check: []
  advance: []
  refresh: []
}>()
</script>

<template>
  <div class="session-dock-actions">
    <p class="dock-context">{{ phase === SessionPhase.EVALUATION_GATE ? '阅读本题反馈后，确认继续。' : '专注当前问题，清晰表达你的思路。' }}</p>
    <template v-if="phase === SessionPhase.ANSWERING">
      <BaseButton :disabled="!canSubmit || submitLocked" :loading="submitLocked" @click="emit('submit')">
        {{ SESSION_COPY.submit }}
      </BaseButton>
    </template>

    <template v-else-if="phase === SessionPhase.SUBMITTING">
      <BaseButton disabled loading>{{ SESSION_COPY.submittingStatus }}</BaseButton>
    </template>

    <template v-else-if="phase === SessionPhase.EVALUATION_PENDING">
      <BaseButton
        :disabled="submitLocked || checkingCurrent"
        :loading="submitLocked"
        @click="emit('retry')"
      >
        {{ SESSION_COPY.pendingReplay }}
      </BaseButton>
      <BaseButton variant="ghost" :disabled="checkingCurrent || submitLocked" @click="emit('check')">
        {{ SESSION_COPY.checkCurrent }}
      </BaseButton>
    </template>

    <template v-else-if="phase === SessionPhase.EVALUATION_GATE && nextAction">
      <BaseButton @click="emit('advance')">{{ continueLabel(nextAction) }}</BaseButton>
    </template>

    <template v-else-if="phase === SessionPhase.RECOVERY_BLOCKED">
      <BaseButton disabled>{{ SESSION_COPY.submit }}</BaseButton>
      <BaseButton variant="ghost" :disabled="checkingCurrent" @click="emit('refresh')">
        {{ SESSION_COPY.recoveryBlockedRefresh }}
      </BaseButton>
    </template>
  </div>
</template>

<style scoped>
.session-dock-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  align-items: center;
}

.dock-context {
  margin-right: auto;
  font-size: 12px;
  color: var(--text-2);
}

@media (max-width: 720px) {
  .dock-context {
    display: none;
  }

  .session-dock-actions {
    justify-content: stretch;
    gap: 8px;
  }

  .session-dock-actions :deep(.base-button) {
    flex: 1;
    min-height: 44px;
  }

}
</style>
