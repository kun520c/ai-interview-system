import { nextTick, computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ErrorKind, isApiError } from '@/api/apiError'
import { getSessionGeneration, isCurrentIdentityScope } from '@/app/session/sessionScope'
import { useAuthStore } from '@/app/stores/auth'
import { submitInterviewAnswer } from '@/features/interview/api/interviewApi'
import { useCurrentInterview } from '@/features/interview/composables/useCurrentInterview'
import { SESSION_COPY } from '@/features/interview/copy/sessionCopy'
import {
  consumeInterviewResumeNotice,
} from '@/features/interview/navigation/sessionNavigationContext'
import {
  clearEvaluationGate,
  readEvaluationGate,
  readLatestEvaluationGate,
  writeEvaluationGate,
} from '@/features/interview/storage/evaluationGateStorage'
import {
  deleteSubmitRecord,
  markSubmitRecordDispatched,
  markSubmitRecordRecoveryBlocked,
  persistSubmitRecord,
  readSubmitRecord,
  SubmitRecordPersistError,
  type SubmitRecordKeyParts,
} from '@/features/interview/storage/submitRecordStorage'
import type { EvaluationGate } from '@/features/interview/types/evaluationGate'
import { SessionPhase } from '@/features/interview/types/sessionPhase'
import {
  isRecoveryBlockedSubmitRecord,
  type RecoveryBlockedReason,
  type SubmitRecord,
} from '@/features/interview/types/submitRecord'
import { validateAnswerContent, AnswerValidationIssue } from '@/features/interview/utils/answerValidation'
import { resolveSessionBootstrap } from '@/features/interview/utils/sessionBootstrap'
import {
  AnswerConflictKind,
  classifyAnswerConflict,
  isEvaluationServiceUnavailable,
} from '@/features/interview/utils/submissionRecovery'
import { createRequestId } from '@/utils/requestId'
import { DecisionAction, InterviewSessionStatus } from '@/types/enums'
import type { InterviewQuestionResponse, SubmitInterviewAnswerResponse } from '@/types/interview'

interface OperationScope {
  userId: number
  generation: number
  viewGeneration: number
}

