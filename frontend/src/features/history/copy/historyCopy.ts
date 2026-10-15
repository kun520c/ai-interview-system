export const HISTORY_COPY = {
  title: '历史面试',
  summary: (total: number) => (total === 0 ? '还没有已完成的面试。' : `共 ${total} 场已完成的面试。`),
  emptyTitle: '还没有完成的面试',
  emptyDescription: '完成第一场后，这里会保存你的面试记录和报告入口。',
  startCta: '开始面试',
  continueCta: '继续当前面试',
  reviewAction: '查看复盘',
  loadError: '历史记录暂时无法加载。',
  retry: '重试',
  previousPage: '上一页',
  nextPage: '下一页',
  pageStatus: (page: number, totalPages: number) => `第 ${page} / ${totalPages} 页`,
} as const
