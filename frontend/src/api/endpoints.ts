export const AUTH_PUBLIC_PATHS = ['/auth/login', '/auth/register'] as const

export const endpoints = {
  register: '/auth/register',
  login: '/auth/login',
  me: '/users/me',
  changePassword: '/users/me/password',
  interviews: '/interviews',
  currentInterview: '/interviews/current',
  interviewHistory: '/interviews/history',
  interviewDetail: (sessionId: number) => `/interviews/${sessionId}`,
  interviewAnswers: (sessionId: number) => `/interviews/${sessionId}/answers`,
  interviewReport: (sessionId: number) => `/interviews/${sessionId}/report`,
  adminQuestions: '/admin/questions',
  adminQuestion: (questionId: number) => `/admin/questions/${questionId}`,
  adminQuestionStatus: (questionId: number) => `/admin/questions/${questionId}/status`,
  knowledgeDocuments: '/admin/knowledge/documents',
  knowledgeDocumentProcess: (documentId: number) =>
    `/admin/knowledge/documents/${documentId}/process`,
} as const

export function isAuthPublicPath(url: string | undefined): boolean {
  if (!url) {
    return false
  }
  return AUTH_PUBLIC_PATHS.some(
    (path) => url === path || url.endsWith(path) || url.includes(`${path}?`),
  )
}
