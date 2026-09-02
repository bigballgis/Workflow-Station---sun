import { describe, expect, it } from 'vitest'
import {
  EMAIL_VAR_GROUP_LOOKUP,
  EMAIL_VAR_GROUP_SUBTABLES,
  type EmailVariableGroup,
} from '@/composables/email/useEmailTemplateVariables'
import {
  appendJuelTokenToExpression,
  filterGroupsForSendTaskRecipient,
  SEND_TASK_INITIATOR_LABEL,
  SEND_TASK_PROCESS_VAR_GROUP,
} from '../sendTaskJuelVariables'

describe('filterGroupsForSendTaskRecipient', () => {
  it('keeps main-table tokens and adds process group', () => {
    const input: EmailVariableGroup[] = [
      {
        label: 'Main',
        options: [{ token: '${assigneeEmail}', label: 'Email (assigneeEmail)' }],
      },
      {
        label: EMAIL_VAR_GROUP_SUBTABLES,
        options: [{ token: '${subTableHtml:1:col=a}', label: 'Sub' }],
      },
      {
        label: `${EMAIL_VAR_GROUP_LOOKUP}:User`,
        options: [{ token: '${lookupField:u:mail}', label: 'Lookup mail' }],
      },
      {
        label: 'Lines (#2)',
        options: [{ token: '${subTableField:2:qty}', label: 'qty' }],
      },
    ]
    const out = filterGroupsForSendTaskRecipient(input)
    expect(out[0]?.label).toBe(SEND_TASK_PROCESS_VAR_GROUP)
    expect(out[0]?.options.some(o => o.token === '${initiator}')).toBe(true)
    expect(out[0]?.options.some(o => o.label === SEND_TASK_INITIATOR_LABEL)).toBe(true)
    expect(out.some(g => g.options.some(o => o.token === '${assigneeEmail}'))).toBe(true)
    expect(out.some(g => g.options.some(o => o.token.includes('subTable')))).toBe(false)
    expect(out.some(g => g.options.some(o => o.token.includes('lookupField')))).toBe(false)
  })

  it('returns only the process group when input is empty', () => {
    const out = filterGroupsForSendTaskRecipient([])
    expect(out).toHaveLength(1)
    expect(out[0]?.label).toBe(SEND_TASK_PROCESS_VAR_GROUP)
    expect(out[0]?.options).toEqual([{ token: '${initiator}', label: SEND_TASK_INITIATOR_LABEL }])
  })

  it('drops non-simple JUEL tokens from main-table groups', () => {
    const out = filterGroupsForSendTaskRecipient([
      {
        label: 'Main',
        options: [
          { token: '${assigneeEmail}', label: 'Email' },
          { token: '${subTableField:1:qty}', label: 'Sub qty' },
        ],
      },
    ])
    const mainOpts = out.find(g => g.label === 'Main')?.options ?? []
    expect(mainOpts.map(o => o.token)).toEqual(['${assigneeEmail}'])
  })
})

describe('appendJuelTokenToExpression', () => {
  it('appends to empty string', () => {
    expect(appendJuelTokenToExpression('', '${a}')).toBe('${a}')
  })

  it('appends with a semicolon', () => {
    expect(appendJuelTokenToExpression('a@x.com', '${b}')).toBe('a@x.com; ${b}')
  })

  it('appends after comma without extra comma', () => {
    expect(appendJuelTokenToExpression('a@x.com,', '${b}')).toBe('a@x.com, ${b}')
  })

  it('appends after semicolon without extra semicolon', () => {
    expect(appendJuelTokenToExpression('a@x.com;', '${b}')).toBe('a@x.com; ${b}')
  })

  it('ignores blank tokens', () => {
    expect(appendJuelTokenToExpression('a@x.com', '   ')).toBe('a@x.com')
  })
})
