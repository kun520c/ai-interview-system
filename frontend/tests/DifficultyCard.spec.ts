import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import DifficultyCard from '@/features/interview/components/DifficultyCard.vue'
import { QuestionDifficulty } from '@/types/enums'

describe('DifficultyCard', () => {
  it.each([
    [QuestionDifficulty.EASY, '基础巩固'],
    [QuestionDifficulty.MEDIUM, '综合进阶'],
    [QuestionDifficulty.HARD, '深度挑战'],
  ])('adds a readable name for %s while keeping the API value and no default selection', (difficulty, label) => {
    const wrapper = mount(DifficultyCard, { props: { difficulty, selected: false } })
    expect(wrapper.get('.difficulty-title').text()).toBe(label)
    expect(wrapper.get('input').element.value).toBe(difficulty)
    expect((wrapper.get('input').element as HTMLInputElement).checked).toBe(false)
  })

  it('keeps a native radio in the keyboard path', () => {
    const wrapper = mount(DifficultyCard, {
      props: { difficulty: QuestionDifficulty.MEDIUM, selected: false },
    })
    const input = wrapper.get('input')
    expect(input.attributes('type')).toBe('radio')
    expect(input.attributes('tabindex')).toBeUndefined()
    expect((input.element as HTMLInputElement).disabled).toBe(false)
  })

  it('declares a visible :focus-visible treatment on the card', () => {
    const source = readFileSync(
      resolve('src/features/interview/components/DifficultyCard.vue'),
      'utf8',
    )
    expect(source).toContain(':has(input:focus-visible)')
    expect(source).toContain('box-shadow: 0 0 0 3px var(--primary-soft)')
  })
})
