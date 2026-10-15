import {
  KNOWLEDGE_ALLOWED_EXTENSIONS,
  KNOWLEDGE_ADMIN_COPY,
  KNOWLEDGE_MAX_FILE_BYTES,
} from '@/features/admin/knowledge/copy/knowledgeAdminCopy'

export function fileExtension(fileName: string): string {
  const trimmed = fileName.trim().toLowerCase()
  const index = trimmed.lastIndexOf('.')
  if (index <= 0) {
    return ''
  }
  return trimmed.slice(index)
}

export function validateKnowledgeFile(file: File | null): string | null {
  if (!file) {
    return KNOWLEDGE_ADMIN_COPY.missingFile
  }
  if (file.size <= 0) {
    return KNOWLEDGE_ADMIN_COPY.emptyFile
  }
  if (file.size > KNOWLEDGE_MAX_FILE_BYTES) {
    return KNOWLEDGE_ADMIN_COPY.oversized
  }
  if (!KNOWLEDGE_ALLOWED_EXTENSIONS.includes(fileExtension(file.name) as (typeof KNOWLEDGE_ALLOWED_EXTENSIONS)[number])) {
    return KNOWLEDGE_ADMIN_COPY.badExtension
  }
  return null
}
