export const REPORT_COPY = {
  title: '面试报告',
  missingRecord: '面试记录不存在或无权查看',
  backToHistory: '返回历史',
  openDetail: '查看复盘',
  loadingSession: '正在确认本场面试状态',
  loadingReport: '正在加载报告',
  notStartedTitle: '尚未生成报告',
  notStartedHint: '生成后可查看本场整体评价、优势、薄弱点和后续建议。',
  generate: '生成报告',
  failedTitle: '上次报告生成未完成',
  failedHint: '可以重新生成。系统不会在你点击前再次提交。',
  retryGenerate: '重新生成',
  generatingTitle: '报告正在生成',
  generatingHint: '可能需要一些时间，可以留在页面等待。',
  generatingBusy: '正在生成报告',
  recoverGenerating: '检查报告状态',
  recoveryHint:
    '当前记录为生成中，可离开页面后再回来查看。检查状态不会重复生成；若长时间没有变化，请联系管理员核对。失败后可点击重新生成。',
  stillGenerating: '当前记录仍为生成中，本次未重复执行。页面会查询最新状态；若长时间没有变化，请联系管理员核对。',
  outcome: '结果',
  meaning: '本场说明',
  strength: '优势',
  gap: '薄弱点',
  action: '建议',
  evidence: '过程证据',
  savedEvidence: '逐题结果已就绪',
  savedEvidenceHint: '以下内容来自本场已保存的逐题评价，综合报告尚未完成。',
  overallScore: '总分',
  abnormalTitle: '报告数据暂时异常',
  abnormalHint: '服务端标记报告已生成，但内容不完整。不会用零分或空卡片代替。',
  refreshStatus: '刷新报告状态',
  loadError: '报告状态暂时无法确认。',
  retry: '重试',
  serviceUnavailable: '报告生成服务暂时不可用',
  pollNetworkHint: '暂时无法刷新报告状态，可手动重试。',
  recoveryUnknownTitle: '报告生成结果暂时无法确认。',
  recoveryUnknownHint: '不会再次提交生成请求。可以刷新报告状态，确认后再决定是否生成。',
} as const

export const REPORT_CONFLICT_GENERATING = '报告正在生成中，请稍后重试'
export const REPORT_CONFLICT_STATE_CHANGED = '报告生成状态已发生变化，请重试'
export const REPORT_CONFLICT_NOT_COMPLETED = '面试尚未完成，无法生成报告'
export const REPORT_SERVICE_UNAVAILABLE = '报告生成服务当前不可用'
export const REPORT_SERVICE_DISABLED = '报告生成服务未启用，请联系管理员'
export const REPORT_SERVICE_NOT_ACCEPTING = '报告生成服务暂不受理新请求，请稍后重试'
