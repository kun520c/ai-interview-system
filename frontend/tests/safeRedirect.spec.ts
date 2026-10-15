import { describe, expect, it } from 'vitest'

import { resolveSafeRedirect } from '@/utils/safeRedirect'

describe('safe redirect', () => {
  it('accepts in-app paths', () => {
    expect(resolveSafeRedirect('/interview')).toBe('/interview')
    expect(resolveSafeRedirect('/interviews/12/report')).toBe('/interviews/12/report')
  })

  it('rejects open redirects', () => {
    expect(resolveSafeRedirect('https://evil.com')).toBe('/')
    expect(resolveSafeRedirect('//evil.com')).toBe('/')
    expect(resolveSafeRedirect('/\\evil.com')).toBe('/')
    expect(resolveSafeRedirect('https:%2F%2Fevil.com')).toBe('/')
  })

  it('does not bounce back to login or register', () => {
    expect(resolveSafeRedirect('/login')).toBe('/')
    expect(resolveSafeRedirect('/register?x=1')).toBe('/')
  })

  it('falls back for empty or non-string values', () => {
    expect(resolveSafeRedirect(undefined)).toBe('/')
    expect(resolveSafeRedirect('')).toBe('/')
    expect(resolveSafeRedirect(12)).toBe('/')
  })
})
