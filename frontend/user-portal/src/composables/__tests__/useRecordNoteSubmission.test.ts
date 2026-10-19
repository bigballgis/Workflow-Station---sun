import { describe, expect, it } from 'vitest'
import { useRecordNoteSubmission } from '../useRecordNoteSubmission'

describe('Record Note submission identity', () => {
  it('preserves a key on retry and rotates it on edit or a new editor session', () => {
    const submission = useRecordNoteSubmission()
    const first = submission.keyFor('same-content')
    expect(submission.keyFor('same-content')).toBe(first)
    const edited = submission.keyFor('edited-content')
    expect(edited).not.toBe(first)
    expect(submission.keyFor('edited-content')).toBe(edited)
    submission.reset()
    expect(submission.keyFor('edited-content')).not.toBe(edited)
  })
})
