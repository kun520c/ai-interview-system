export const DASHBOARD_INTERVIEW_FLOW = [
  { step: '01', label: '选择难度' },
  { step: '02', label: '回答主问题' },
  { step: '03', label: '可能追问' },
  { step: '04', label: '完成本场' },
  { step: '05', label: '生成报告' },
] as const

export const CREATE_INTERVIEW_FLOW = [
  { step: '01', label: '选择难度' },
  { step: '02', label: '系统准备本场主问题' },
  { step: '03', label: '逐题回答并获得反馈' },
  { step: '04', label: '每个主问题最多一次追问' },
  { step: '05', label: '完成后生成报告' },
] as const
