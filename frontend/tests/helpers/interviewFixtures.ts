import {
  InterviewDetailAnswerResponse,
  InterviewDetailQuestionResponse,
  InterviewDetailResponse,
  InterviewHistoryItemResponse,
  InterviewHistoryPageResponse,
  InterviewEvaluationResponse,
  InterviewQuestionResponse,
  InterviewReportResponse,
  InterviewSessionResponse,
  SubmitInterviewAnswerResponse,
} from '@/types/interview'
import type { EvaluationGate } from '@/features/interview/types/evaluationGate'
import {
  DecisionAction,
  EvaluationLevel,
  EvaluationPhase,
  InterviewAnswerStatus,
  InterviewQuestionStatus,
  InterviewQuestionType,
  InterviewReportStatus,
  InterviewSessionStatus,
  QuestionDifficulty,
} from '@/types/enums'

export function currentQuestion(
  overrides: Partial<InterviewQuestionResponse> = {},
): InterviewQuestionResponse {
  return {
    interviewQuestionId: 88,
    category: 'SPRING',
    knowledgePoint: 'Bean 生命周期',
    questionType: InterviewQuestionType.MAIN,
    content: '请说明 Spring Bean 的生命周期。',
    ...overrides,
  }
}

export function followUpQuestion(
  overrides: Partial<InterviewQuestionResponse> = {},
): InterviewQuestionResponse {
  return currentQuestion({
    interviewQuestionId: 89,
    knowledgePoint: 'Bean 生命周期追问',
    questionType: InterviewQuestionType.FOLLOW_UP,
    content: '初始化回调失败时容器会怎样处理？',
    ...overrides,
  })
}

export function evaluation(
  overrides: Partial<InterviewEvaluationResponse> = {},
): InterviewEvaluationResponse {
  return {
    evaluationPhase: EvaluationPhase.INITIAL,
    correctnessScore: 16,
    completenessScore: 14,
    depthScore: 12,
    clarityScore: 15,
    practiceScore: 13,
    totalScore: 70,
    level: EvaluationLevel.FAIR,
    strengths: ['说明了实例化与依赖注入的顺序。'],
    missingPoints: ['没有提到销毁阶段。'],
    correction: '补充 DisposableBean 与销毁回调的顺序。',
    ...overrides,
  }
}

export function submitResponse(
  overrides: Partial<SubmitInterviewAnswerResponse> = {},
): SubmitInterviewAnswerResponse {
  return {
    evaluation: evaluation(),
    nextAction: DecisionAction.FOLLOW_UP,
    sessionStatus: InterviewSessionStatus.IN_PROGRESS,
    nextQuestion: followUpQuestion(),
    ...overrides,
  }
}

export function evaluationGate(
  overrides: Partial<EvaluationGate> = {},
): EvaluationGate {
  return {
    userId: 3,
    sessionId: 21,
    createdAt: '2026-09-21T04:00:00.000Z',
    answeredQuestion: currentQuestion(),
    answerContent: '先实例化，再注入依赖，然后初始化。',
    evaluation: evaluation(),
    nextAction: DecisionAction.FOLLOW_UP,
    nextQuestion: followUpQuestion(),
    sessionStatus: InterviewSessionStatus.IN_PROGRESS,
    ...overrides,
  }
}

export function emptyHistoryPage(
  overrides: Partial<InterviewHistoryPageResponse> = {},
): InterviewHistoryPageResponse {
  return {
    page: 1,
    pageSize: 5,
    total: 0,
    totalPages: 0,
    items: [],
    ...overrides,
  }
}

export function historyItem(
  overrides: Partial<InterviewHistoryItemResponse> = {},
): InterviewHistoryItemResponse {
  return {
    sessionId: 41,
    difficulty: QuestionDifficulty.MEDIUM,
    status: InterviewSessionStatus.COMPLETED,
    reportStatus: InterviewReportStatus.READY,
    totalScore: 82.5,
    plannedQuestionCount: 7,
    completedQuestionCount: 7,
    startedAt: '2026-09-18T10:00:00',
    endedAt: '2026-09-18T11:20:00',
    createdAt: '2026-09-18T09:50:00',
    ...overrides,
  }
}

export function currentSession(
  overrides: Partial<InterviewSessionResponse> = {},
): InterviewSessionResponse {
  return {
    sessionId: 21,
    difficulty: QuestionDifficulty.MEDIUM,
    status: InterviewSessionStatus.IN_PROGRESS,
    plannedQuestionCount: 7,
    completedQuestionCount: 2,
    currentQuestion: currentQuestion(),
    ...overrides,
  }
}

export const PENDING_FUTURE_QUESTION_SECRET = 'PENDING_FUTURE_QUESTION_SECRET'

