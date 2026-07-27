import { describe, it, expect } from 'vitest'
import { flattenNestedSubTableRowsIntoPayload } from '../miLinkChildScrub'
import { dropSubsumedSubTableRows, normalizeSubTableRowsForBinding } from '../subTableRowNormalize'
import { subTableFieldValueKey } from '../subTableCore'

/**
 * A LOOKUP cell stores the WHOLE selected relation-table row (an object), see
 * {@code useSubTableDialogLookup}: `formData.value[field] = row`. Row-identity checks used to be
 * blind to that — one skipped non-scalars, the other stringified them to "[object Object]" — so two
 * rows differing ONLY by their lookup selection counted as one and the extra row was dropped.
 * Most damaging in a nested sub-table, where the innermost rows often differ only by the lookup
 * (same label / quantity, different handler or supplier).
 */

const LINA = { id: 'user-e2e-lina', username: 'e2e_lina', display_name: '李娜' }
const WANG = { id: 'user-e2e-wangfang', username: 'e2e_wangfang', display_name: '王芳' }

describe('subTableFieldValueKey', () => {
  it('distinguishes different objects and matches structurally equal ones', () => {
    expect(subTableFieldValueKey(LINA)).not.toBe(subTableFieldValueKey(WANG))
    expect(subTableFieldValueKey({ a: 1, b: 2 })).toBe(subTableFieldValueKey({ b: 2, a: 1 }))
  })

  it('keeps scalar semantics (trimmed, number/string agnostic) and treats empties as absent', () => {
    expect(subTableFieldValueKey(555)).toBe(subTableFieldValueKey(' 555 '))
    expect(subTableFieldValueKey('')).toBeNull()
    expect(subTableFieldValueKey(null)).toBeNull()
    expect(subTableFieldValueKey({})).toBeNull()
    expect(subTableFieldValueKey([])).toBeNull()
  })
})

describe('hoisting nested rows keeps lookup-only differences', () => {
  function subTables(nestedPackages: any[], flatPackages: any[]) {
    return {
      '50113': [
        { id_idw: 'ship-1', shipment_name: 'S-1', __subTables__: { '50114': nestedPackages } },
      ],
      '50114': flatPackages,
    } as Record<string, unknown>
  }

  it('a nested row that differs from the enriched flat row only by its lookup is still hoisted', () => {
    const st = subTables(
      [
        { package_label: 'P-1', weight_kg: '3', lookup: LINA },
        { package_label: 'P-1', weight_kg: '3', lookup: WANG },
      ],
      [{ package_label: 'P-1', weight_kg: '3', lookup: LINA, id_idw: 'pkg-1', shipment_id: 'ship-1' }],
    )
    flattenNestedSubTableRowsIntoPayload(st)
    const rows = st['50114'] as any[]
    expect(rows).toHaveLength(2)
    expect(rows.map(r => r.lookup?.username).sort()).toEqual(['e2e_lina', 'e2e_wangfang'])
  })

  it('the true duplicate (same lookup) still folds into its enriched flat copy', () => {
    const st = subTables(
      [{ package_label: 'P-1', weight_kg: '3', lookup: { ...LINA } }],
      [{ package_label: 'P-1', weight_kg: '3', lookup: LINA, id_idw: 'pkg-1', shipment_id: 'ship-1' }],
    )
    flattenNestedSubTableRowsIntoPayload(st)
    const rows = st['50114'] as any[]
    expect(rows).toHaveLength(1)
    expect(rows[0].id_idw).toBe('pkg-1')
  })
})

describe('ghost-row collapse keeps lookup-only differences', () => {
  it('does not fold a thin row whose lookup differs from the fatter row', () => {
    const rows = [
      { package_label: 'P-1', lookup: LINA },
      { package_label: 'P-1', lookup: WANG, weight_kg: '3' },
    ]
    expect(dropSubsumedSubTableRows(rows)).toHaveLength(2)
  })

  it('still folds a genuine subset row (same lookup, fewer fields)', () => {
    const rows = [
      { package_label: 'P-1', lookup: { ...LINA } },
      { package_label: 'P-1', lookup: LINA, weight_kg: '3' },
    ]
    const kept = dropSubsumedSubTableRows(rows)
    expect(kept).toHaveLength(1)
    expect(kept[0].weight_kg).toBe('3')
  })

  it('a row carrying only a lookup is not vacuous', () => {
    expect(normalizeSubTableRowsForBinding([{ lookup: LINA }])).toHaveLength(1)
  })
})
