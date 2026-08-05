import { describe, expect, it } from 'vitest'
import {
  buildSendTaskCompletedLookups,
  normSendTaskLabel,
  resolveSendTaskDiagramStatus,
} from '../sendTaskDiagramStatus'

describe('sendTaskDiagramStatus', () => {
  it('marks completed when history action is send', () => {
    const status = resolveSendTaskDiagramStatus(
      [{ nodeId: 'SendTask_1', nodeName: 'Send Task', status: 'completed', action: 'send' }],
      'SendTask_1',
      'Send Task',
    )
    expect(status).toBe('completed')
  })

  it('matches node names with normalized whitespace', () => {
    const status = resolveSendTaskDiagramStatus(
      [{ nodeId: '', nodeName: 'Send  Task', status: 'completed', action: 'send' }],
      'SendTask_1',
      'Send Task',
    )
    expect(status).toBe('completed')
  })

  it('uses prebuilt lookups for activity id match', () => {
    const lookups = buildSendTaskCompletedLookups([
      { nodeId: 'Activity_Email', nodeName: '', status: 'completed' },
    ])
    expect(
      resolveSendTaskDiagramStatus([], 'Activity_Email', 'Notify', lookups),
    ).toBe('completed')
  })

  it('returns pending when no send history exists', () => {
    expect(
      resolveSendTaskDiagramStatus(
        [{ nodeId: 'UserTask_1', nodeName: 'Approve', status: 'completed' }],
        'SendTask_1',
        'Send Task',
      ),
    ).toBe('pending')
  })

  it('normalizes labels consistently', () => {
    expect(normSendTaskLabel('  Send   Task  ')).toBe('Send Task')
  })
})
