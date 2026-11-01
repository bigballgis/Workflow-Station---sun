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


describe('end event identity', () => {
  it('still matches legacy history without an activity ID by its label', () => {
    expect(resolveEndEventDiagramStatus([{ nodeName: 'Finished', status: 'completed' }],
      'ourExit', 'Finished')).toBe('completed')
  })
  it('keeps an executed renamed event completed by its activity ID', () => {
    expect(resolveEndEventDiagramStatus([{ nodeId: 'ourExit', nodeName: 'Old label', status: 'completed' }],
      'ourExit', 'Renamed label')).toBe('completed')
  })

  it('does not confuse another activity with the same label', () => {
    const history = [{ nodeId: 'otherExit', nodeName: 'Finished', status: 'completed' }]
    expect(resolveEndEventDiagramStatus(history, 'ourExit', 'Finished')).toBe('pending')
  })
  it('prefers the exact activity over an earlier same-label event', () => {
    const history = [{ nodeId: 'otherExit', nodeName: 'Finished', status: 'completed' },
      { nodeId: 'ourExit', nodeName: 'Finished', status: 'rejected' }]
    expect(resolveEndEventDiagramStatus(history, 'ourExit', 'Finished')).toBe('rejected')
  })
})
