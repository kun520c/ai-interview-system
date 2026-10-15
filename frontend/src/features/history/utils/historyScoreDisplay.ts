import { formatScore } from '@/utils/formatScore'
import { InterviewReportStatus } from '@/types/enums'
import type { InterviewHistoryItemResponse } from '@/types/interview'

export function historyScoreText(item: InterviewHistoryItemResponse): string {
  if (item.totalScore !== null && item.totalScore !== undefined) {
    return formatScore(item.totalScore)
  }

  if (item.reportStatus === InterviewReportStatus.NOT_STARTED) {
    return '报告未生成'
  }
  if (item.reportStatus === InterviewReportStatus.GENERATING) {
    return '报告生成中'
  }
  if (item.reportStatus === InterviewReportStatus.FAILED) {
    return '报告生成失败'
  }
  return '报告数据暂时异常'
}

export const HISTORY_REPORT_ACTION_LABEL: Record<InterviewReportStatus, string> = {
  [InterviewReportStatus.READY]: '查看报告',
  [InterviewReportStatus.GENERATING]: '查看状态',
  [InterviewReportStatus.NOT_STARTED]: '生成报告',
  [InterviewReportStatus.FAILED]: '重新生成报告',
}

export function historyReportActionLabel(status: InterviewReportStatus): string {
  return HISTORY_REPORT_ACTION_LABEL[status]
}
