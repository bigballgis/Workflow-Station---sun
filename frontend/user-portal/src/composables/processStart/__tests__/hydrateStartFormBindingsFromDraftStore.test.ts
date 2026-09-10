import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createProcessStartSubTables } from '../useProcessStartSubTables'
import type { ProcessStartSubTableBinding } from '../useProcessStartState'

function correspondenceBinding(
  extra: Partial<ProcessStartSubTableBinding> = {},
): ProcessStartSubTableBinding {
  return {
    bindingId: 1133,
    tableId: 392,
    bindingType: 'table',
    bindingMode: 'subtable',
    tableName: 'atm_correspondence',
    designerTableName: 'atm_correspondence',
    tableType: 'dw',
    tableDescription: '',
    columns: [],
    data: [],
    ...extra,
  }
}

function createApi(bindings: ProcessStartSubTableBinding[]) {
  const subTableBindings = ref(bindings)
  return {
    subTableBindings,
    ...createProcessStartSubTables({
      caches: { cachedContentForms: [], cachedRelationTableFieldIndex: new Map() },
      subTableBindings,
      deriveColumnsFromBinding: () => [],
    }),
  }
}

describe('hydrateStartFormBindingsFromDraftStore', () => {
  it('restores rows from the canonical dw: key written by save draft', () => {
    const { subTableBindings, hydrateStartFormBindingsFromDraftStore } = createApi([
      correspondenceBinding(),
    ])
    hydrateStartFormBindingsFromDraftStore({
      'dw:atm_correspondence': [{ correspondence_id: 'Corr-000047' }],
    })
    expect(subTableBindings.value[0].data.map(r => r.correspondence_id))
      .toEqual(['Corr-000047'])
  })

  it('falls back to binding-id keys for older drafts', () => {
    const { subTableBindings, hydrateStartFormBindingsFromDraftStore } = createApi([
      correspondenceBinding(),
    ])
    hydrateStartFormBindingsFromDraftStore({
      1133: [{ correspondence_id: 'Corr-000001' }],
    })
    expect(subTableBindings.value[0].data.map(r => r.correspondence_id))
      .toEqual(['Corr-000001'])
  })
})
