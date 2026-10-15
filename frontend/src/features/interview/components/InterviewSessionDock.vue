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
  gap: 12px;
  align-items: center;
}

@media (max-width: 720px) {
  .session-dock-actions :deep(.base-button) {
    min-height: 44px;
  }
}
</style>
