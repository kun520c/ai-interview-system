import { describe, expect, it } from 'vitest'

import {
  buildKnowledgeRecentKey,
  persistKnowledgeRecent,
  probeKnowledgeRecentWritable,
  readKnowledgeRecent,
  upsertKnowledgeRecent,
} from '@/features/admin/knowledge/storage/knowledgeRecentStorage'
import { localKnowledgeDoc } from './helpers/adminFixtures'
import { createMemoryStorage } from './helpers/memoryStorage'
import { KnowledgeProcessingStatus, QuestionCategory } from '@/types/enums'

function createStorageWithSetItemError(inner = createMemoryStorage()): Storage {
  return {
    get length() {
      return inner.length
    },
    clear() {
      inner.clear()
    },
    getItem(key: string) {
      return inner.getItem(key)
    },
    key(index: number) {
      return inner.key(index)
    },
    removeItem(key: string) {
      inner.removeItem(key)
    },
    setItem() {
      throw new DOMException('The quota has been exceeded.', 'QuotaExceededError')
    },
  }
}

describe('knowledge recent storage', () => {
  it('namespaces by user id', () => {
    expect(buildKnowledgeRecentKey(9)).toBe('admin.knowledge.recent.9')
  })

  it('does not show user A documents to user B', () => {
    const storage = createMemoryStorage()
    upsertKnowledgeRecent(3, localKnowledgeDoc({ title: 'A-only' }), storage)
    expect(readKnowledgeRecent(3, storage)[0]?.title).toBe('A-only')
    expect(readKnowledgeRecent(9, storage)).toEqual([])
  })

  it('discards corrupt JSON safely', () => {
    const storage = createMemoryStorage()
    storage.setItem(buildKnowledgeRecentKey(3), '{not json')
    expect(readKnowledgeRecent(3, storage)).toEqual([])
    expect(storage.getItem(buildKnowledgeRecentKey(3))).toBeNull()
  })

  it('discards structurally invalid records', () => {
    const storage = createMemoryStorage()
    persistKnowledgeRecent(3, [{ documentId: -1, title: 'bad' } as never], storage)
    expect(readKnowledgeRecent(3, storage)).toEqual([])
  })

  it('keeps a valid document including category', () => {
    const storage = createMemoryStorage()
    const saved = upsertKnowledgeRecent(
      3,
      localKnowledgeDoc({
        category: QuestionCategory.MYSQL,
        processingStatus: KnowledgeProcessingStatus.UPLOADED,
      }),
      storage,
    )
    expect(saved.ok).toBe(true)
    expect(saved.documents[0]?.category).toBe(QuestionCategory.MYSQL)
  })

  it('returns a failed persist result instead of throwing on quota', () => {
    const storage = createStorageWithSetItemError()
    expect(() => persistKnowledgeRecent(3, [localKnowledgeDoc()], storage)).not.toThrow()
    const result = persistKnowledgeRecent(3, [localKnowledgeDoc()], storage)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error.reason).toBe('quota')
    }
    expect(result.documents[0]?.documentId).toBe(15)
  })

  it('does not throw when localStorage is unavailable', () => {
    expect(readKnowledgeRecent(3, null)).toEqual([])
    expect(persistKnowledgeRecent(3, [localKnowledgeDoc()], null).ok).toBe(false)
    expect(probeKnowledgeRecentWritable(3, null)).toBe(false)
  })

  it('does not throw when getItem is blocked', () => {
    const storage = createMemoryStorage()
    storage.getItem = () => {
      throw new DOMException('blocked', 'SecurityError')
    }
    expect(readKnowledgeRecent(3, storage)).toEqual([])
  })

  it('survives cleanup rewrite failure after discarding invalid records', () => {
    const inner = createMemoryStorage()
    inner.setItem(
      buildKnowledgeRecentKey(3),
      JSON.stringify([localKnowledgeDoc({ title: 'keep-me' }), { documentId: -1 }]),
    )
    const storage = createStorageWithSetItemError(inner)
    expect(() => readKnowledgeRecent(3, storage)).not.toThrow()
    expect(readKnowledgeRecent(3, storage)[0]?.title).toBe('keep-me')
  })

  it('probes writability without destroying the recent list', () => {
    const storage = createMemoryStorage()
    persistKnowledgeRecent(3, [localKnowledgeDoc({ title: 'keep' })], storage)
    expect(probeKnowledgeRecentWritable(3, storage)).toBe(true)
    expect(readKnowledgeRecent(3, storage)[0]?.title).toBe('keep')
    expect(storage.getItem(`${buildKnowledgeRecentKey(3)}.__probe`)).toBeNull()
  })

  it('reports a failed probe when setItem throws', () => {
    const storage = createStorageWithSetItemError()
    expect(probeKnowledgeRecentWritable(3, storage)).toBe(false)
  })
})
