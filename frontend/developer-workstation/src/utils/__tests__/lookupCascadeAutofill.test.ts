import { describe, it, expect } from 'vitest'
import {
  buildPreviewAutofillModelValue,
  buildPreviewAutofillRow,
} from '../lookupCascade'

const stageParent = { id: 'CAST-1', code: 'A', type: 'Q' }
const testStatusCfg = {
  filterConditions: [] as { fieldName: string; value: string; matchType?: 'eq' }[],
  derivedFrom: {
    parentField: 'stage',
    joins: [{ fromColumn: 'code', toColumn: 'status_code', matchType: 'eq' as const }],
    derivedMode: 'autofill' as const,
  },
}

describe('buildPreviewAutofillRow / ModelValue', () => {
  it('sets Selected Display Field to a readable Sample tag (not PK-only)', () => {
    const row = buildPreviewAutofillRow(testStatusCfg, stageParent, {
      searchFields: ['status_id'],
      selectedDisplayField: 'status_name',
      displayFields: ['status_name'],
    })
    expect(row).toBeTruthy()
    expect(row!.status_code).toBe('A')
    expect(row!.status_name).toBe('Sample 1')
    expect(String(row!.status_name)).not.toContain('[object Object]')
  })

  it('multiple autofill returns an array of full rows (Portal parity)', () => {
    const value = buildPreviewAutofillModelValue(testStatusCfg, stageParent, {
      searchFields: ['status_id'],
      selectedDisplayField: 'status_name',
      displayFields: ['status_name'],
      multiple: true,
    })
    expect(Array.isArray(value)).toBe(true)
    const rows = value as Record<string, unknown>[]
    expect(rows).toHaveLength(1)
    expect(rows[0].status_name).toBe('Sample 1')
  })

  it('single autofill returns one row object; clear parent yields null / []', () => {
    expect(
      buildPreviewAutofillModelValue(testStatusCfg, stageParent, {
        selectedDisplayField: 'status_name',
        multiple: false,
      }),
    ).toMatchObject({ status_code: 'A' })
    expect(
      buildPreviewAutofillModelValue(testStatusCfg, null, { multiple: true }),
    ).toEqual([])
    expect(
      buildPreviewAutofillModelValue(testStatusCfg, null, { multiple: false }),
    ).toBeNull()
  })
})