export function detailAnswer(
  overrides: Partial<InterviewDetailAnswerResponse> = {},
): InterviewDetailAnswerResponse {
  return {
    answerContent: '先实例化，再注入依赖，然后初始化。',
    answerStatus: InterviewAnswerStatus.EVALUATED,
    submittedAt: '2026-09-18T10:20:00',
    ...overrides,
  }
}

export function detailQuestion(
  overrides: Partial<InterviewDetailQuestionResponse> = {},
): InterviewDetailQuestionResponse {
  return {
    interviewQuestionId: 88,
    questionType: InterviewQuestionType.MAIN,
    category: 'SPRING',
    knowledgePoint: 'Bean 生命周期',
    questionContent: '请说明 Spring Bean 的生命周期。',
    planOrder: 1,
    displayOrder: 1,
    status: InterviewQuestionStatus.ANSWERED,
    answer: detailAnswer(),
    evaluation: evaluation(),
    ...overrides,
  }
}

export function followUpDetailQuestion(
  overrides: Partial<InterviewDetailQuestionResponse> = {},
): InterviewDetailQuestionResponse {
  return detailQuestion({
    interviewQuestionId: 89,
    questionType: InterviewQuestionType.FOLLOW_UP,
    knowledgePoint: 'Bean 生命周期追问',
    questionContent: '初始化回调失败时容器会怎样处理？',
    planOrder: null,
    displayOrder: 2,
    evaluation: evaluation({
      evaluationPhase: EvaluationPhase.FINAL,
      totalScore: 78,
      level: EvaluationLevel.FAIR,
    }),
    ...overrides,
  })
}

export function completedDetail(
  overrides: Partial<InterviewDetailResponse> = {},
): InterviewDetailResponse {
  return {
    sessionId: 41,
    difficulty: QuestionDifficulty.MEDIUM,
    status: InterviewSessionStatus.COMPLETED,
    reportStatus: InterviewReportStatus.READY,
    totalScore: 82.5,
    plannedQuestionCount: 7,
    completedQuestionCount: 7,
    startedAt: '2026-09-18T10:00:00',
    endedAt: '2026-09-18T11:20:00',
    createdAt: '2026-09-18T09:50:00',
    questions: [detailQuestion(), followUpDetailQuestion()],
    ...overrides,
  }
}

export function inProgressDetail(
  overrides: Partial<InterviewDetailResponse> = {},
): InterviewDetailResponse {
  return completedDetail({
    sessionId: 21,
    status: InterviewSessionStatus.IN_PROGRESS,
    reportStatus: InterviewReportStatus.NOT_STARTED,
    totalScore: null,
    completedQuestionCount: 2,
    endedAt: null,
    questions: [
      detailQuestion(),
      detailQuestion({
        interviewQuestionId: 201,
        questionContent: PENDING_FUTURE_QUESTION_SECRET,
        planOrder: 3,
        displayOrder: 5,
        status: InterviewQuestionStatus.PENDING,
        answer: null,
        evaluation: null,
      }),
    ],
    ...overrides,
  })
}

export function createdDetail(
  overrides: Partial<InterviewDetailResponse> = {},
): InterviewDetailResponse {
  return inProgressDetail({
    sessionId: 31,
    status: InterviewSessionStatus.CREATED,
    completedQuestionCount: 0,
    ...overrides,
  })
}

export const CANCELLED_QUESTION_SECRET = 'CANCELLED_PENDING_QUESTION_SECRET'

export function cancelledDetail(
  overrides: Partial<InterviewDetailResponse> = {},
): InterviewDetailResponse {
  return completedDetail({
    sessionId: 51,
    status: InterviewSessionStatus.CANCELLED,
    reportStatus: InterviewReportStatus.NOT_STARTED,
    totalScore: null,
    questions: [
      detailQuestion({
        questionContent: CANCELLED_QUESTION_SECRET,
        status: InterviewQuestionStatus.PENDING,
        answer: null,
        evaluation: null,
      }),
    ],
    ...overrides,
  })
}

export function reportResponse(
  overrides: Partial<InterviewReportResponse> = {},
): InterviewReportResponse {
  return {
    sessionId: 41,
    reportStatus: InterviewReportStatus.READY,
    overallScore: 82.5,
    result: EvaluationLevel.GOOD,
    summary: '本场面试对 Spring 容器机制有基本掌握，但对失败路径说明不足。',
    strengths: ['能够区分实例化与依赖注入。', '提到了初始化回调。'],
    weaknesses: ['销毁阶段覆盖不足。', '缺少对异常路径的说明。'],
    suggestions: ['补齐 Bean 销毁回调。', '用一次失败初始化把容器行为讲清楚。'],
    ...overrides,
  }
}

export function statusReport(
  status: InterviewReportStatus,
  sessionId = 42,
): InterviewReportResponse {
  return {
    sessionId,
    reportStatus: status,
    overallScore: null,
    result: null,
    summary: null,
    strengths: null,
    weaknesses: null,
    suggestions: null,
  }
}
