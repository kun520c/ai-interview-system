import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import InterviewEvaluationPane from '@/features/interview/components/InterviewEvaluationPane.vue'
import { evaluation } from './helpers/interviewFixtures'

describe('InterviewEvaluationPane heading hierarchy', () => {
  it('keeps session subheadings below h2', () => {
    const wrapper = mount(InterviewEvaluationPane, {
      props: {
        evaluation: evaluation(),
        headingTag: 'h2',
      },
    })
    expect(wrapper.get('.evaluation-heading').element.tagName).toBe('H2')
    expect(wrapper.get('.feedback-title').element.tagName).toBe('H3')
  })

  it('does not invert headings when the parent evaluation heading is h4', () => {
    const wrapper = mount(InterviewEvaluationPane, {
      props: {
        evaluation: evaluation(),
        headingTag: 'h4',
        headingId: 'timeline-panel-88-evaluation',
      },
    })
    expect(wrapper.get('.evaluation-heading').element.tagName).toBe('H4')
    expect(wrapper.find('.feedback-block h3').exists()).toBe(false)
    expect(wrapper.get('.feedback-title').element.tagName).toBe('H5')
    expect(wrapper.text()).toContain('做得好的')
    expect(wrapper.text()).toContain('需要补充')
    expect(wrapper.text()).toContain('参考修正')
  })
})
