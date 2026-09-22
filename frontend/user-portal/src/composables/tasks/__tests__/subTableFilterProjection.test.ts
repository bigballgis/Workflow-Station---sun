import { describe, expect, it } from 'vitest'
import {
  applyDisplayedSliceToCanonical,
  projectSavedRowsForBinding,
  shouldProjectByFilter,
  sliceRowsByDeclaredFilter,
} from '../subTableFilterProjection'

const MAIN_TID = 50100
const PARTY_TID = 50200
const FILE_TID = 50348

const caseDoc = { id: 'X', case_id: 'C1', title: 'case-doc' }
const partyDoc = { id: 'Y', case_id: 'C1', party_id: 'P-A', title: 'party-doc' }
const otherCase = { id: 'Z', case_id: 'C2', title: 'other-case' }

function caseFiles() {
  return {
    bindingId: 50705,
    tableId: FILE_TID,
    bindingType: 'SUB',
    filterFkRefTableId: MAIN_TID,
    filterFkFieldName: 'case_id',
    primaryKeyFields: ['id'],
    data: [caseDoc, partyDoc, otherCase],
  }
}

function partyFiles() {
  return {
    bindingId: 50706,
    tableId: FILE_TID,
    bindingType: 'SUB',
    filterFkRefTableId: PARTY_TID,
    filterFkFieldName: 'party_id',
    primaryKeyFields: ['id'],
    data: [caseDoc, partyDoc, otherCase],
  }
}

function parties(rows: Array<Record<string, unknown>> = [{ id: 'P-A' }]) {
  return {
    bindingId: 50704,
    tableId: PARTY_TID,
    bindingType: 'SUB',
    filterFkRefTableId: MAIN_TID,
    filterFkFieldName: 'case_id',
    primaryKeyFields: ['id'],
    fieldDefinitions: [{ fieldName: 'id', isPrimaryKey: true }],
    data: rows,
  }
}

describe('shouldProjectByFilter', () => {
  it('projects distinct FK columns even when both reference the same parent table', () => {
    const a = caseFiles()
    const b = { ...caseFiles(), bindingId: 50706, filterFkFieldName: 'related_case_id' }
    const rows = [
      { id: 'X', case_id: 'C1', related_case_id: 'C2' },
      { id: 'Y', case_id: 'C2', related_case_id: 'C1' },
    ]
    const context = { primaryTableId: MAIN_TID, primaryPkFields: ['id'], formData: { id: 'C1' } }
    expect(projectSavedRowsForBinding(rows, a, [a, b], context)).toEqual([rows[0]])
    expect(projectSavedRowsForBinding(rows, b, [a, b], context)).toEqual([rows[1]])
  })
  it('does not project a lone SUB binding even when a filter FK is declared', () => {
    expect(shouldProjectByFilter(caseFiles(), [caseFiles(), parties()])).toBe(false)
  })

  it('projects when the same table has two SUB bindings with different filter targets', () => {
    const siblings = [caseFiles(), partyFiles(), parties()]
    expect(shouldProjectByFilter(caseFiles(), siblings)).toBe(true)
    expect(shouldProjectByFilter(partyFiles(), siblings)).toBe(true)
  })

  it('does not project two bindings that share the same filter target', () => {
    const a = caseFiles()
    const b = { ...partyFiles(), filterFkRefTableId: MAIN_TID, filterFkFieldName: 'case_id' }
    expect(shouldProjectByFilter(a, [a, b])).toBe(false)
  })
})

describe('sliceRowsByDeclaredFilter', () => {
  it('keeps intersection rows on the MAIN-filter widget', () => {
    expect(sliceRowsByDeclaredFilter([caseDoc, partyDoc, otherCase], caseFiles(), ['C1']))
      .toEqual([caseDoc, partyDoc])
  })

  it('keeps only rows matching the nested parent on the party-filter widget', () => {
    expect(sliceRowsByDeclaredFilter([caseDoc, partyDoc, otherCase], partyFiles(), ['P-A']))
      .toEqual([partyDoc])
  })

  it('returns empty when the declared parent is missing — never the full store', () => {
    expect(sliceRowsByDeclaredFilter([caseDoc, partyDoc], partyFiles(), [])).toEqual([])
  })
})

