import { InterviewReportStatus } from '@/types/enums'

export const REPORT_STATUS_LABEL: Record<InterviewReportStatus, string> = {
  [InterviewReportStatus.NOT_STARTED]: '未生成',
  [InterviewReportStatus.GENERATING]: '生成中',
  [InterviewReportStatus.READY]: '已生成',
  [InterviewReportStatus.FAILED]: '生成失败',
}

export const REPORT_SCORE_PLACEHOLDER: Record<
  Exclude<InterviewReportStatus, typeof InterviewReportStatus.READY>,
  string
> = {
  [InterviewReportStatus.NOT_STARTED]: '报告未生成',
  [InterviewReportStatus.GENERATING]: '正在生成',
  [InterviewReportStatus.FAILED]: '生成失败',
}

export const REPORT_ACTION_LABEL: Record<InterviewReportStatus, string> = {
  [InterviewReportStatus.READY]: '查看报告',
  [InterviewReportStatus.GENERATING]: '查看状态',
  [InterviewReportStatus.NOT_STARTED]: '生成报告',
  [InterviewReportStatus.FAILED]: '生成报告',
}

export function reportStatusLabel(status: InterviewReportStatus): string {
  return REPORT_STATUS_LABEL[status]
}

export function reportScorePlaceholder(status: InterviewReportStatus): string {
  if (status === InterviewReportStatus.READY) {
    return '报告数据暂时异常'
  }
  return REPORT_SCORE_PLACEHOLDER[status]
}

export function reportActionLabel(status: InterviewReportStatus): string {
  return REPORT_ACTION_LABEL[status]
}
