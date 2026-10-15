export const DOCUMENT_TITLE_PREFIX = '面试训练'

export function applyDocumentTitle(pageTitle: string): string {
  return `${DOCUMENT_TITLE_PREFIX} · ${pageTitle}`
}
