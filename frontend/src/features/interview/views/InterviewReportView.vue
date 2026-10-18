<script setup lang="ts">
import { computed } from 'vue'
import { Check, TriangleAlert, ListChecks } from '@lucide/vue'
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
          <div class="report-summary">
            <section class="report-outcome" aria-labelledby="report-outcome-title">
              <h2 id="report-outcome-title" class="u-sr-only">{{ REPORT_COPY.outcome }}</h2>
              <p class="report-score-caption">本场综合表现</p>
              <p class="report-score">
                <span class="score-number">{{ formatScore(report.overallScore) }}</span>
                <span class="score-maximum">/ 100</span>
                <span class="score-label">{{ REPORT_COPY.overallScore }}</span>
              </p>
              <p class="report-result" :class="resultClass">
                {{ displayReportResult(report.result) }}
              </p>
            </section>

            <section class="report-block report-meaning" aria-labelledby="report-meaning-title">
              <h2 id="report-meaning-title">{{ REPORT_COPY.meaning }}</h2>
              <p>{{ report.summary }}</p>
            </section>
          </div>

          <div class="report-pair">
            <section class="report-block report-strength" aria-labelledby="report-strength-title">
              <h2 id="report-strength-title"><Check :size="18" aria-hidden="true" />{{ REPORT_COPY.strength }}</h2>
              <ol v-if="report.strengths?.length">
                <li v-for="(item, index) in report.strengths" :key="`s-${index}`">{{ item }}</li>
              </ol>
            </section>
            <section class="report-block report-gap" aria-labelledby="report-gap-title">
              <h2 id="report-gap-title"><TriangleAlert :size="18" aria-hidden="true" />{{ REPORT_COPY.gap }}</h2>
              <ol v-if="report.weaknesses?.length">
                <li v-for="(item, index) in report.weaknesses" :key="`w-${index}`">{{ item }}</li>
              </ol>
            </section>
          </div>

          <section class="report-block report-actions" aria-labelledby="report-action-title">
            <h2 id="report-action-title"><ListChecks :size="18" aria-hidden="true" />{{ REPORT_COPY.action }}</h2>
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

.report-page :deep(.page-header) {
  margin-bottom: 0;
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
  gap: 8px 16px;
}

.report-link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-size: 12px;
  font-weight: 600;
  color: var(--primary);
}

.report-state {
  display: grid;
  gap: 16px;
  justify-items: start;
  padding: 32px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.report-state h2 {
  font-size: 21px;
  font-weight: 650;
}

.report-state p {
  font-size: 14px;
}

.report-summary {
  display: grid;
  grid-template-columns: minmax(0,.8fr) minmax(0,2fr);
  border: 1px solid var(--primary-border);
  border-radius: var(--radius-lg);
  background: var(--training-wash);
  box-shadow: var(--shadow-panel);
}

.report-outcome {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 28px;
  border-right: 1px solid var(--border);
}

.report-score-caption {
  font-size: 12px;
  color: var(--text-2);
}

.score-number {
  font-family: var(--mono);
  font-size: 56px;
  font-weight: 500;
  line-height: 1.1;
  letter-spacing: -.06em;
}

.score-maximum {
  margin-left: 8px;
  font: 12px var(--mono);
  color: var(--text-2);
}

.score-label {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-2);
}

.report-result {
  font-size: 12px;
  font-weight: 650;
  border: 1px solid currentColor;
  padding: 3px 12px;
  border-radius: 999px;
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

.report-block {
  padding: 26px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  min-width: 0;
  box-shadow: var(--shadow-sm);
}

.report-meaning {
  align-self: center;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.report-block h2, .report-evidence > h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 650;
}

.report-block p, .report-block li {
  font-size: 14px;
  line-height: 1.85;
  overflow-wrap: anywhere;
}

.report-block li + li {
  margin-top: 10px;
}

.report-strength h2 {
  color: var(--success);
}

.report-gap h2 {
  color: var(--warning);
}

.report-strength li, .report-gap li {
  position: relative;
  padding-left: 16px;
}

.report-strength li::before, .report-gap li::before {
  content: '';
  position: absolute;
  width: 4px;
  height: 4px;
  top: .8em;
  left: 0;
  border-radius: 50%;
  background: var(--border-strong);
}

.report-pair {
  display: grid;
  grid-template-columns: repeat(2,minmax(0,1fr));
  gap: 24px;
}

.report-actions h2 {
  color: var(--primary);
}

.suggestion-list {
  counter-reset: suggestion;
  list-style: none;
  padding: 0;
}

.suggestion-list li {
  position: relative;
  padding: 10px 0 10px 42px;
}

.suggestion-list li + li {
  border-top: 1px solid var(--border);
  margin-top: 0;
}

.suggestion-list li::before {
  counter-increment: suggestion;
  content: counter(suggestion, decimal-leading-zero);
  position: absolute;
  left: 0;
  top: 12px;
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 7px;
  font-family: var(--mono);
  font-size: 11px;
  color: var(--primary);
  background: var(--primary-soft);
}

.report-evidence {
  min-width: 0;
  padding-top: 8px;
}

.notice-copy {
  margin: 8px 0 12px;
}

@media (max-width: 720px) {
  .report-summary, .report-pair {
    grid-template-columns: minmax(0,1fr);
  }

  .report-outcome {
    border-right: 0;
    border-bottom: 1px solid var(--border);
    padding: 22px;
  }

  .report-block {
    padding: 22px;
  }

  .score-number {
    font-size: 48px;
  }

  .report-pair {
    gap: 16px;
  }

}
</style>
