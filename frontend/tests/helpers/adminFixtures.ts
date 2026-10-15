import {
  QuestionCategory,
  QuestionDifficulty,
  QuestionPointType,
  QuestionStatus,
} from '@/types/enums'
import type {
  AdminQuestionDetailResponse,
  AdminQuestionListItem,
  AdminQuestionPageResponse,
  CreateQuestionResponse,
} from '@/types/question'
import type { LocalKnowledgeDocument, UploadKnowledgeDocumentResponse } from '@/types/knowledge'
import { KnowledgeFileType, KnowledgeProcessingStatus } from '@/types/enums'

export function adminQuestionItem(
  overrides: Partial<AdminQuestionListItem> = {},
): AdminQuestionListItem {
  return {
    id: 7,
    category: QuestionCategory.SPRING,
    knowledgePoint: 'Bean 生命周期',
    difficulty: QuestionDifficulty.MEDIUM,
    questionContent: '请说明 Spring Bean 的生命周期。',
    status: QuestionStatus.ENABLED,
    updatedAt: '2026-09-18T11:00:00',
    ...overrides,
  }
}

export function adminQuestionPage(
  overrides: Partial<AdminQuestionPageResponse> = {},
): AdminQuestionPageResponse {
  return {
    records: [adminQuestionItem()],
    total: 1,
    pageNum: 1,
    pageSize: 10,
    totalPages: 1,
    ...overrides,
  }
}

export function adminQuestionDetail(
  overrides: Partial<AdminQuestionDetailResponse> = {},
): AdminQuestionDetailResponse {
  return {
    id: 7,
    category: QuestionCategory.SPRING,
    knowledgePoint: 'Bean 生命周期',
    difficulty: QuestionDifficulty.MEDIUM,
    content: '请说明 Spring Bean 的生命周期。',
    referenceAnswer: '实例化、依赖注入、初始化、销毁。',
    status: QuestionStatus.ENABLED,
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-18T11:00:00',
    scoringPoints: [
      {
        pointType: QuestionPointType.CORE,
        content: '覆盖实例化与依赖注入',
        weight: 60,
      },
      {
        pointType: QuestionPointType.KEY,
        content: '提到销毁回调',
        weight: 40,
      },
    ],
    ...overrides,
  }
}

export function createQuestionResponse(
  overrides: Partial<CreateQuestionResponse> = {},
): CreateQuestionResponse {
  return {
    questionId: 88,
    ...overrides,
  }
}

export function uploadedKnowledge(
  overrides: Partial<UploadKnowledgeDocumentResponse> = {},
): UploadKnowledgeDocumentResponse {
  return {
    documentId: 15,
    fileName: 'spring.md',
    fileType: KnowledgeFileType.MARKDOWN,
    documentVersion: 1,
    processingStatus: KnowledgeProcessingStatus.UPLOADED,
    ...overrides,
  }
}

export function localKnowledgeDoc(
  overrides: Partial<LocalKnowledgeDocument> = {},
): LocalKnowledgeDocument {
  return {
    documentId: 15,
    title: 'Spring Bean',
    category: QuestionCategory.SPRING,
    fileName: 'spring.md',
    processingStatus: KnowledgeProcessingStatus.UPLOADED,
    updatedAt: '2026-09-21T08:00:00.000Z',
    fileType: KnowledgeFileType.MARKDOWN,
    documentVersion: 1,
    recoveryState: null,
    ...overrides,
  }
}
