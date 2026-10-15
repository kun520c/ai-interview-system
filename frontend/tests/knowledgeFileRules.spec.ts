import { describe, expect, it } from 'vitest'

import { validateKnowledgeFile } from '@/features/admin/knowledge/utils/knowledgeFileRules'
import { KNOWLEDGE_MAX_FILE_BYTES } from '@/features/admin/knowledge/copy/knowledgeAdminCopy'
import { buildKnowledgeUploadForm } from '@/features/admin/knowledge/api/knowledgeAdminApi'
import { QuestionCategory } from '@/types/enums'

function makeFile(name: string, size = 12): File {
  return new File([new Uint8Array(size)], name, { type: 'text/plain' })
}

describe('knowledge file rules', () => {
  it('rejects missing, empty, oversized and illegal extensions', () => {
    expect(validateKnowledgeFile(null)).toBe('请选择文件')
    expect(validateKnowledgeFile(makeFile('a.md', 0))).toBe('不能为空文件')
    expect(validateKnowledgeFile(makeFile('a.md', KNOWLEDGE_MAX_FILE_BYTES + 1))).toBe(
      '上传文件不能超过5MB',
    )
    expect(validateKnowledgeFile(makeFile('notes.pdf'))).toBe('当前只支持 Markdown 和 TXT 文件')
    expect(validateKnowledgeFile(makeFile('notes.md'))).toBeNull()
    expect(validateKnowledgeFile(makeFile('notes.markdown'))).toBeNull()
    expect(validateKnowledgeFile(makeFile('notes.txt'))).toBeNull()
  })

  it('builds the exact multipart fields', () => {
    const file = makeFile('spring.md')
    const form = buildKnowledgeUploadForm({
      file,
      title: 'Spring',
      category: QuestionCategory.SPRING,
      source: 'local',
    })
    expect(form.get('file')).toBe(file)
    expect(form.get('title')).toBe('Spring')
    expect(form.get('category')).toBe('SPRING')
    expect(form.get('source')).toBe('local')
    expect([...form.keys()].sort()).toEqual(['category', 'file', 'source', 'title'])
  })
})
