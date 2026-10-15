import { apiPost, apiPostForm } from '@/api/httpClient'
import { endpoints } from '@/api/endpoints'
import type { QuestionCategory } from '@/types/enums'
import type { UploadKnowledgeDocumentResponse } from '@/types/knowledge'

export interface UploadKnowledgeDocumentInput {
  file: File
  title: string
  category: QuestionCategory
  source?: string
}

export function buildKnowledgeUploadForm(input: UploadKnowledgeDocumentInput): FormData {
  const form = new FormData()
  form.append('file', input.file)
  form.append('title', input.title)
  form.append('category', input.category)
  if (input.source) {
    form.append('source', input.source)
  }
  return form
}

export function uploadKnowledgeDocument(
  input: UploadKnowledgeDocumentInput,
): Promise<UploadKnowledgeDocumentResponse> {
  return apiPostForm<UploadKnowledgeDocumentResponse>(
    endpoints.knowledgeDocuments,
    buildKnowledgeUploadForm(input),
  )
}

export function processKnowledgeDocument(documentId: number): Promise<null> {
  return apiPost<null>(endpoints.knowledgeDocumentProcess(documentId))
}
