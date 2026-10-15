export const KNOWLEDGE_ADMIN_COPY = {
  title: '知识文档',
  description: '上传 Markdown 或 TXT，再显式开始处理。上传不会自动向量化。',
  uploadTitle: '上传文档',
  file: '文件',
  fileHint: '仅 UTF-8 的 .md / .markdown / .txt，最大 5 MiB。',
  titleLabel: '标题',
  category: '知识领域',
  source: '来源（可选）',
  upload: '上传',
  uploading: '正在上传',
  uploaded: '上传成功 / 尚未处理',
  startProcess: '开始处理',
  processing: '处理中...',
  retryProcess: '按同一文档继续处理',
  recentTitle: '本机最近上传',
  recentHint: '这里只显示当前浏览器、当前账号上传过的文档，不是服务器文档列表。',
  emptyRecent: '还没有本机最近上传。',
  loadError: '知识文档操作暂时失败。',
  retry: '重试',
  uploadUncertain: '上传结果无法确认，再次上传可能创建另一份文档。',
  processUncertain: '处理结果待确认',
  processUnavailable: '知识文档处理服务当前不可用',
  notFound: '知识文档不存在',
  ready: '已处理完成',
  processingStatus: '处理中',
  uploadedStatus: '已上传',
  failedStatus: '处理失败',
  oversized: '上传文件不能超过5MB',
  emptyFile: '不能为空文件',
  missingFile: '请选择文件',
  badExtension: '当前只支持 Markdown 和 TXT 文件',
  storageUnavailable: '本机恢复存储不可用，暂时无法安全上传文档。',
  uploadPersistFailed:
    '文档已上传成功，但本机恢复记录保存失败。请不要刷新页面，并先完成文档处理。',
  processPersistFailed: '处理已完成，但本机状态保存失败。',
} as const

export const KNOWLEDGE_PROCESS_READY = '文档已处理完成，不能重复处理'
export const KNOWLEDGE_PROCESS_BUSY = '文档正在处理中，请稍后重试'
export const KNOWLEDGE_PROCESS_FORBIDDEN_STATE = '文档当前状态不允许处理'
export const KNOWLEDGE_SERVICE_UNAVAILABLE = '知识文档处理服务当前不可用'
export const KNOWLEDGE_NOT_FOUND = '知识文档不存在'
export const KNOWLEDGE_OVERSIZE = '上传文件不能超过5MB'

export const KNOWLEDGE_MAX_FILE_BYTES = 5 * 1024 * 1024
export const KNOWLEDGE_TITLE_MAX = 200
export const KNOWLEDGE_SOURCE_MAX = 255
export const KNOWLEDGE_ALLOWED_EXTENSIONS = ['.md', '.markdown', '.txt'] as const
