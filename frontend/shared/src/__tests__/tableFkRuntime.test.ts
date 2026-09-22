import { describe, expect, it } from 'vitest'
import {
  applyFkFillSources,
  applyFkToInitialRow,
  buildRowAddContext,
  guardBeforeChildRowAdd,
  resolveBindingParentSelection,
  selectBindingOwnedFkMetas,
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

describe('binding parent selection', () => {
  it('offers every distinct configured parent row without relying on table or field names', () => {
    const selection = resolveBindingParentSelection(
      [{
        fieldName: 'owner_ref',
        isForeignKey: true,
        refTableId: PARTY,
        refPrimaryKeyFields: ['party_key'],
      }],
      'owner_ref',
      [
        {
          bindingId: 11,
          tableId: PARTY,
          tableDisplayName: 'Stakeholders',
          primaryKeyFields: ['party_key'],
          columns: [
            { field: 'party_key', label: 'Party key' },
            { field: 'display_text', label: 'Display text' },
          ],
          data: [
            { party_key: 'P-35', display_text: 'Alice' },
            { party_key: 'P-36', display_text: 'Bob' },
          ],
        },
        { bindingId: 20, tableId: FILE, data: [] },
      ],
      20,
    )

    expect(selection?.fieldName).toBe('owner_ref')
    expect(selection?.parentTableName).toBe('Stakeholders')
    expect(selection?.options.map(option => ({ value: option.value, label: option.label }))).toEqual([
      { value: 'P-35', label: 'P-35 · Alice' },
      { value: 'P-36', label: 'P-36 · Bob' },
    ])
  })

  it('deduplicates the same parent row and falls back to the configured composite key label', () => {
    const selection = resolveBindingParentSelection(
      [{
        fieldName: 'owner_ref',
        isForeignKey: true,
        refTableId: PARTY,
        refPrimaryKeyFields: ['region', 'party_key'],
      }],
      'owner_ref',
      [
        {
          bindingId: 11,
          tableId: PARTY,
          tableName: 'renamed_parent',
          primaryKeyFields: ['region', 'party_key'],
          columns: [
            { field: 'region', label: 'Region' },
            { field: 'party_key', label: 'Party key' },
          ],
          data: [{ region: 'HK', party_key: 'P-35' }],
        },
        {
          bindingId: 12,
          tableId: PARTY,
          data: [{ region: 'HK', party_key: 'P-35' }],
        },
      ],
      20,
    )

    expect(selection?.options).toHaveLength(1)
    expect(selection?.options[0].label).toBe('region=HK, party_key=P-35')
  })

  it('writes the explicitly selected parent only to the current binding ownership FK', () => {
    const selection = resolveBindingParentSelection(
      [
        { fieldName: 'case_ref', isForeignKey: true, refTableId: MAIN, refPrimaryKeyFields: ['id'] },
        { fieldName: 'owner_ref', isForeignKey: true, refTableId: PARTY, refPrimaryKeyFields: ['party_key'] },
      ],
      'owner_ref',
      [{
        bindingId: 11,
        tableId: PARTY,
        primaryKeyFields: ['party_key'],
        data: [
          { party_key: 'P-35', display_text: 'Alice' },
          { party_key: 'P-36', display_text: 'Bob' },
        ],
      }],
      20,
    )
    const selected = selection?.options.find(option => option.value === 'P-36')
    expect(selected).toBeDefined()

    const ctx = buildRowAddContext(
      { id: 'Case-1' },
      [{ tableId: MAIN, bindingType: 'PRIMARY' }],
      selected?.row,
      selected?.tableId,
      { bindingId: 20, tableId: FILE, filterFkRefTableId: PARTY },
      selected?.bindingId,
    )
    const ownedMetas = selectBindingOwnedFkMetas([
      { fieldName: 'case_ref', isForeignKey: true, refTableId: MAIN, refPrimaryKeyFields: ['id'] },
      { fieldName: 'owner_ref', isForeignKey: true, refTableId: PARTY, refPrimaryKeyFields: ['party_key'] },
    ], 'owner_ref')

    expect(applyFkToInitialRow({}, ownedMetas, ctx)).toEqual({ owner_ref: 'P-36' })
  })
})
