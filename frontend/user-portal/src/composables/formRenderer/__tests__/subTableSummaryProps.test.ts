import { describe, expect, it } from 'vitest'
import { useSubTablePortalViews } from '../useSubTablePortalViews'
import type { FormField } from '@/components/formRendererHelpers/formRendererTypes'

function makeComposable() {
  return useSubTablePortalViews({
    resolveBinding: () => undefined,
  })
}

const subTable = (extra: Partial<FormField> = {}): FormField => ({
  key: '__subTable_1',
  label: '',
  type: 'subTable',
  _bindingId: 1,
  span: 24,
  ...extra,
}) as FormField

describe('sub-table summary presentation via canvas props', () => {
  it('compactCells collapses cell detail', () => {
    const { subTableCompactLookupCells } = makeComposable()

    expect(subTableCompactLookupCells(subTable({ compactCells: true }))).toBe(true)
    expect(subTableCompactLookupCells(subTable())).toBe(false)
  })
})
