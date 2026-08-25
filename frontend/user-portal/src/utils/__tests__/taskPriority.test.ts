import { describe, expect, it } from 'vitest'

import { taskPriorityBand, taskPriorityCssClass } from '../taskPriority'

describe('taskPriorityBand', () => {
  it('maps Flowable numeric strings onto the same bands the High filter uses', () => {
    expect(taskPriorityBand('50')).toBe('HIGH')
    expect(taskPriorityBand('74')).toBe('HIGH')
    expect(taskPriorityBand('75')).toBe('URGENT')
    expect(taskPriorityBand('80')).toBe('URGENT')
    expect(taskPriorityBand('25')).toBe('NORMAL')
    expect(taskPriorityBand('49')).toBe('NORMAL')
    expect(taskPriorityBand('24')).toBe('LOW')
    expect(taskPriorityBand('0')).toBe('LOW')
  })

  it('maps numbers the same way as numeric strings', () => {
    expect(taskPriorityBand(50)).toBe('HIGH')
    expect(taskPriorityBand(0)).toBe('LOW')
    expect(taskPriorityBand(75)).toBe('URGENT')
  })

  it('keeps named labels', () => {
    expect(taskPriorityBand('HIGH')).toBe('HIGH')
    expect(taskPriorityBand('high')).toBe('HIGH')
    expect(taskPriorityBand('NORMAL')).toBe('NORMAL')
  })

  it('treats blank and unknown values as NORMAL', () => {
    expect(taskPriorityBand(undefined)).toBe('NORMAL')
    expect(taskPriorityBand(null)).toBe('NORMAL')
    expect(taskPriorityBand('')).toBe('NORMAL')
    expect(taskPriorityBand('  ')).toBe('NORMAL')
    expect(taskPriorityBand('bogus')).toBe('NORMAL')
  })

  it('exposes the CSS class as the lowercase band', () => {
    expect(taskPriorityCssClass('50')).toBe('high')
    expect(taskPriorityCssClass(80)).toBe('urgent')
  })
})
