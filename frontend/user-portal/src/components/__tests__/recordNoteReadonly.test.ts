import { describe, expect, it } from 'vitest'
import { extractFieldsRecursive } from '../formRendererHelpers/formRendererRuleParsing'

/**
 * The Record Note panel means different things on the two surfaces it appears on.
 *
 * On a **request form** a note is an audit opinion, so the server decides: only an audit grant
 * held by the user's active role (or SYS_ADMIN) may write, and this designer switch has no say.
 * On a **To Do form** a note is a comment by whoever works the task, so the task's handler may
 * write by default and the Developer Workstation Readonly switch is what turns that off.
 *
 * The switch is therefore opt-in: forms designed before it existed, and forms where the designer
 * left it alone, must reach the portal writable.
 */
describe('recordNote readonly extraction', () => {
  function recordNoteRule(props: Record<string, unknown>) {
    return [{ type: 'recordNote', props }] as unknown as Record<string, unknown>[]
  }

  it('defaults to false when the designer never set the switch (legacy forms)', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'TABLE' }))
    expect(field._recordNote?.readonly).toBe(false)
  })

  it('stays false when the switch is explicitly off', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'TABLE', readonly: false }))
    expect(field._recordNote?.readonly).toBe(false)
  })

  it('is enabled only by an explicit true', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'RECORD', readonly: true }))
    expect(field._recordNote?.readonly).toBe(true)
  })

  it('does not disturb the other note switches', () => {
    const [field] = extractFieldsRecursive(recordNoteRule({ scope: 'TABLE', readonly: true }))
    expect(field._recordNote?.allowAttachment).toBe(true)
    expect(field._recordNote?.allowDelete).toBe(false)
    expect(field._recordNote?.pageSize).toBe(5)
  })

  it('survives nesting inside designer layout containers', () => {
    const rules = [
      {
        type: 'fcRow',
        children: [
          {
            type: 'fcCol',
            col: { span: 24 },
            children: [{ type: 'recordNote', props: { scope: 'TABLE', readonly: true } }],
          },
        ],
      },
    ] as unknown as Record<string, unknown>[]
    const notes = extractFieldsRecursive(rules)
      .flatMap(f => f.children ?? [])
      .flatMap(c => c.children ?? [])
      .filter(f => f.type === 'recordNote')
    expect(notes).toHaveLength(1)
    expect(notes[0]._recordNote?.readonly).toBe(true)
  })
})

/**
 * The rule FormRendererFields applies when binding the panel's `readonly` prop. Kept in sync with
 * `recordNoteReadonly()` there: the flag only bites on a task form, and `taskId` — not
 * `viewContext` — is what identifies one. `viewContext` defaults to 'assigneeTodo' in
 * FormRenderer, so a surface that simply never passed it would otherwise masquerade as To Do and
 * wrongly inherit the switch.
 */
describe('recordNote readonly applies to To Do forms only', () => {
  function isReadonly(taskId: string | null | undefined, readonly: boolean | undefined): boolean {
    return taskId != null && readonly === true
  }

  it('seals the panel on a task form when the designer switched it on', () => {
    expect(isReadonly('task-1', true)).toBe(true)
  })

  it('leaves the panel writable on a task form by default', () => {
    expect(isReadonly('task-1', false)).toBe(false)
    expect(isReadonly('task-1', undefined)).toBe(false)
  })

  it('ignores the switch off a task form, where audit roles govern instead', () => {
    expect(isReadonly(null, true)).toBe(false)
    expect(isReadonly(undefined, true)).toBe(false)
  })
})
