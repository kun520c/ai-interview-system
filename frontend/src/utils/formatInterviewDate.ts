const DATE_TIME: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
}

const DATE_SHORT: Intl.DateTimeFormatOptions = {
  month: 'numeric',
  day: 'numeric',
}

function parseInterviewDate(value: string): Date | null {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date
}

export function formatInterviewDate(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = parseInterviewDate(value)
  if (!date) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', DATE_TIME).format(date)
}

export function formatInterviewDateShort(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = parseInterviewDate(value)
  if (!date) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', DATE_SHORT).format(date)
}
