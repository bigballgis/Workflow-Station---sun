import { describe, it, expect } from 'vitest'
import { resolveMiLinkIsolateInlineRow } from '../inlineFormBelowTableRuntime'

describe('resolveMiLinkIsolateInlineRow', () => {
  const matchByFk = (rows: unknown[], parentId: string | number) =>
    rows.findIndex(r => (r as { sub_task_id?: string }).sub_task_id === String(parentId))

  it('returns single nested row', () => {
    const rows = [{ name: '1111', sub_task_id: 'Test-000057' }]
    expect(resolveMiLinkIsolateInlineRow(rows, 'Test-000057', matchByFk, () => null)).toEqual({
      name: '1111',
      sub_task_id: 'Test-000057',
    })
  })

  it('returns empty object when no nested rows exist', () => {
    expect(resolveMiLinkIsolateInlineRow([], 'Test-000057', matchByFk, () => null)).toEqual({})
  })

  it('picks FK-aligned row when multiple nested children share parent sub_task_id', () => {
    const rows = [
      { id: 'a', name: '1111', age: 'ii', sub_task_id: 'Test-000057' },
      { id: 'b', name: '222', sub_task_id: 'Test-000057' },
      { id: 'c', name: '33', sub_task_id: 'Test-000057' },
    ]
    const picked = resolveMiLinkIsolateInlineRow(rows, 'Test-000057', matchByFk, () => rows[0])
    expect(picked).toEqual(rows[0])
  })

  it('falls back to preferred row when FK index misses', () => {
    const rows = [
      { id: 'a', name: 'thin' },
      { id: 'b', name: '1111', age: 'ii', sex: true },
    ]
    const picked = resolveMiLinkIsolateInlineRow(
      rows,
      'Test-000057',
      () => -1,
      list => list[1],
    )
    expect(picked).toEqual(rows[1])
  })
})
