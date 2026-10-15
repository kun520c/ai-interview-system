import type { QuestionCategory } from '@/types/enums'
import {
  KnowledgeFileType,
  KnowledgeProcessingStatus,
  QuestionCategory as Categories,
} from '@/types/enums'
import type { KnowledgeRecoveryState, LocalKnowledgeDocument } from '@/types/knowledge'

export const KNOWLEDGE_RECENT_KEY_PREFIX = 'admin.knowledge.recent'
export const KNOWLEDGE_RECENT_MAX = 20
export const KNOWLEDGE_RECENT_PROBE_SUFFIX = '__probe'

export type KnowledgeRecentStorageFailure = 'unavailable' | 'quota' | 'security' | 'unknown'

export class KnowledgeRecentStorageError extends Error {
  readonly reason: KnowledgeRecentStorageFailure

  constructor(reason: KnowledgeRecentStorageFailure, message: string, options?: { cause?: unknown }) {
    super(message, options)
    this.name = 'KnowledgeRecentStorageError'
    this.reason = reason
  }
}

export type KnowledgeRecentWriteResult =
  | { ok: true; documents: LocalKnowledgeDocument[] }
  | { ok: false; error: KnowledgeRecentStorageError; documents: LocalKnowledgeDocument[] }

function getStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function buildKnowledgeRecentKey(userId: number): string {
  return `${KNOWLEDGE_RECENT_KEY_PREFIX}.${userId}`
}

function buildKnowledgeRecentProbeKey(userId: number): string {
  return `${buildKnowledgeRecentKey(userId)}.${KNOWLEDGE_RECENT_PROBE_SUFFIX}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isQuestionCategory(value: unknown): value is QuestionCategory {
  return typeof value === 'string' && (Object.values(Categories) as string[]).includes(value)
}

function isProcessingStatus(value: unknown): value is KnowledgeProcessingStatus {
  return (
    typeof value === 'string' &&
    (Object.values(KnowledgeProcessingStatus) as string[]).includes(value)
  )
}

function isFileType(value: unknown): value is KnowledgeFileType {
  return typeof value === 'string' && (Object.values(KnowledgeFileType) as string[]).includes(value)
}

function isRecoveryState(value: unknown): value is KnowledgeRecoveryState {
  return value === 'UNCONFIRMED' || value === 'UNAVAILABLE'
}

function isLocalDocument(value: unknown): value is LocalKnowledgeDocument {
  if (!isRecord(value)) {
    return false
  }
  const recoveryValid =
    value.recoveryState === undefined || value.recoveryState === null || isRecoveryState(value.recoveryState)
  const fileTypeValid = value.fileType === undefined || isFileType(value.fileType)
  const versionValid =
    value.documentVersion === undefined ||
    (typeof value.documentVersion === 'number' && Number.isInteger(value.documentVersion) && value.documentVersion > 0)
  return (
    typeof value.documentId === 'number' &&
    Number.isSafeInteger(value.documentId) &&
    value.documentId > 0 &&
    typeof value.title === 'string' &&
    isQuestionCategory(value.category) &&
    typeof value.fileName === 'string' &&
    isProcessingStatus(value.processingStatus) &&
    typeof value.updatedAt === 'string' &&
    recoveryValid &&
    fileTypeValid &&
    versionValid
  )
}

function classifyStorageFailure(error: unknown): KnowledgeRecentStorageFailure {
  if (error instanceof DOMException) {
    if (error.name === 'QuotaExceededError' || error.code === 22 || error.code === 1014) {
      return 'quota'
    }
    if (error.name === 'SecurityError') {
      return 'security'
    }
  }
  return 'unknown'
}

function toStorageError(error: unknown, fallback: KnowledgeRecentStorageFailure): KnowledgeRecentStorageError {
  if (error instanceof KnowledgeRecentStorageError) {
    return error
  }
  return new KnowledgeRecentStorageError(fallback, '本机恢复记录保存失败', { cause: error })
}

function safeGetItem(storage: Storage, key: string): { ok: true; value: string | null } | { ok: false } {
  try {
    return { ok: true, value: storage.getItem(key) }
  } catch {
    return { ok: false }
  }
}

function safeRemoveItem(storage: Storage, key: string): void {
  try {
    storage.removeItem(key)
  } catch {
    // Cleanup must not crash the knowledge page.
  }
}

export function probeKnowledgeRecentWritable(
  userId: number,
  storage: Storage | null = getStorage(),
): boolean {
  if (!storage || !Number.isSafeInteger(userId) || userId <= 0) {
    return false
  }
  const key = buildKnowledgeRecentProbeKey(userId)
  const token = `probe:${userId}`
  try {
    storage.setItem(key, token)
    const readBack = storage.getItem(key)
    storage.removeItem(key)
    return readBack === token
  } catch {
    safeRemoveItem(storage, key)
    return false
  }
}

export function readKnowledgeRecent(
  userId: number,
  storage: Storage | null = getStorage(),
): LocalKnowledgeDocument[] {
  if (!storage || !Number.isSafeInteger(userId) || userId <= 0) {
    return []
  }
  const key = buildKnowledgeRecentKey(userId)
  const rawResult = safeGetItem(storage, key)
  if (!rawResult.ok) {
    return []
  }
  const raw = rawResult.value
  if (!raw) {
    return []
  }
  try {
    let parsed: unknown
    try {
      parsed = JSON.parse(raw)
    } catch {
      safeRemoveItem(storage, key)
      return []
    }
    if (!Array.isArray(parsed)) {
      safeRemoveItem(storage, key)
      return []
    }
    const documents = parsed.filter(isLocalDocument)
    if (documents.length !== parsed.length) {
      if (documents.length === 0) {
        safeRemoveItem(storage, key)
        return []
      }
      persistKnowledgeRecent(userId, documents, storage)
    }
    return documents
  } catch {
    return []
  }
}

export function persistKnowledgeRecent(
  userId: number,
  documents: LocalKnowledgeDocument[],
  storage: Storage | null = getStorage(),
): KnowledgeRecentWriteResult {
  const next = documents.filter(isLocalDocument).slice(0, KNOWLEDGE_RECENT_MAX)
  if (!storage || !Number.isSafeInteger(userId) || userId <= 0) {
    return {
      ok: false,
      error: new KnowledgeRecentStorageError('unavailable', '本机恢复存储不可用'),
      documents: next,
    }
  }
  try {
    storage.setItem(buildKnowledgeRecentKey(userId), JSON.stringify(next))
    return { ok: true, documents: next }
  } catch (error) {
    return {
      ok: false,
      error: toStorageError(error, classifyStorageFailure(error)),
      documents: next,
    }
  }
}

export function upsertKnowledgeRecent(
  userId: number,
  document: LocalKnowledgeDocument,
  storage: Storage | null = getStorage(),
): KnowledgeRecentWriteResult {
  const existing = readKnowledgeRecent(userId, storage).filter(
    (item) => item.documentId !== document.documentId,
  )
  const next = [document, ...existing].slice(0, KNOWLEDGE_RECENT_MAX)
  return persistKnowledgeRecent(userId, next, storage)
}
