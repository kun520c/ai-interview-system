<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import BaseButton from '@/components/ui/BaseButton.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusNotice from '@/components/ui/StatusNotice.vue'
import InterviewRecordMissing from '@/features/interview/components/InterviewRecordMissing.vue'
import InterviewTimeline from '@/features/interview/components/InterviewTimeline.vue'
import { useInterviewReport } from '@/features/interview/composables/useInterviewReport'
import { displayReportResult } from '@/features/interview/copy/evaluationLevelDisplay'
import { REPORT_COPY } from '@/features/interview/copy/reportCopy'
import { ReportPageState } from '@/features/interview/types/reportPageState'
import { formatInterviewDate } from '@/utils/formatInterviewDate'
import { formatScore } from '@/utils/formatScore'
import { EvaluationLevel } from '@/types/enums'

const {
  pageState,
  detail,
  report,
  timeline,
  statusMessage,
  readyAbnormal,
  canGenerate,
  generateBusy,
  bootstrap,
  generate,
  refreshReport,
} = useInterviewReport()

const headerDescription = computed(() => {
  if (!detail.value) {
    return undefined
  }
  const parts: string[] = [detail.value.difficulty]
  if (detail.value.endedAt) {
    parts.push(formatInterviewDate(detail.value.endedAt))
  }
  return parts.join(' · ')
})

const resultClass = computed(() => {
  const result = report.value?.result
  if (result === EvaluationLevel.EXCELLENT || result === EvaluationLevel.GOOD) {
    return 'is-success'
  }
  if (result === EvaluationLevel.FAIR) {
    return 'is-warning'
  }
  if (result === EvaluationLevel.WEAK) {
    return 'is-danger'
  }
  return ''
})

const showWaiting = computed(() => {
  return (
    pageState.value === ReportPageState.GUARDING_SESSION ||
    pageState.value === ReportPageState.LOADING_REPORT ||
    pageState.value === ReportPageState.REDIRECTING
  )
})

const waitingLabel = computed(() => {
  if (pageState.value === ReportPageState.LOADING_REPORT) {
    return REPORT_COPY.loadingReport
  }
  return REPORT_COPY.loadingSession
})

const generating = computed(() => {
  return (
    pageState.value === ReportPageState.GENERATING ||
    pageState.value === ReportPageState.GENERATING_POST
  )
})
</script>

