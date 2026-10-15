import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, ErrorKind } from '@/api/apiError'
import { resetSessionScopeForTests } from '@/app/session/sessionScope'
import {
  processKnowledgeDocument,
  uploadKnowledgeDocument,
} from '@/features/admin/knowledge/api/knowledgeAdminApi'
import {
  persistKnowledgeRecent,
  readKnowledgeRecent,
} from '@/features/admin/knowledge/storage/knowledgeRecentStorage'
import { fetchCurrentInterview } from '@/features/interview/api/interviewApi'
import { fetchInterviewHistory } from '@/features/history/api/historyApi'
import { KnowledgeProcessingStatus, QuestionCategory, UserRole } from '@/types/enums'
import { authenticate, mountApp } from './helpers/mountApp'
import { emptyHistoryPage } from './helpers/interviewFixtures'
import { localKnowledgeDoc, uploadedKnowledge } from './helpers/adminFixtures'
import { deferred } from './helpers/deferred'

vi.mock('@/features/auth/api/authApi', () => ({
  login: vi.fn(),
  registerAccount: vi.fn(),
}))

vi.mock('@/features/auth/api/userApi', () => ({
  fetchCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))

vi.mock('@/features/interview/api/interviewApi', () => ({
  fetchCurrentInterview: vi.fn(),
  createInterview: vi.fn(),
  createOrResumeInterview: vi.fn(),
  submitInterviewAnswer: vi.fn(),
  fetchInterviewDetail: vi.fn(),
  fetchInterviewReport: vi.fn(),
  generateInterviewReport: vi.fn(),
}))

vi.mock('@/features/history/api/historyApi', () => ({
  fetchInterviewHistory: vi.fn(),
}))

vi.mock('@/features/admin/knowledge/api/knowledgeAdminApi', () => ({
  uploadKnowledgeDocument: vi.fn(),
  processKnowledgeDocument: vi.fn(),
  buildKnowledgeUploadForm: vi.fn(),
}))

function makeFile(name = 'spring.md', size = 24): File {
  return new File([new Uint8Array(size)], name, { type: 'text/markdown' })
}

async function fillUploadForm(
  wrapper: Awaited<ReturnType<typeof mountApp>>['wrapper'],
  file = makeFile(),
) {
  await wrapper.get('#knowledge-title').setValue('Spring Bean')
  const input = wrapper.get('#knowledge-file')
  Object.defineProperty(input.element, 'files', {
    value: [file],
    configurable: true,
  })
  await input.trigger('change')
}

describe('Admin knowledge', () => {
  beforeEach(() => {
    resetSessionScopeForTests()
    localStorage.clear()
    vi.mocked(fetchCurrentInterview).mockReset()
    vi.mocked(fetchInterviewHistory).mockReset()
    vi.mocked(uploadKnowledgeDocument).mockReset()
    vi.mocked(processKnowledgeDocument).mockReset()
    vi.mocked(fetchCurrentInterview).mockResolvedValue(null)
    vi.mocked(fetchInterviewHistory).mockResolvedValue(emptyHistoryPage())
  })

  it('uploads without auto-processing and stores a user-scoped recent row', async () => {
    vi.mocked(uploadKnowledgeDocument).mockResolvedValue(uploadedKnowledge())
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper)
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(uploadKnowledgeDocument).toHaveBeenCalledTimes(1)
    expect(processKnowledgeDocument).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('上传成功 / 尚未处理')
    expect(wrapper.text()).toContain('本机最近上传')
    expect(wrapper.text()).not.toContain('知识库文档')
    expect(wrapper.text()).not.toContain('全部文档')
    expect(readKnowledgeRecent(9)[0]?.documentId).toBe(15)
  })

  it('rejects an oversized file on the client', async () => {
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper, makeFile('spring.md', 5 * 1024 * 1024 + 1))
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(uploadKnowledgeDocument).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('上传文件不能超过5MB')
  })

  it('rejects a bad extension and an empty file', async () => {
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper, makeFile('notes.pdf'))
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('当前只支持 Markdown 和 TXT 文件')
    await fillUploadForm(wrapper, makeFile('notes.md', 0))
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('不能为空文件')
    expect(uploadKnowledgeDocument).not.toHaveBeenCalled()
  })

  it('shows the 413 oversize message', async () => {
    vi.mocked(uploadKnowledgeDocument).mockRejectedValue(
      new ApiError({
        httpStatus: 413,
        code: 413,
        message: '上传文件不能超过5MB',
        kind: ErrorKind.PAYLOAD_TOO_LARGE,
      }),
    )
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper)
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('上传文件不能超过5MB')
  })

  it('does not auto-retry an uncertain upload', async () => {
    vi.mocked(uploadKnowledgeDocument).mockRejectedValue(
      new ApiError({ httpStatus: 0, code: 0, message: '网络异常，请稍后重试', kind: ErrorKind.NETWORK }),
    )
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper)
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('上传结果无法确认')
    expect(uploadKnowledgeDocument).toHaveBeenCalledTimes(1)
  })

  it('isolates local recent documents across users', async () => {
    persistKnowledgeRecent(3, [localKnowledgeDoc({ title: 'UserA doc' })])
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    expect(wrapper.text()).not.toContain('UserA doc')
    expect(wrapper.text()).toContain('还没有本机最近上传')
  })

  it('marks process success as READY and does not expose rebuild', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc()])
    vi.mocked(processKnowledgeDocument).mockResolvedValue(null)
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    expect(wrapper.text()).toContain('开始处理')
    await wrapper.findAll('button').find((button) => button.text().includes('开始处理'))?.trigger('click')
    await flushPromises()
    expect(processKnowledgeDocument).toHaveBeenCalledWith(15)
    expect(wrapper.text()).toContain('已处理完成')
    expect(wrapper.findAll('button').some((button) => button.text() === '开始处理')).toBe(false)
    expect(wrapper.text()).not.toContain('重建')
    expect(wrapper.text()).not.toContain('重新向量化')
    expect(readKnowledgeRecent(9)[0]?.processingStatus).toBe(KnowledgeProcessingStatus.READY)
  })

  it('maps 409 already-ready to READY', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc()])
    vi.mocked(processKnowledgeDocument).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: '文档已处理完成，不能重复处理',
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await wrapper.findAll('button').find((button) => button.text().includes('开始处理'))?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已处理完成')
    expect(readKnowledgeRecent(9)[0]?.processingStatus).toBe(KnowledgeProcessingStatus.READY)
  })

  it('maps 409 processing to PROCESSING without another POST', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc()])
    vi.mocked(processKnowledgeDocument).mockRejectedValue(
      new ApiError({
        httpStatus: 409,
        code: 409,
        message: '文档正在处理中，请稍后重试',
        kind: ErrorKind.CONFLICT,
      }),
    )
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await wrapper.findAll('button').find((button) => button.text().includes('开始处理'))?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('处理中')
    expect(processKnowledgeDocument).toHaveBeenCalledTimes(1)
    expect(readKnowledgeRecent(9)[0]?.processingStatus).toBe(KnowledgeProcessingStatus.PROCESSING)
  })

  it('keeps the same documentId after 502 and retries process not upload', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc()])
    vi.mocked(processKnowledgeDocument).mockRejectedValue(
      new ApiError({
        httpStatus: 502,
        code: 502,
        message: '外部服务暂时不可用',
        kind: ErrorKind.EXTERNAL_SERVICE,
      }),
    )
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await wrapper.findAll('button').find((button) => button.text().includes('开始处理'))?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('处理结果待确认')
    expect(uploadKnowledgeDocument).not.toHaveBeenCalled()
    vi.mocked(processKnowledgeDocument).mockResolvedValue(null)
    await wrapper.findAll('button').find((button) => button.text().includes('按同一文档继续处理'))?.trigger('click')
    await flushPromises()
    expect(processKnowledgeDocument).toHaveBeenLastCalledWith(15)
    expect(uploadKnowledgeDocument).not.toHaveBeenCalled()
  })

  it('sends only one process request when double-clicked', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc()])
    const pending = deferred<null>()
    vi.mocked(processKnowledgeDocument).mockReturnValue(pending.promise)
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    const button = wrapper.findAll('button').find((item) => item.text().includes('开始处理'))
    await button?.trigger('click')
    await button?.trigger('click')
    pending.resolve(null)
    await flushPromises()
    expect(processKnowledgeDocument).toHaveBeenCalledTimes(1)
  })

  it('does not persist a pending upload into the next user recent list', async () => {
    const pending = deferred<ReturnType<typeof uploadedKnowledge>>()
    vi.mocked(uploadKnowledgeDocument).mockReturnValue(pending.promise)
    const { wrapper, router, auth } = await mountApp('/admin/knowledge', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await fillUploadForm(wrapper)
    await wrapper.get('form.upload-form').trigger('submit')
    auth.logout()
    authenticate(auth, UserRole.ADMIN, {
      userId: 11,
      account: 'admin-b',
      username: '管理员乙',
      email: 'admin-b@example.com',
    })
    await router.push('/admin/knowledge')
    await flushPromises()
    pending.resolve(uploadedKnowledge())
    await flushPromises()
    expect(readKnowledgeRecent(11)).toEqual([])
    expect(readKnowledgeRecent(9)[0]?.documentId).toBe(15)
    expect(wrapper.findAll('.recent-item')).toHaveLength(0)
    expect(wrapper.text()).toContain('还没有本机最近上传')
  })

  it('does not apply a pending process READY result to the next user', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc({ title: 'UserA process' })])
    const pending = deferred<null>()
    vi.mocked(processKnowledgeDocument).mockReturnValue(pending.promise)
    const { wrapper, router, auth } = await mountApp('/admin/knowledge', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await wrapper.findAll('button').find((button) => button.text().includes('开始处理'))?.trigger('click')
    auth.logout()
    authenticate(auth, UserRole.ADMIN, {
      userId: 11,
      account: 'admin-b',
      username: '管理员乙',
      email: 'admin-b@example.com',
    })
    await router.push('/admin/knowledge')
    await flushPromises()
    pending.resolve(null)
    await flushPromises()
    expect(wrapper.text()).not.toContain('UserA process')
    expect(readKnowledgeRecent(11)).toEqual([])
    expect(readKnowledgeRecent(9)[0]?.processingStatus).toBe(KnowledgeProcessingStatus.READY)
  })

  it('blocks upload when local recent storage cannot be written', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('The quota has been exceeded.', 'QuotaExceededError')
    })
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper)
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(uploadKnowledgeDocument).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('本机恢复存储不可用，暂时无法安全上传文档。')
  })

  it('keeps a known uploaded documentId when persist fails after HTTP 200', async () => {
    const originalSetItem = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key, value) {
      if (String(key) === 'admin.knowledge.recent.9') {
        throw new DOMException('The quota has been exceeded.', 'QuotaExceededError')
      }
      return originalSetItem.call(this, key, value)
    })
    vi.mocked(uploadKnowledgeDocument).mockResolvedValue(uploadedKnowledge())
    vi.mocked(processKnowledgeDocument).mockResolvedValue(null)
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await fillUploadForm(wrapper)
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('文档已上传成功，但本机恢复记录保存失败')
    expect(wrapper.text()).not.toContain('上传结果无法确认')
    expect(wrapper.find('[role="alert"]').text()).toContain('文档已上传成功')
    expect(readKnowledgeRecent(9)).toEqual([])
    expect(wrapper.text()).toContain('Spring Bean')
    const processButton = wrapper.findAll('button').find((button) => button.text().includes('开始处理'))
    expect(processButton).toBeDefined()
    await processButton?.trigger('click')
    await flushPromises()
    expect(uploadKnowledgeDocument).toHaveBeenCalledTimes(1)
    expect(processKnowledgeDocument).toHaveBeenCalledWith(15)
  })

  it('keeps memory READY when process 200 persist fails', async () => {
    persistKnowledgeRecent(9, [localKnowledgeDoc()])
    const originalSetItem = Storage.prototype.setItem
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function (this: Storage, key, value) {
      if (String(key) === 'admin.knowledge.recent.9') {
        throw new DOMException('The quota has been exceeded.', 'QuotaExceededError')
      }
      return originalSetItem.call(this, key, value)
    })
    vi.mocked(processKnowledgeDocument).mockResolvedValue(null)
    const { wrapper } = await mountApp('/admin/knowledge', (store) => authenticate(store, UserRole.ADMIN))
    await wrapper.findAll('button').find((button) => button.text().includes('开始处理'))?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已处理完成')
    expect(wrapper.text()).toContain('处理已完成，但本机状态保存失败。')
    expect(wrapper.text()).not.toContain('处理结果待确认')
    expect(wrapper.findAll('button').some((button) => button.text() === '开始处理')).toBe(false)
  })

  it('persists the captured upload category even if the reactive category later changes', async () => {
    const pending = deferred<ReturnType<typeof uploadedKnowledge>>()
    vi.mocked(uploadKnowledgeDocument).mockReturnValue(pending.promise)
    const { wrapper, router, auth } = await mountApp('/admin/knowledge', (store) =>
      authenticate(store, UserRole.ADMIN),
    )
    await fillUploadForm(wrapper)
    await wrapper.get('#knowledge-category').setValue(QuestionCategory.SPRING)
    await wrapper.get('form.upload-form').trigger('submit')
    await flushPromises()
    await wrapper.get('#knowledge-category').setValue(QuestionCategory.REDIS)
    auth.logout()
    authenticate(auth, UserRole.ADMIN, {
      userId: 11,
      account: 'admin-b',
      username: '管理员乙',
      email: 'admin-b@example.com',
    })
    await router.push('/admin/knowledge')
    await flushPromises()
    pending.resolve(uploadedKnowledge())
    await flushPromises()
    expect(readKnowledgeRecent(9)[0]?.category).toBe(QuestionCategory.SPRING)
    expect(readKnowledgeRecent(11)).toEqual([])
  })
})
