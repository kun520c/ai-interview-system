import { describe, expect, it } from 'vitest'

import { parseHistoryQuery } from '@/features/history/utils/historyQuery'

describe('parseHistoryQuery', () => {
  it('uses defaults for missing query', () => {
    expect(parseHistoryQuery({})).toEqual({ page: 1, pageSize: 10, canonical: true })
  })

  it('keeps a valid query canonical', () => {
    expect(parseHistoryQuery({ page: '2', pageSize: '10' })).toEqual({
      page: 2,
      pageSize: 10,
      canonical: true,
    })
  })

  it('canonicalizes illegal page and oversize pageSize', () => {
    expect(parseHistoryQuery({ page: '0', pageSize: '99' })).toEqual({
      page: 1,
      pageSize: 50,
      canonical: false,
    })
    expect(parseHistoryQuery({ page: 'abc', pageSize: '-1' })).toEqual({
      page: 1,
      pageSize: 10,
      canonical: false,
    })
  })
})