<template>
  <section class="report-page">
    <template
      v-if="
        pageState === ReportPageState.INVALID_ID ||
        pageState === ReportPageState.NOT_FOUND ||
        pageState === ReportPageState.UNAVAILABLE
      "
    >
      <InterviewRecordMissing :title="REPORT_COPY.missingRecord" />
    </template>

    <template v-else-if="showWaiting">
      <div class="report-skeleton" aria-busy="true" :aria-label="waitingLabel">
        <span class="skeleton-block is-title"></span>
        <span class="skeleton-block"></span>
        <span class="skeleton-block is-short"></span>
      </div>
    </template>

    <StatusNotice v-else-if="pageState === ReportPageState.ERROR" tone="danger" role="alert">
      <p>{{ REPORT_COPY.loadError }}</p>
      <BaseButton variant="ghost" @click="bootstrap()">{{ REPORT_COPY.retry }}</BaseButton>
    </StatusNotice>

    <template v-else>
      <PageHeader :title="REPORT_COPY.title" :description="headerDescription">
        <template #actions>
          <div class="report-nav">
            <RouterLink class="report-link" :to="{ name: 'interview-history' }">
              {{ REPORT_COPY.backToHistory }}
            </RouterLink>
            <RouterLink
              v-if="detail"
              class="report-link"
              :to="{ name: 'interview-detail', params: { sessionId: String(detail.sessionId) } }"
            >
              {{ REPORT_COPY.openDetail }}
            </RouterLink>
          </div>
        </template>
      </PageHeader>

      <StatusNotice v-if="statusMessage" tone="warning" role="alert">
        <p>{{ statusMessage }}</p>
        <BaseButton
          v-if="statusMessage === REPORT_COPY.pollNetworkHint"
          variant="ghost"
          @click="refreshReport()"
        >
          {{ REPORT_COPY.refreshStatus }}
        </BaseButton>
      </StatusNotice>

      <section
        v-if="pageState === ReportPageState.NOT_STARTED"
        class="report-state"
        aria-labelledby="report-not-started-title"
      >
        <h2 id="report-not-started-title">{{ REPORT_COPY.notStartedTitle }}</h2>
        <p class="u-muted">{{ REPORT_COPY.notStartedHint }}</p>
        <BaseButton :disabled="!canGenerate" @click="generate()">
          {{ REPORT_COPY.generate }}
        </BaseButton>
      </section>

      <section
        v-else-if="pageState === ReportPageState.FAILED"
        class="report-state"
        aria-labelledby="report-failed-title"
      >
        <h2 id="report-failed-title">{{ REPORT_COPY.failedTitle }}</h2>
        <p class="u-muted">{{ REPORT_COPY.failedHint }}</p>
        <BaseButton :disabled="!canGenerate" @click="generate()">
          {{ REPORT_COPY.retryGenerate }}
        </BaseButton>
      </section>

      <section
        v-else-if="pageState === ReportPageState.RECOVERY_UNKNOWN"
        class="report-state"
        role="alert"
        aria-labelledby="report-recovery-title"
      >
        <h2 id="report-recovery-title">{{ REPORT_COPY.recoveryUnknownTitle }}</h2>
        <p class="u-muted">{{ REPORT_COPY.recoveryUnknownHint }}</p>
        <BaseButton @click="refreshReport()">{{ REPORT_COPY.refreshStatus }}</BaseButton>
      </section>

      <section
        v-else-if="generating"
        class="report-state"
        role="status"
        aria-live="polite"
        :aria-busy="generateBusy ? 'true' : undefined"
      >
        <h2>{{ generateBusy ? REPORT_COPY.generatingBusy : REPORT_COPY.generatingTitle }}</h2>
        <p class="u-muted">{{ REPORT_COPY.generatingHint }}</p>
      </section>

      <template v-else-if="pageState === ReportPageState.READY && report">
        <StatusNotice v-if="readyAbnormal" tone="warning" role="alert">
          <p>{{ REPORT_COPY.abnormalTitle }}</p>
          <p class="notice-copy">{{ REPORT_COPY.abnormalHint }}</p>
          <BaseButton variant="ghost" @click="refreshReport()">{{ REPORT_COPY.refreshStatus }}</BaseButton>
        </StatusNotice>

        <template v-else>
          <section class="report-outcome" aria-labelledby="report-outcome-title">
            <h2 id="report-outcome-title" class="u-sr-only">{{ REPORT_COPY.outcome }}</h2>
            <p class="report-score">
              <span class="score-number">{{ formatScore(report.overallScore) }}</span>
              <span class="score-label">{{ REPORT_COPY.overallScore }}</span>
            </p>
            <p class="report-result" :class="resultClass">
              {{ displayReportResult(report.result) }}
            </p>
          </section>

          <section class="report-block" aria-labelledby="report-meaning-title">
            <h2 id="report-meaning-title">{{ REPORT_COPY.meaning }}</h2>
            <p>{{ report.summary }}</p>
          </section>

          <div class="report-pair">
            <section class="report-block" aria-labelledby="report-strength-title">
              <h2 id="report-strength-title">{{ REPORT_COPY.strength }}</h2>
              <ol v-if="report.strengths?.length">
                <li v-for="(item, index) in report.strengths" :key="`s-${index}`">{{ item }}</li>
              </ol>
            </section>
            <section class="report-block" aria-labelledby="report-gap-title">
              <h2 id="report-gap-title">{{ REPORT_COPY.gap }}</h2>
              <ol v-if="report.weaknesses?.length">
                <li v-for="(item, index) in report.weaknesses" :key="`w-${index}`">{{ item }}</li>
              </ol>
            </section>
          </div>

          <section class="report-block" aria-labelledby="report-action-title">
            <h2 id="report-action-title">{{ REPORT_COPY.action }}</h2>
            <ol v-if="report.suggestions?.length" class="suggestion-list">
              <li v-for="(item, index) in report.suggestions" :key="`a-${index}`">{{ item }}</li>
            </ol>
          </section>

          <section class="report-evidence" aria-labelledby="report-evidence-title">
            <h2 id="report-evidence-title">{{ REPORT_COPY.evidence }}</h2>
            <InterviewTimeline :items="timeline" collapsible />
          </section>
        </template>
      </template>
    </template>
  </section>
</template>

<style scoped>
.report-page {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 24px;
}

.report-skeleton {
  display: grid;
  gap: 12px;
}

.skeleton-block {
  display: block;
  height: 12px;
  border-radius: var(--radius);
  background: var(--surface-subtle);
}

.skeleton-block.is-title {
  height: 24px;
  width: 36%;
}

.skeleton-block.is-short {
  width: 58%;
}

.report-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
}

.report-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-weight: 600;
  color: var(--primary);
}

.report-state {
  display: grid;
  gap: 12px;
  justify-items: start;
}

.report-state :deep(.base-button) {
  min-height: 44px;
}

.report-state h2 {
  font-size: 20px;
  font-weight: 650;
}

.report-outcome {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px 20px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.score-number {
  font-family: var(--mono);
  font-size: 36px;
  font-weight: 500;
  line-height: 1;
}

.score-label {
  margin-left: 8px;
  font-size: 13px;
  color: var(--text-2);
}

.report-result {
  font-size: 18px;
  font-weight: 650;
}

.report-result.is-success {
  color: var(--success);
}

.report-result.is-warning {
  color: var(--warning);
}

.report-result.is-danger {
  color: var(--danger);
}

.report-block h2,
.report-evidence h2 {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 650;
}

.report-block p,
.report-block li {
  font-size: 16px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.report-block li + li,
.suggestion-list li + li {
  margin-top: 8px;
}

.report-pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 32px;
}

.suggestion-list {
  counter-reset: suggestion;
  list-style: none;
  padding: 0;
}

.suggestion-list li {
  position: relative;
  padding-left: 28px;
}

.suggestion-list li::before {
  counter-increment: suggestion;
  content: counter(suggestion);
  position: absolute;
  left: 0;
  top: 0;
  font-family: var(--mono);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
}

.notice-copy {
  margin: 8px 0 12px;
}

@media (max-width: 1024px) {
  .report-pair {
    grid-template-columns: minmax(0, 1fr);
    gap: 24px;
  }
}

@media (max-width: 720px) {
  .score-number {
    font-size: 32px;
  }
}
</style>