export function useInterviewSession() {
  const router = useRouter()
  const auth = useAuthStore()
  const current = useCurrentInterview()

  const phase = ref<SessionPhase>(SessionPhase.BOOTSTRAP)
  const draft = ref('')
  const inlineError = ref<string | null>(null)
  const banner = ref<string | null>(null)
  const alertMessage = ref<string | null>(null)
  const resumeNotice = ref(false)
  const gate = ref<EvaluationGate | null>(null)
  const activeRecord = ref<SubmitRecord | null>(null)
  const submitLocked = ref(false)
  const checkingCurrent = ref(false)
  const finishedSessionId = ref<number | null>(null)
  let mounted = true
  let viewGeneration = 0

  const workspaceQuestion = computed<InterviewQuestionResponse | null>(() => {
    if (gate.value) {
      return gate.value.answeredQuestion
    }
    return current.session.value?.currentQuestion ?? null
  })

  const submittedAnswer = computed(() => {
    if (gate.value) {
      return gate.value.answerContent
    }
    if (activeRecord.value?.dispatchedAt) {
      return activeRecord.value.answerContent
    }
    return null
  })

  const answerLocked = computed(() => {
    return (
      phase.value === SessionPhase.SUBMITTING ||
      phase.value === SessionPhase.EVALUATION_PENDING ||
      phase.value === SessionPhase.RECOVERY_BLOCKED ||
      phase.value === SessionPhase.EVALUATION_GATE ||
      Boolean(activeRecord.value?.dispatchedAt)
    )
  })

  const canSubmit = computed(() => {
    const session = current.session.value
    return (
      phase.value === SessionPhase.ANSWERING &&
      !submitLocked.value &&
      !answerLocked.value &&
      Boolean(session?.currentQuestion) &&
      session?.status === InterviewSessionStatus.IN_PROGRESS &&
      validateAnswerContent(draft.value) === null
    )
  })

  function captureScope(): OperationScope | null {
    const userId = auth.userId
    if (userId === null) {
      return null
    }
    return {
      userId,
      generation: getSessionGeneration(),
      viewGeneration,
    }
  }

  function isLiveScope(scope: OperationScope | null | undefined): scope is OperationScope {
    return (
      mounted &&
      scope != null &&
      scope.viewGeneration === viewGeneration &&
      isCurrentIdentityScope(scope.userId, scope.generation, auth.userId)
    )
  }

  function matchingCurrentQuestion(record: SubmitRecord): InterviewQuestionResponse | null {
    const session = current.session.value
    const question = session?.currentQuestion
    if (
      !session ||
      !question ||
      session.sessionId !== record.sessionId ||
      question.interviewQuestionId !== record.interviewQuestionId
    ) {
      return null
    }
    return { ...question }
  }

  function currentKeyParts(): SubmitRecordKeyParts | null {
    const userId = auth.userId
    const session = current.session.value
    const question = session?.currentQuestion
    if (userId === null || !session || !question) {
      return null
    }
    return {
      userId,
      sessionId: session.sessionId,
      interviewQuestionId: question.interviewQuestionId,
    }
  }

  function recordForBootstrap(keyParts: SubmitRecordKeyParts | null): SubmitRecord | null {
    const stored = keyParts ? readSubmitRecord(keyParts) : null
    const memory = activeRecord.value
    if (
      memory &&
      isRecoveryBlockedSubmitRecord(memory) &&
      keyParts &&
      memory.userId === keyParts.userId &&
      memory.sessionId === keyParts.sessionId &&
      memory.interviewQuestionId === keyParts.interviewQuestionId
    ) {
      return memory
    }
    return stored
  }

  function persistBlockedMarker(record: SubmitRecord, reason: RecoveryBlockedReason): SubmitRecord {
    const blockedAt = new Date().toISOString()
    const next: SubmitRecord = {
      ...record,
      recoveryBlockedReason: reason,
      recoveryBlockedAt: blockedAt,
      updatedAt: blockedAt,
    }
    try {
      return markSubmitRecordRecoveryBlocked(record, reason, blockedAt)
    } catch {
      return next
    }
  }

  function applyRecoveryBlocked(record: SubmitRecord, notice: string): void {
    activeRecord.value = record
    draft.value = record.answerContent
    phase.value = SessionPhase.RECOVERY_BLOCKED
    alertMessage.value = notice
  }

  function setAnswering(nextDraft: string, record: SubmitRecord | null): void {
    phase.value = SessionPhase.ANSWERING
    draft.value = nextDraft
    activeRecord.value = record
    if (record?.dispatchedAt) {
      draft.value = record.answerContent
    }
  }

  async function applyBootstrapResult(
    sessionIdForGate: number | null,
    scope: OperationScope | null,
  ): Promise<void> {
    if (!isLiveScope(scope)) {
      return
    }
    const userId = scope.userId
    const storedGate =
      sessionIdForGate !== null
        ? readEvaluationGate(sessionIdForGate, userId)
        : readLatestEvaluationGate(userId)
    const keyParts = currentKeyParts()
    const record = recordForBootstrap(keyParts)
    const result = resolveSessionBootstrap({
      session: current.session.value,
      gate: storedGate,
      submitRecord: record,
    })

    banner.value = null
    alertMessage.value = null
    inlineError.value = null
    gate.value = null
    finishedSessionId.value = current.session.value?.sessionId ?? storedGate?.sessionId ?? null

    if (result.kind === 'redirect-dashboard') {
      if (storedGate && sessionIdForGate !== null) {
        clearEvaluationGate(storedGate.sessionId, userId)
      }
      if (!isLiveScope(scope)) {
        return
      }
      await router.replace({ name: 'dashboard' })
      return
    }

    if (!isLiveScope(scope)) {
      return
    }

    if (result.kind === 'finish-gate') {
      gate.value = result.gate
      draft.value = result.gate.answerContent
      phase.value = SessionPhase.EVALUATION_GATE
      finishedSessionId.value = result.gate.sessionId
      return
    }

    if (result.kind === 'recovering-question') {
      if (storedGate) {
        clearEvaluationGate(storedGate.sessionId, userId)
      }
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.recoveringQuestion
      return
    }

    if (result.kind === 'evaluation-gate') {
      gate.value = result.gate
      draft.value = result.gate.answerContent
      activeRecord.value = record
      phase.value = SessionPhase.EVALUATION_GATE
      return
    }

    if (result.kind === 'stale-gate') {
      if (storedGate) {
        clearEvaluationGate(storedGate.sessionId, userId)
      }
      banner.value = SESSION_COPY.staleEvaluationNotice
      const currentQuestionId = current.session.value?.currentQuestion?.interviewQuestionId
      const sameQuestion = Boolean(record && record.interviewQuestionId === currentQuestionId)
      if (record && sameQuestion && isRecoveryBlockedSubmitRecord(record)) {
        applyRecoveryBlocked(record, SESSION_COPY.recoveryBlockedTitle)
        return
      }
      if (record && sameQuestion && record.dispatchedAt) {
        draft.value = record.answerContent
        activeRecord.value = record
        phase.value = SessionPhase.EVALUATION_PENDING
        return
      }
      if (record && sameQuestion && !record.dispatchedAt) {
        setAnswering(record.answerContent, record)
        return
      }
      setAnswering('', null)
      return
    }

    if (result.kind === 'recovery-blocked') {
      applyRecoveryBlocked(result.record, SESSION_COPY.recoveryBlockedTitle)
      return
    }

    if (result.kind === 'pending') {
      activeRecord.value = result.record
      draft.value = result.record.answerContent
      phase.value = SessionPhase.EVALUATION_PENDING
      return
    }

    setAnswering(result.draft, result.record)
  }

  async function bootstrap(): Promise<void> {
    const scope = captureScope()
    phase.value = SessionPhase.BOOTSTRAP
    resumeNotice.value = consumeInterviewResumeNotice(scope?.userId ?? auth.userId)
    gate.value = null
    activeRecord.value = null
    draft.value = ''
    try {
      const session = await current.refresh()
      if (!isLiveScope(scope)) {
        return
      }
      if (session === null && current.session.value !== null) {
        await applyBootstrapResult(current.session.value.sessionId, scope)
        return
      }
      await applyBootstrapResult(session?.sessionId ?? null, scope)
    } catch (caught) {
      if (!isLiveScope(scope)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        return
      }
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.clientStateError
    }
  }

  function setDraft(value: string): void {
    if (answerLocked.value) {
      return
    }
    draft.value = value
    inlineError.value = null
    const record = activeRecord.value
    if (record && !record.dispatchedAt && value !== record.answerContent) {
      deleteSubmitRecord(record)
      activeRecord.value = null
    }
  }

  function validationMessage(issue: (typeof AnswerValidationIssue)[keyof typeof AnswerValidationIssue]): string {
    return issue === AnswerValidationIssue.BLANK ? SESSION_COPY.blankAnswer : SESSION_COPY.oversizedAnswer
  }

  async function persistThenDispatch(record: SubmitRecord, parts: SubmitRecordKeyParts): Promise<SubmitRecord> {
    persistSubmitRecord(record)
    const dispatchedAt = new Date().toISOString()
    return markSubmitRecordDispatched(parts, dispatchedAt)
  }

  function buildEvaluationGate(
    record: SubmitRecord,
    response: SubmitInterviewAnswerResponse,
    answeredQuestion: InterviewQuestionResponse,
  ): EvaluationGate | null {
    if (answeredQuestion.interviewQuestionId !== record.interviewQuestionId) {
      return null
    }
    return {
      userId: record.userId,
      sessionId: record.sessionId,
      createdAt: new Date().toISOString(),
      answeredQuestion,
      answerContent: record.answerContent,
      evaluation: response.evaluation,
      nextAction: response.nextAction,
      nextQuestion: response.nextQuestion,
      sessionStatus: response.sessionStatus,
    }
  }

  function persistOwnedGate(nextGate: EvaluationGate): void {
    writeEvaluationGate(nextGate.sessionId, nextGate)
  }

  function openEvaluationGate(nextGate: EvaluationGate): void {
    gate.value = nextGate
    persistOwnedGate(nextGate)
    phase.value = SessionPhase.EVALUATION_GATE
    submitLocked.value = false
    void nextTick(() => {
      document.getElementById('evaluation-feedback-heading')?.focus()
    })
  }

  function visibleQuestionMatchesRecord(record: SubmitRecord): boolean {
    const session = current.session.value
    return (
      session?.sessionId === record.sessionId &&
      session.currentQuestion?.interviewQuestionId === record.interviewQuestionId
    )
  }

  async function postAnswer(
    record: SubmitRecord,
    answeredQuestionSnapshot: InterviewQuestionResponse,
    scope: OperationScope,
  ): Promise<void> {
    const response = await submitInterviewAnswer(record.sessionId, {
      interviewQuestionId: record.interviewQuestionId,
      answerContent: record.answerContent,
      requestId: record.requestId,
    })
    const nextGate = buildEvaluationGate(record, response, answeredQuestionSnapshot)
    if (!nextGate) {
      if (isLiveScope(scope)) {
        phase.value = SessionPhase.ERROR
        alertMessage.value = SESSION_COPY.stateChanged
        submitLocked.value = false
      }
      return
    }
    if (!isLiveScope(scope)) {
      persistOwnedGate(nextGate)
      return
    }

    const visibleSession = current.session.value
    const finishWithoutCurrentQuestion =
      response.nextAction === DecisionAction.FINISH &&
      (visibleSession == null || visibleSession.sessionId === record.sessionId) &&
      visibleSession?.currentQuestion == null

    if (visibleQuestionMatchesRecord(record) || finishWithoutCurrentQuestion) {
      openEvaluationGate(nextGate)
      return
    }

    if (visibleSession?.sessionId === record.sessionId) {
      await realignAfterServerChange(scope)
      return
    }

    persistOwnedGate(nextGate)
  }

  async function handleSubmitFailure(
    error: unknown,
    record: SubmitRecord,
    scope: OperationScope,
  ): Promise<void> {
    if (!isLiveScope(scope)) {
      return
    }
    submitLocked.value = false
    if (isApiError(error) && error.kind === ErrorKind.UNAUTHENTICATED) {
      return
    }
    if (isApiError(error) && error.kind === ErrorKind.CONFLICT) {
      await handleConflict(error.message, record, scope)
      return
    }
    if (isEvaluationServiceUnavailable(error)) {
      phase.value = SessionPhase.EVALUATION_PENDING
      alertMessage.value = SESSION_COPY.serviceUnavailable
      draft.value = record.answerContent
      activeRecord.value = record
      return
    }
    if (isApiError(error) && error.kind === ErrorKind.NOT_FOUND) {
      alertMessage.value = SESSION_COPY.stateChanged
      await realignAfterServerChange(scope)
      return
    }
    if (isApiError(error) && error.kind === ErrorKind.VALIDATION) {
      inlineError.value = error.message
      if (record.dispatchedAt) {
        phase.value = SessionPhase.EVALUATION_PENDING
        draft.value = record.answerContent
        activeRecord.value = record
      } else {
        phase.value = SessionPhase.ANSWERING
      }
      return
    }
    if (isApiError(error) && error.kind === ErrorKind.EXTERNAL_SERVICE) {
      phase.value = SessionPhase.EVALUATION_PENDING
      alertMessage.value = SESSION_COPY.externalFailure
      draft.value = record.answerContent
      activeRecord.value = record
      return
    }
    phase.value = SessionPhase.EVALUATION_PENDING
    alertMessage.value = SESSION_COPY.unexpectedFailure
    draft.value = record.answerContent
    activeRecord.value = record
  }

  async function handleConflict(
    message: string,
    record: SubmitRecord,
    scope: OperationScope,
  ): Promise<void> {
    if (!isLiveScope(scope)) {
      return
    }
    const kind = classifyAnswerConflict(message)
    draft.value = record.answerContent
    activeRecord.value = record

    if (kind === AnswerConflictKind.EVALUATING) {
      phase.value = SessionPhase.EVALUATION_PENDING
      alertMessage.value = SESSION_COPY.evaluatingHint
      return
    }

    if (kind === AnswerConflictKind.OTHER_REQUEST_ID) {
      const blocked = persistBlockedMarker(record, kind)
      applyRecoveryBlocked(blocked, SESSION_COPY.recoveryBlockedTitle)
      return
    }

    if (
      kind === AnswerConflictKind.REQUEST_ID_OTHER_QUESTION ||
      kind === AnswerConflictKind.DIFFERENT_CONTENT
    ) {
      const blocked = persistBlockedMarker(record, kind)
      activeRecord.value = blocked
      draft.value = blocked.answerContent
      await realignAfterServerChange(scope)
      if (
        isLiveScope(scope) &&
        current.session.value?.currentQuestion?.interviewQuestionId === record.interviewQuestionId
      ) {
        applyRecoveryBlocked(blocked, SESSION_COPY.conflictLocked)
      }
      return
    }

    if (
      kind === AnswerConflictKind.WRONG_CURRENT ||
      kind === AnswerConflictKind.SESSION_NOT_PROGRESS ||
      kind === AnswerConflictKind.UNKNOWN_CONFLICT
    ) {
      await realignAfterServerChange(scope)
    }
  }

  async function realignAfterServerChange(scope: OperationScope | null = captureScope()): Promise<void> {
    try {
      const session = await current.refresh()
      if (!isLiveScope(scope)) {
        return
      }
      const authoritative = session ?? current.session.value
      if (session === null && current.session.value !== null) {
        await applyBootstrapResult(current.session.value.sessionId, scope)
        return
      }
      if (!authoritative) {
        const latestGate = readLatestEvaluationGate(scope.userId)
        if (latestGate?.nextAction === DecisionAction.FINISH) {
          gate.value = latestGate
          draft.value = latestGate.answerContent
          phase.value = SessionPhase.EVALUATION_GATE
          finishedSessionId.value = latestGate.sessionId
          return
        }
        await router.replace({ name: 'dashboard' })
        return
      }
      const previousQuestionId = activeRecord.value?.interviewQuestionId
      await applyBootstrapResult(authoritative.sessionId, scope)
      if (
        isLiveScope(scope) &&
        authoritative.currentQuestion &&
        previousQuestionId &&
        authoritative.currentQuestion.interviewQuestionId !== previousQuestionId &&
        phase.value === SessionPhase.ANSWERING
      ) {
        banner.value = SESSION_COPY.workspaceChanged
      }
    } catch (caught) {
      if (!isLiveScope(scope)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        return
      }
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.clientStateError
    }
  }

  async function submitAnswer(): Promise<void> {
    if (submitLocked.value) {
      return
    }
    if (phase.value === SessionPhase.EVALUATION_PENDING || phase.value === SessionPhase.RECOVERY_BLOCKED) {
      return
    }
    if (phase.value !== SessionPhase.ANSWERING) {
      return
    }

    const scope = captureScope()
    if (!scope) {
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.missingUserId
      return
    }

    const session = current.session.value
    const question = session?.currentQuestion
    if (!session || !question) {
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.recoveringQuestion
      return
    }

    const issue = validateAnswerContent(draft.value)
    if (issue) {
      inlineError.value = validationMessage(issue)
      return
    }

    const answeredQuestionSnapshot: InterviewQuestionResponse = { ...question }
    submitLocked.value = true
    inlineError.value = null
    alertMessage.value = null
    const parts: SubmitRecordKeyParts = {
      userId: scope.userId,
      sessionId: session.sessionId,
      interviewQuestionId: question.interviewQuestionId,
    }

    let record = activeRecord.value ?? readSubmitRecord(parts)
    if (record && record.dispatchedAt) {
      submitLocked.value = false
      draft.value = record.answerContent
      activeRecord.value = record
      phase.value = SessionPhase.EVALUATION_PENDING
      return
    }

    const now = new Date().toISOString()
    if (!record) {
      record = {
        userId: scope.userId,
        sessionId: session.sessionId,
        interviewQuestionId: question.interviewQuestionId,
        requestId: createRequestId(),
        answerContent: draft.value,
        createdAt: now,
        updatedAt: now,
      }
    } else {
      record = {
        ...record,
        answerContent: draft.value,
        updatedAt: now,
      }
    }

    if (answeredQuestionSnapshot.interviewQuestionId !== record.interviewQuestionId) {
      submitLocked.value = false
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.stateChanged
      return
    }

    try {
      const dispatched = await persistThenDispatch(record, parts)
      if (!isLiveScope(scope)) {
        return
      }
      activeRecord.value = dispatched
      draft.value = dispatched.answerContent
      phase.value = SessionPhase.SUBMITTING
      await postAnswer(dispatched, answeredQuestionSnapshot, scope)
    } catch (caught) {
      if (caught instanceof SubmitRecordPersistError) {
        if (isLiveScope(scope)) {
          submitLocked.value = false
          phase.value = SessionPhase.ANSWERING
          alertMessage.value = SESSION_COPY.persistUnavailable
        }
        return
      }
      const failedRecord = activeRecord.value ?? record
      await handleSubmitFailure(caught, { ...failedRecord, dispatchedAt: failedRecord.dispatchedAt ?? now }, scope)
    }
  }

  async function retrySamePair(): Promise<void> {
    if (checkingCurrent.value || submitLocked.value) {
      return
    }
    const record = activeRecord.value
    if (!record?.dispatchedAt) {
      return
    }
    if (phase.value !== SessionPhase.EVALUATION_PENDING) {
      return
    }
    const scope = captureScope()
    if (!scope) {
      return
    }
    const answeredQuestionSnapshot = matchingCurrentQuestion(record)
    if (!answeredQuestionSnapshot) {
      await realignAfterServerChange(scope)
      return
    }
    submitLocked.value = true
    alertMessage.value = null
    inlineError.value = null
    phase.value = SessionPhase.SUBMITTING
    try {
      await postAnswer(record, answeredQuestionSnapshot, scope)
    } catch (caught) {
      await handleSubmitFailure(caught, record, scope)
    }
  }

  async function checkCurrentStatus(): Promise<void> {
    if (checkingCurrent.value || submitLocked.value || phase.value === SessionPhase.SUBMITTING) {
      return
    }
    const scope = captureScope()
    checkingCurrent.value = true
    try {
      await realignAfterServerChange(scope)
    } finally {
      if (mounted) {
        checkingCurrent.value = false
      }
    }
  }

  async function continueAfterEvaluation(): Promise<void> {
    const currentGate = gate.value
    const scope = captureScope()
    if (!currentGate || submitLocked.value) {
      return
    }
    if (currentGate.nextAction === DecisionAction.FINISH) {
      await finishInterview()
      return
    }

    const oldQuestionId = currentGate.answeredQuestion.interviewQuestionId
    const sessionId = currentGate.sessionId
    const userId = scope?.userId ?? auth.userId
    gate.value = null
    clearEvaluationGate(sessionId, userId)
    if (userId !== null) {
      deleteSubmitRecord({
        userId,
        sessionId,
        interviewQuestionId: oldQuestionId,
      })
    }
    activeRecord.value = null
    draft.value = ''
    submitLocked.value = false
    phase.value = SessionPhase.BOOTSTRAP
    try {
      const session = await current.refresh()
      if (!isLiveScope(scope)) {
        return
      }
      if (session === null && current.session.value !== null) {
        await applyBootstrapResult(current.session.value.sessionId, scope)
        return
      }
      await applyBootstrapResult(session?.sessionId ?? null, scope)
    } catch (caught) {
      if (!isLiveScope(scope)) {
        return
      }
      if (isApiError(caught) && caught.kind === ErrorKind.UNAUTHENTICATED) {
        return
      }
      phase.value = SessionPhase.ERROR
      alertMessage.value = SESSION_COPY.clientStateError
    }
  }

  async function finishInterview(): Promise<void> {
    const currentGate = gate.value
    const scope = captureScope()
    const sessionId = currentGate?.sessionId ?? finishedSessionId.value ?? current.session.value?.sessionId
    if (!sessionId) {
      if (isLiveScope(scope)) {
        await router.push({ name: 'dashboard' })
      }
      return
    }
    const userId = scope?.userId ?? auth.userId
    if (currentGate) {
      clearEvaluationGate(currentGate.sessionId, userId)
      if (userId !== null) {
        deleteSubmitRecord({
          userId,
          sessionId: currentGate.sessionId,
          interviewQuestionId: currentGate.answeredQuestion.interviewQuestionId,
        })
      }
    }
    gate.value = null
    activeRecord.value = null
    if (!isLiveScope(scope)) {
      return
    }
    current.invalidate()
    await router.push({ name: 'interview-report', params: { sessionId: String(sessionId) } })
  }

  async function retryBootstrap(): Promise<void> {
    await bootstrap()
  }

  onUnmounted(() => {
    mounted = false
    viewGeneration += 1
  })

  return {
    session: current.session,
    loaded: current.loaded,
    loading: current.loading,
    error: current.error,
    phase,
    draft,
    inlineError,
    banner,
    alertMessage,
    resumeNotice,
    gate,
    activeRecord,
    submitLocked,
    checkingCurrent,
    workspaceQuestion,
    submittedAnswer,
    answerLocked,
    canSubmit,
    bootstrap,
    setDraft,
    submitAnswer,
    retrySamePair,
    checkCurrentStatus,
    continueAfterEvaluation,
    finishInterview,
    retryBootstrap,
  }
}
