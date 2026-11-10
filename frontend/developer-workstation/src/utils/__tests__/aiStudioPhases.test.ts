import { describe, expect, it } from 'vitest'
import { AI_STUDIO_PHASES, firstUnconfirmedAiStudioPhase } from '@/utils/aiStudioDraft'

describe('AI Studio phases', () => {
  it('start with the requirements interview and end with validation', () => {
    expect(AI_STUDIO_PHASES[0]).toBe('REQUIREMENTS')
    expect(AI_STUDIO_PHASES[1]).toBe('PROCESS_DESIGN')
    expect(AI_STUDIO_PHASES[AI_STUDIO_PHASES.length - 1]).toBe('VALIDATION')
    expect(AI_STUDIO_PHASES).toHaveLength(12)
  })

  it('resumes at the requirements interview only while nothing is confirmed', () => {
    expect(firstUnconfirmedAiStudioPhase([])).toBe('REQUIREMENTS')
    expect(firstUnconfirmedAiStudioPhase(['REQUIREMENTS'])).toBe('PROCESS_DESIGN')
    // 跳过了需求访谈、已经确认过设计阶段：不拉回去
    expect(firstUnconfirmedAiStudioPhase(['PROCESS_DESIGN', 'TABLE_DESIGN'])).toBe('FORM_DESIGN')
    expect(firstUnconfirmedAiStudioPhase([...AI_STUDIO_PHASES])).toBeUndefined()
  })
})
