import { DecisionAction, EvaluationPhase, InterviewQuestionType } from '@/types/enums'

export const SESSION_COPY = {
  resumeNotice: '已为你继续未完成的面试',
  staleEvaluationNotice: '上一题评价可在完成后的详情中查看。',
  recoveringQuestion: '当前面试正在恢复',
  recoveringQuestionHint: '当前题目暂时不可用。可以重试加载当前面试，不要编造题目。',
  retryCurrent: '重试当前面试',
  persistUnavailable: '本机恢复存储不可用，答案尚未提交。',
  missingUserId: '本机登录状态不完整，无法提交答案。请重新登录后再试。',
  blankAnswer: '请先填写答案再提交。',
  oversizedAnswer: '答案超出允许长度，请缩短后再提交。',
  dispatchedLock: '该答案已经提交过一次，恢复操作必须使用原提交内容。',
  submittingStatus: '正在评估',
  submittingHint: '系统正在根据回答进行评分，可能需要一些时间。',
  pendingTitle: '本次提交结果暂时无法确认。',
  pendingHint: '系统不会创建新的答案提交。',
  pendingRetry: '按原答案重试',
  pendingReplay: '按原答案重新获取评价',
  checkCurrent: '检查当前面试状态',
  evaluatingHint: '答案正在评估中，请稍后按原答案重新获取评价。',
  recoveryBlockedTitle: '当前答案已在服务端登记，但本机恢复信息与服务端提交身份不一致。',
  recoveryBlockedHint: '请不要继续创建新的提交。',
  recoveryBlockedRefresh: '刷新当前状态',
  conflictLocked: '本机提交身份与服务端冲突，不能创建新的提交。',
  serviceUnavailable: '评价服务暂时不可用，稍后可按原答案重试。',
  stateChanged: '当前面试状态已变化',
  unexpectedFailure: '提交结果暂时无法确认，请按原答案重试。',
  externalFailure: '外部服务暂时不可用，请按原答案重试。',
  clientStateError: '当前面试状态暂时无法加载',
  submit: '提交答案',
  followUp: '继续追问',
  nextMain: '下一题',
  finish: '完成本场并查看报告',
  questionPane: '当前题目',
  answerPane: '作答',
  submittedAnswerPane: '已提交的回答',
  feedbackPane: '本题反馈',
  workspaceChanged: '当前面试工作区已更新为服务端的最新题目。',
} as const

export const EVALUATION_PHASE_LABEL: Record<EvaluationPhase, string> = {
  [EvaluationPhase.INITIAL]: '阶段性评价',
  [EvaluationPhase.FINAL]: '本题最终评价',
}

export const QUESTION_TYPE_LABEL: Record<InterviewQuestionType, string> = {
  [InterviewQuestionType.MAIN]: '主问题',
  [InterviewQuestionType.FOLLOW_UP]: '追问',
}

export const SCORE_DIMENSIONS = [
  { key: 'correctnessScore', label: '正确' },
  { key: 'completenessScore', label: '完整' },
  { key: 'depthScore', label: '深度' },
  { key: 'clarityScore', label: '清晰' },
  { key: 'practiceScore', label: '实践' },
] as const

export function continueLabel(nextAction: DecisionAction): string {
  if (nextAction === DecisionAction.FOLLOW_UP) {
    return SESSION_COPY.followUp
  }
  if (nextAction === DecisionAction.NEXT_MAIN) {
    return SESSION_COPY.nextMain
  }
  return SESSION_COPY.finish
}
