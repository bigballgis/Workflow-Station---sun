import { describe, it, expect } from 'vitest'
import {
  collectLinkFormTargetBindingIds,
  collectLinkFormTargetBindingIdsFromSubListViews,
  collectAllLinkFormTargetBindingIds,
  collectRuleBindingIds,
  filterLinkOnlyStandaloneSubTableFields,
  shouldSuppressStandaloneSubTableInInitiatorRequest,
} from '../formRendererHelpers'

describe('link-only sub-table placement', () => {
    const parentBinding = {
    bindingId: 64,
    columns: [
      {
        type: 'linkForm',
        props: { boundSubTableBindingId: 66, boundSubTableName: 'subtable2' },
      },
    ],
  }
  const linkTargetBinding = {
    bindingId: 66,
    subMode: 'FULL',
    columns: [{ field: 'id', label: 'id' }],
  }
  const bindings = [parentBinding, linkTargetBinding]

  it('collects link form target binding ids by id and name', () => {
    expect(collectLinkFormTargetBindingIds(bindings)).toEqual(new Set([66]))
  })

  it('collects link targets from designer subListViews when runtime columns omit linkForm', () => {
    const runtimeBindings = [
      { bindingId: 69, columns: [{ field: 'id', label: 'id' }] },
      { bindingId: 30, subMode: 'FULL', columns: [{ field: 'id', label: 'id' }] },
    ]
    const formConfig = {
      subListViews: {
        69: {
          columns: [
            {
              columnType: 'linkForm',
              boundSubTableBindingId: 30,
              fieldName: 'linkForm:subtable2',
            },
          ],
        },
      },
    }
    expect(collectLinkFormTargetBindingIdsFromSubListViews(formConfig)).toEqual(new Set([30]))
    expect(collectAllLinkFormTargetBindingIds(runtimeBindings, formConfig)).toEqual(new Set([30]))
    expect(shouldSuppressStandaloneSubTableInInitiatorRequest(30, runtimeBindings, null, null, formConfig)).toBe(true)
  })

  it('suppresses link targets on My Request even when stale rule still places them', () => {
    const rule = [
      { type: 'subTable', _bindingId: 64 },
      { type: 'subTable', _bindingId: 66 },
    ]
    expect(collectRuleBindingIds(rule)).toEqual(new Set([64, 66]))
    expect(shouldSuppressStandaloneSubTableInInitiatorRequest(66, bindings, null)).toBe(true)
    expect(shouldSuppressStandaloneSubTableInInitiatorRequest(64, bindings, null)).toBe(false)
  })

  it('filters subTable fields for link targets from form field trees', () => {
    const rule = [{ type: 'subTable', _bindingId: 64 }]
    const fields = [
      { key: 'a', label: 'A', type: 'text' },
      { key: '__subTable_64', label: '', type: 'subTable', _bindingId: 64 },
      { key: '__subTable_66', label: '', type: 'subTable', _bindingId: 66 },
    ]
    const filtered = filterLinkOnlyStandaloneSubTableFields(fields, bindings, rule)
    expect(filtered.map(f => f.key)).toEqual(['a', '__subTable_64'])
  })

  it('suppresses merge-only bindings not in native tableBindings', () => {
    const nativeIds = new Set([64])
    expect(
      shouldSuppressStandaloneSubTableInInitiatorRequest(66, bindings, null, nativeIds)
    ).toBe(true)
    expect(
      shouldSuppressStandaloneSubTableInInitiatorRequest(64, bindings, null, nativeIds)
    ).toBe(false)
  })

  it('treats FORM_ONLY bindings as suppressible', () => {
    const formOnly = { bindingId: 99, subMode: 'FORM_ONLY', columns: [] }
    expect(shouldSuppressStandaloneSubTableInInitiatorRequest(99, [formOnly], null)).toBe(true)
  })

  it('keeps native canvas sub-tables when binding is also a link-form target (mirrorTodo + tableOnly)', () => {
    const portalViews = {
      assigneeTodo: 'tableOnly' as const,
      initiatorRequest: 'mirrorTodo' as const,
    }
    const selfRefBinding = {
      bindingId: 281,
      subMode: 'FULL',
      columns: [
        { field: 'row_id', label: 'Row ID' },
        {
          columnType: 'linkForm',
          boundSubTableBindingId: 281,
          fieldName: 'linkForm:-281',
        },
      ],
    }
    const formConfig = {
      subListViews: {
        281: {
          columns: [
            { columnType: 'linkForm', boundSubTableBindingId: 281, fieldName: 'linkForm:-281' },
          ],
        },
      },
    }
    const nativeIds = new Set([281])
    expect(
      shouldSuppressStandaloneSubTableInInitiatorRequest(
        281,
        [selfRefBinding],
        portalViews,
        nativeIds,
        formConfig,
      ),
    ).toBe(false)

    const fields = [
      { key: '__subTable_281', label: '', type: 'subTable', _bindingId: 281, portalViews },
    ]
    const filtered = filterLinkOnlyStandaloneSubTableFields(
      fields,
      [selfRefBinding],
      [{ type: 'subTable', _bindingId: 281 }],
      nativeIds,
      formConfig,
    )
    expect(filtered.map(f => f.key)).toEqual(['__subTable_281'])
  })
})
