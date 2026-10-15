import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '@/app/stores/auth'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import { useCurrentInterviewStore } from '@/features/interview/stores/currentInterview'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { writeAuthCredential } from '@/features/auth/storage/authStorage'
import { IdentityStatus } from '@/types/auth'
import { InterviewSessionStatus, QuestionDifficulty } from '@/types/enums'
import type { InterviewSessionResponse } from '@/types/interview'
import { deferred } from './helpers/deferred'

vi.mock('@/features/interview/api/interviewApi', () => ({
  fetchCurrentInterview: vi.fn(),
  createInterview: vi.fn(),
  createOrResumeInterview: vi.fn(),
  submitInterviewAnswer: vi.fn(),
  fetchInterviewDetail: vi.fn(),
  fetchInterviewReport: vi.fn(),
  generateInterviewReport: vi.fn(),
}))

const sessionA: InterviewSessionResponse = {
  sessionId: 11,
  difficulty: QuestionDifficulty.EASY,
  status: InterviewSessionStatus.IN_PROGRESS,
  plannedQuestionCount: 5,
  completedQuestionCount: 0,
  currentQuestion: {
    interviewQuestionId: 101,
    category: 'JAVA_BASIC',
    knowledgePoint: '集合',
    questionType: 'MAIN',
    content: 'A 的当前题',
  },
}

const sessionB: InterviewSessionResponse = {
  ...sessionA,
  sessionId: 22,
  currentQuestion: {
    interviewQuestionId: 202,
    category: 'SPRING',
    knowledgePoint: 'Bean',
    questionType: 'MAIN',
    content: 'B 的当前题',
  },
}

function credential(userId: number) {
  return {
    accessToken: `token-${userId}`,
    tokenType: 'Bearer',
    expiresAt: Date.now() + 3_600_000,
    userId,
  }
}

describe('currentInterview user scope', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(fetchCurrentInterview).mockReset()
  })

  it('clears loaded session on logout', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValue(sessionA)
    const interview = useCurrentInterviewStore()
    const auth = useAuthStore()
    writeAuthCredential(credential(1))
    auth.accessToken = 'token-1'
    auth.userId = 1
    auth.identityStatus = IdentityStatus.RESOLVED
    auth.ready = true

    await interview.refresh()
    expect(interview.session?.sessionId).toBe(11)

    auth.logout()
    expect(interview.session).toBeNull()
    expect(interview.loaded).toBe(false)
  })

  it('ignores a stale in-flight refresh after logout', async () => {
    const pending = deferred<InterviewSessionResponse | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pending.promise)
    const interview = useCurrentInterviewStore()
    const auth = useAuthStore()
    auth.accessToken = 'token-1'
    auth.userId = 1
    auth.ready = true

    const refreshPromise = interview.refresh()
    auth.logout()
    expect(interview.session).toBeNull()
    expect(interview.loaded).toBe(false)

    pending.resolve(sessionA)
    await expect(refreshPromise).resolves.toBeNull()
    expect(interview.session).toBeNull()
    expect(interview.loaded).toBe(false)
  })

  it('does not expose user A session to user B before B refreshes', async () => {
    vi.mocked(fetchCurrentInterview).mockResolvedValueOnce(sessionA)
    const interview = useCurrentInterviewStore()
    const auth = useAuthStore()
    auth.accessToken = 'token-1'
    auth.userId = 1
    auth.identityStatus = IdentityStatus.RESOLVED
    auth.ready = true
    await interview.refresh()
    expect(interview.session?.sessionId).toBe(11)

    auth.logout()
    writeAuthCredential(credential(2))
    auth.accessToken = 'token-2'
    auth.userId = 2
    auth.identityStatus = IdentityStatus.RESOLVED
    expect(interview.session).toBeNull()
    expect(interview.loaded).toBe(false)
    expect(interview.session?.sessionId).not.toBe(11)

    vi.mocked(fetchCurrentInterview).mockResolvedValueOnce(sessionB)
    await interview.refresh()
    expect(interview.session?.sessionId).toBe(22)
  })
})

describe('currentInterview single-flight and mutation adoption', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(fetchCurrentInterview).mockReset()
  })

  it('shares one same-epoch refresh and resolves both callers with the same result', async () => {
    const pending = deferred<InterviewSessionResponse | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pending.promise)
    const interview = useCurrentInterviewStore()

    const first = interview.refresh()
    const second = interview.refresh()
    expect(fetchCurrentInterview).toHaveBeenCalledTimes(1)

    pending.resolve(sessionA)
    await expect(first).resolves.toEqual(sessionA)
    await expect(second).resolves.toEqual(sessionA)
    expect(interview.session?.sessionId).toBe(11)
    expect(fetchCurrentInterview).toHaveBeenCalledTimes(1)
  })

  it('ignores a pending refresh after invalidate and starts a new request afterwards', async () => {
    const pending = deferred<InterviewSessionResponse | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValueOnce(pending.promise)
    const interview = useCurrentInterviewStore()

    const first = interview.refresh()
    interview.invalidate()
    expect(interview.session).toBeNull()
    expect(interview.loaded).toBe(false)

    vi.mocked(fetchCurrentInterview).mockResolvedValueOnce(sessionB)
    const second = interview.refresh()
    expect(fetchCurrentInterview).toHaveBeenCalledTimes(2)

    pending.resolve(sessionA)
    await expect(first).resolves.toBeNull()
    await expect(second).resolves.toEqual(sessionB)
    expect(interview.session?.sessionId).toBe(22)
  })

  it('keeps an adopted session when an older GET current returns later', async () => {
    const pending = deferred<InterviewSessionResponse | null>()
    vi.mocked(fetchCurrentInterview).mockReturnValue(pending.promise)
    const interview = useCurrentInterviewStore()

    const refreshPromise = interview.refresh()
    interview.adoptCreatedSession(sessionB)
    expect(interview.session?.sessionId).toBe(22)
    expect(interview.loaded).toBe(true)
    expect(interview.loading).toBe(false)
    expect(interview.error).toBeNull()

    pending.resolve(null)
    await expect(refreshPromise).resolves.toBeNull()
    expect(interview.session?.sessionId).toBe(22)
    expect(interview.loaded).toBe(true)
  })
})
