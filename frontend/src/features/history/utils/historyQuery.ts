import type { LocationQuery, LocationQueryValue } from 'vue-router'

export const HISTORY_DEFAULT_PAGE = 1
export const HISTORY_DEFAULT_PAGE_SIZE = 10
export const HISTORY_MAX_PAGE_SIZE = 50

export interface HistoryQuery {
  page: number
  pageSize: number
  canonical: boolean
}

function firstQueryValue(value: LocationQueryValue | LocationQueryValue[] | undefined): string | null {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : null
  }
  return typeof value === 'string' ? value : null
}

function parsePositiveInteger(raw: string | null): number | null {
  if (raw === null) {
    return null
  }
  if (!/^[1-9]\d*$/.test(raw)) {
    return null
  }
  const parsed = Number(raw)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

export function parseHistoryQuery(query: LocationQuery): HistoryQuery {
  const rawPage = firstQueryValue(query.page)
  const rawPageSize = firstQueryValue(query.pageSize)

  if (rawPage === null && rawPageSize === null) {
    return {
      page: HISTORY_DEFAULT_PAGE,
      pageSize: HISTORY_DEFAULT_PAGE_SIZE,
      canonical: true,
    }
  }

  const parsedPage = parsePositiveInteger(rawPage)
  const parsedPageSize = parsePositiveInteger(rawPageSize)

  const page = parsedPage ?? HISTORY_DEFAULT_PAGE
  let pageSize = parsedPageSize ?? HISTORY_DEFAULT_PAGE_SIZE
  if (pageSize > HISTORY_MAX_PAGE_SIZE) {
    pageSize = HISTORY_MAX_PAGE_SIZE
  }

  const canonical =
    rawPage === String(page) &&
    rawPageSize === String(pageSize)

  return { page, pageSize, canonical }
}

export function historyQueryRecord(page: number, pageSize: number): Record<string, string> {
  return {
    page: String(page),
    pageSize: String(pageSize),
  }
}
