import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import InterviewEvaluationPane from '@/features/interview/components/InterviewEvaluationPane.vue'
import { evaluation } from './helpers/interviewFixtures'

describe('InterviewEvaluationPane heading hierarchy', () => {
  it('renders the actual five scores on the documented 20-point scale', () => {
    const wrapper = mount(InterviewEvaluationPane, {
      props: { evaluation: evaluation({ correctnessScore: 0, completenessScore: 20 }) },
    })
    const dimensions = wrapper.findAll('.score-row li')
    expect(dimensions).toHaveLength(5)
    expect(dimensions[0]!.get('.score-value').text()).toBe('0')
    expect(dimensions[0]!.get('.score-track > span').attributes('style')).toBe('width: 0%;')
    expect(dimensions[1]!.get('.score-value').text()).toBe('20')
    expect(dimensions[1]!.get('.score-track > span').attributes('style')).toBe('width: 100%;')
    // The UI displays the backend total; it never recalculates a business score.
    expect(wrapper.get('.total-number').text()).toBe('70')
    expect(wrapper.get('.score-track').attributes('aria-hidden')).toBe('true')
  })

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
