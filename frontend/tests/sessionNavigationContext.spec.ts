import { beforeEach, describe, expect, it } from 'vitest'

import {
  armInterviewResumeNotice,
  consumeInterviewResumeNotice,
  interviewResumeNoticeState,
  peekInterviewResumeNoticeForTests,
  resetInterviewResumeNoticeForTests,
} from '@/features/interview/navigation/sessionNavigationContext'

describe('sessionNavigationContext', () => {
  beforeEach(() => {
    resetInterviewResumeNoticeForTests()
  })

  it('is a one-shot flag that does not survive consume', () => {
    expect(consumeInterviewResumeNotice(3)).toBe(false)
    armInterviewResumeNotice(3)
    expect(peekInterviewResumeNoticeForTests()).toBe(true)
    expect(consumeInterviewResumeNotice(3)).toBe(true)
    expect(peekInterviewResumeNoticeForTests()).toBe(false)
    expect(consumeInterviewResumeNotice(3)).toBe(false)
  })

  it('consumes router history state once and clears it for the owning user', () => {
    const state = { ...interviewResumeNoticeState(3), keep: true }
    expect(consumeInterviewResumeNotice(3, state)).toBe(true)
    expect(state.interviewResumeNotice).toBeUndefined()
    expect(state.interviewResumeNoticeUserId).toBeUndefined()
    expect(consumeInterviewResumeNotice(3, state)).toBe(false)
  })

  it('does not surface another user resume notice', () => {
    armInterviewResumeNotice(3)
    expect(consumeInterviewResumeNotice(11)).toBe(false)
    expect(peekInterviewResumeNoticeForTests()).toBe(false)
    const state = { ...interviewResumeNoticeState(3), keep: true }
    expect(consumeInterviewResumeNotice(11, state)).toBe(false)
    expect(state.interviewResumeNotice).toBeUndefined()
  })
})
