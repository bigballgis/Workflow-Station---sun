import { describe, expect, it } from 'vitest'
import { resolveEndEventDiagramStatus } from '../endEventDiagramStatus'

describe('resolveEndEventDiagramStatus', () => {
  it('marks the taken end event completed and leaves the other pending', () => {
    const history = [
      { nodeId: 'EndEvent_Sent', nodeName: 'Sent', status: 'completed' as const },
    ]
    expect(resolveEndEventDiagramStatus(history, 'EndEvent_Sent', 'Sent')).toBe('completed')
    expect(resolveEndEventDiagramStatus(history, 'EndEvent_Skipped', 'Skipped')).toBe('pending')
  })
})
