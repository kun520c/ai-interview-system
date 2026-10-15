import type {
  KnowledgeFileType,
  KnowledgeProcessingStatus,
  QuestionCategory,
} from '@/types/enums'

export interface UploadKnowledgeDocumentResponse {
  documentId: number
  fileName: string
  fileType: KnowledgeFileType
  documentVersion: number
  processingStatus: KnowledgeProcessingStatus
}

export type KnowledgeRecoveryState = 'UNCONFIRMED' | 'UNAVAILABLE'

export interface LocalKnowledgeDocument {
  documentId: number
  title: string
  category: QuestionCategory
  fileName: string
  processingStatus: KnowledgeProcessingStatus
  updatedAt: string
  fileType?: KnowledgeFileType
  documentVersion?: number
  recoveryState?: KnowledgeRecoveryState | null
}
