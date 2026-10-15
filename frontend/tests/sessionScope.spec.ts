import { beforeEach, describe, expect, it } from 'vitest'

import {
  getSessionGeneration,
  isCurrentIdentityScope,
  notifySessionIdentityChanged,
  resetSessionScopeForTests,
} from '@/app/session/sessionScope'

describe('session identity scope', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
  })

  it('matches only the captured user and generation', () => {
    expect(isCurrentIdentityScope(3, 0, 3)).toBe(true)
    expect(isCurrentIdentityScope(3, 0, 11)).toBe(false)
    expect(isCurrentIdentityScope(null, 0, 3)).toBe(false)
    notifySessionIdentityChanged(11)
    expect(getSessionGeneration()).toBe(1)
    expect(isCurrentIdentityScope(3, 0, 3)).toBe(false)
    expect(isCurrentIdentityScope(11, 1, 11)).toBe(true)
  })
})
