import { describe, expect, it } from 'vitest'
import {
  findSubTableRowByMiExpansionId,
  findMiIsolatedParentRow,
  rowMatchesMiExpansionId,
  scrubMiCorruptLinkChildRowsForParent,
  pickMiLinkChildRowsForParent
} from '../shared'

describe('linkFormMiIsolation helpers', () => {
  it('rowMatchesMiExpansionId matches id_idw', () => {
    expect(rowMatchesMiExpansionId({ id_idw: 88, name: '8' }, 88)).toBe(true)
    expect(rowMatchesMiExpansionId({ id_idw: 88 }, 44)).toBe(false)
  })

  it('findSubTableRowByMiExpansionId returns the participant row', () => {
    const rows = [
      { id: '44', id_idw: 245, assignee: { id: 'other' } },
      { name: '8', id_idw: 88, assignee: { id: 'user-e2e-sunqiang' } }
    ]
    const found = findSubTableRowByMiExpansionId(rows, 88)
    expect(found?.name).toBe('8')
    expect(found?.id).toBeUndefined()
  })

  it('findMiIsolatedParentRow falls back to sole row when rowId is id_idw but row only has SQL id', () => {
    const rows = [{ id: 6532, assignee_user_id: '44053631', task_current_node: 'sub form2' }]
    expect(findSubTableRowByMiExpansionId(rows, 44053631)).toBeNull()
    const parent = findMiIsolatedParentRow(rows, 44053631)
    expect(parent?.id).toBe(6532)
  })

  it('scrubMiCorruptLinkChildRowsForParent repairs id when row has form payload', () => {
    const subTables: Record<string, unknown> = {
      '90': [
        { id: 44, id_idw: 88, sex: 'x' },
        { id: 88, id_idw: 88, sex: 'ok' }
      ]
    }
    scrubMiCorruptLinkChildRowsForParent(subTables, 88)
    const rows = subTables['90'] as Array<Record<string, unknown>>
    expect(rows).toHaveLength(2)
    const repaired = rows.find(r => r.sex === 'x')
    expect(repaired?.id).toBe(88)
  })

  it('pickMiLinkChildRowsForParent matches child by parent id_idw not stale id FK', () => {
    const parent = { name: '8', id_idw: 88 }
    const candidates = [
      { id: 44, id_idw: 88, sex: true, age: '12' },
      { id: 555, id_idw: 245, sex: false, age: '9' }
    ]
    const picked = pickMiLinkChildRowsForParent(parent, candidates, null)
    expect(picked).toHaveLength(1)
    expect(picked[0].sex).toBe(true)
    expect(picked[0].age).toBe('12')
  })

  it('scrubMiCorruptLinkChildRowsForParent drops thin stale id rows without payload', () => {
    const subTables: Record<string, unknown> = {
      '90': [{ id: 44, id_idw: 88 }, { id: 88, id_idw: 88, age: 1 }]
    }
    scrubMiCorruptLinkChildRowsForParent(subTables, 88)
    const rows = subTables['90'] as Array<Record<string, unknown>>
    expect(rows).toHaveLength(1)
    expect(rows[0].age).toBe(1)
  })
})
