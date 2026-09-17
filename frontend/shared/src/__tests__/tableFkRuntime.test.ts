import { describe, expect, it } from 'vitest'
import {
  applyFkFillSources,
  applyFkToInitialRow,
  buildRowAddContext,
  guardBeforeChildRowAdd,
  uniqueAncestorRow,
} from '../tableFkRuntime'

const MAIN = 50100
const PARTY = 50200
const FILE = 50300

const fileFks = [
  { fieldName: 'case_id', isForeignKey: true, refTableId: MAIN, refPrimaryKeyFields: ['id'] },
  { fieldName: 'party_id', isForeignKey: true, refTableId: PARTY, refPrimaryKeyFields: ['id'] },
]

describe('context frames — unique ancestor rows', () => {
  it('fills both FKs when MAIN and PARTY each have one frame', () => {
    const ctx = buildRowAddContext(
      { id: 'Case-1' },
      [
        { tableId: MAIN, bindingType: 'PRIMARY' },
        { bindingId: 1, tableId: PARTY, bindingType: 'SUB', data: [{ id: 'Party-1' }] },
        { bindingId: 2, tableId: FILE, bindingType: 'SUB', filterFkRefTableId: PARTY, data: [] },
      ],
      null,
      null,
      { bindingId: 2, tableId: FILE, filterFkRefTableId: PARTY },
    )
    expect(uniqueAncestorRow(ctx, PARTY)?.id).toBe('Party-1')
    const row = applyFkToInitialRow({}, fileFks, ctx)
    expect(row.case_id).toBe('Case-1')
    expect(row.party_id).toBe('Party-1')
  })

  it('does not guess when two distinct rows of the same ancestor table are in context', () => {
    const ctx = {
      primaryFormData: { id: 'Case-1' },
      contextFrames: [
        { tableId: MAIN, row: { id: 'Case-1' }, role: 'PRIMARY' as const },
        { tableId: PARTY, row: { id: 'Party-A' }, role: 'PARENT' as const, bindingId: 11 },
        { tableId: PARTY, row: { id: 'Party-B' }, role: 'FILTER_SIBLING' as const, bindingId: 12 },
      ],
    }
    expect(uniqueAncestorRow(ctx, PARTY)).toBeNull()
    expect(uniqueAncestorRow(ctx, MAIN)?.id).toBe('Case-1')
    const row = applyFkToInitialRow({}, fileFks, ctx)
    expect(row.case_id).toBe('Case-1')
    expect(row.party_id).toBeUndefined()
    expect(guardBeforeChildRowAdd(fileFks, ctx)).toContain('party_id')
    expect(guardBeforeChildRowAdd(fileFks, ctx)).not.toContain('case_id')
  })

  it('treats two frames of the same row identity as unique', () => {
    const party = { id: 'Party-1', platformRowUuid: 'u-1' }
    const ctx = {
      primaryFormData: { id: 'Case-1' },
      contextFrames: [
        { tableId: MAIN, row: { id: 'Case-1' }, role: 'PRIMARY' as const },
        { tableId: PARTY, row: party, role: 'PARENT' as const },
        { tableId: PARTY, row: { ...party }, role: 'FILTER_SIBLING' as const },
      ],
    }
    expect(uniqueAncestorRow(ctx, PARTY)?.id).toBe('Party-1')
    expect(applyFkToInitialRow({}, fileFks, ctx).party_id).toBe('Party-1')
  })

  it('fills a named ancestor binding when two same-table rows exist', () => {
    const ctx = buildRowAddContext(
      { id: 'Case-1' },
      [
        { tableId: MAIN, bindingType: 'PRIMARY' },
        { bindingId: 11, tableId: PARTY, bindingType: 'SUB', data: [{ id: 'Party-A' }] },
        { bindingId: 12, tableId: PARTY, bindingType: 'SUB', data: [{ id: 'Party-B' }] },
        { bindingId: 20, tableId: FILE, bindingType: 'SUB', data: [] },
      ],
      { id: 'Party-A' },
      PARTY,
      { bindingId: 20, tableId: FILE, filterFkRefTableId: PARTY },
      11,
    )
    expect(uniqueAncestorRow(ctx, PARTY)).toBeNull()
    const undeclared = applyFkToInitialRow({}, fileFks, ctx)
    expect(undeclared.party_id).toBeUndefined()
    const declared = applyFkToInitialRow({}, applyFkFillSources(fileFks, [
      { fieldName: 'case_id', kind: 'PRIMARY' },
      { fieldName: 'party_id', kind: 'ANCESTOR', ancestorBindingId: 12 },
    ]), ctx)
    expect(declared.case_id).toBe('Case-1')
    expect(declared.party_id).toBe('Party-B')
    const fromParent = applyFkToInitialRow({}, applyFkFillSources(fileFks, [
      { fieldName: 'party_id', kind: 'PARENT' },
    ]), ctx)
    expect(fromParent.party_id).toBe('Party-A')
  })

  it('omits an ambiguous table from ancestorRowsByTableId', () => {
    const ctx = buildRowAddContext(
      { id: 'Case-1' },
      [{ tableId: MAIN, bindingType: 'PRIMARY' }],
      { id: 'Other-main' },
      MAIN,
    )
    expect(ctx.ancestorRowsByTableId?.[MAIN]).toBeUndefined()
    expect(uniqueAncestorRow(ctx, MAIN)).toBeNull()
  })
})