describe('projectSavedRowsForBinding', () => {
  it('splits the canonical file store across case vs party widgets', () => {
    const siblings = [caseFiles(), partyFiles(), parties()]
    const form = {
      formData: { id: 'C1' },
      primaryTableId: MAIN_TID,
      primaryPkFields: ['id'],
    }
    expect(projectSavedRowsForBinding(siblings[0].data, siblings[0], siblings, form))
      .toEqual([caseDoc, partyDoc])
    expect(projectSavedRowsForBinding(siblings[1].data, siblings[1], siblings, form))
      .toEqual([partyDoc])
    expect(siblings[0].data).toEqual([caseDoc, partyDoc, otherCase])
    expect(siblings[1].data).toEqual([caseDoc, partyDoc, otherCase])
  })

  it('uses _currentItem over sibling party rows when both exist', () => {
    const bobDoc = { id: 'W', case_id: 'C1', party_id: 'P-B', title: 'bob-doc' }
    const partyBinding = { ...partyFiles(), data: [caseDoc, partyDoc, bobDoc] }
    const siblings = [caseFiles(), partyBinding, parties([{ id: 'P-A' }, { id: 'P-B' }])]
    expect(projectSavedRowsForBinding(partyBinding.data, partyBinding, siblings, {
      formData: {
        id: 'C1',
        _currentItem: { rowKey: { id: 'P-A' }, rowId: 'P-A' },
      },
      primaryTableId: MAIN_TID,
      primaryPkFields: ['id'],
    })).toEqual([partyDoc])
    expect(partyBinding.data).toEqual([caseDoc, partyDoc, bobDoc])
  })

  it('leaves a single-binding form unsliced (V1)', () => {
    const only = {
      bindingId: 1133,
      tableId: 392,
      bindingType: 'SUB',
      tableName: 'atm_correspondence',
      data: [{ correspondence_id: 'Corr-000047' }],
    }
    expect(projectSavedRowsForBinding(only.data, only, [only], {
      formData: { id: 'C1' },
      primaryTableId: MAIN_TID,
    })).toEqual([{ correspondence_id: 'Corr-000047' }])
  })

  it('does not empty MAIN-filter widgets when the primary PK columns cannot be read', () => {
    const siblings = [caseFiles(), partyFiles(), parties()]
    expect(projectSavedRowsForBinding(siblings[0].data, siblings[0], siblings, {
      formData: { id: 'C1' },
      primaryTableId: MAIN_TID,
    })).toEqual([caseDoc, partyDoc, otherCase])
  })

  it('empties the nested widget when no party parent exists', () => {
    const partyBinding = partyFiles()
    const siblings = [caseFiles(), partyBinding, parties([])]
    expect(projectSavedRowsForBinding(partyBinding.data, partyBinding, siblings, {
      formData: { id: 'C1' },
      primaryTableId: MAIN_TID,
      primaryPkFields: ['id'],
    })).toEqual([])
    expect(partyBinding.data).toEqual([caseDoc, partyDoc, otherCase])
  })

  it('projects inline lookups from the unsliced store', () => {
    const siblings = [caseFiles(), partyFiles(), parties()]
    const storeRows = [caseDoc, partyDoc, otherCase]
    expect(projectSavedRowsForBinding(storeRows, partyFiles(), siblings, {
      formData: { id: 'C1' },
      primaryTableId: MAIN_TID,
      primaryPkFields: ['id'],
    })).toEqual([partyDoc])
  })
})

describe('applyDisplayedSliceToCanonical', () => {
  it('keeps case-doc when the party widget emits only party-doc', () => {
    const caseBinding = caseFiles()
    const partyBinding = partyFiles()
    const next = applyDisplayedSliceToCanonical(
      [caseBinding, partyBinding, parties()],
      50706,
      [partyDoc],
      { formData: { id: 'C1' }, primaryTableId: MAIN_TID, primaryPkFields: ['id'] },
    )
    expect(next.map(r => (r as { id: string }).id)).toEqual(['X', 'Z', 'Y'])
    expect(caseBinding.data).toBe(next)
    expect(partyBinding.data).toBe(next)
  })

  it('does not wipe the store when the nested parent is missing', () => {
    const caseBinding = caseFiles()
    const partyBinding = partyFiles()
    applyDisplayedSliceToCanonical(
      [caseBinding, partyBinding, parties([])],
      50706,
      [],
      { formData: { id: 'C1' }, primaryTableId: MAIN_TID, primaryPkFields: ['id'] },
    )
    expect(caseBinding.data).toEqual([caseDoc, partyDoc, otherCase])
  })
})
