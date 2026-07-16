import { describe, it, expect } from 'vitest'
import {
  clampColumnWidth,
  matchesColumnFilter,
  activeFilterEntries,
  sanitizeRuntimeState,
  createDefaultRuntime,
  DWL_COLUMN_WIDTH_MIN,
  DWL_COLUMN_WIDTH_MAX,
  type DesignerListRuntimeState,
} from '../designerListGridRuntime'

describe('designerListGridRuntime', () => {
  describe('clampColumnWidth', () => {
    it('clamps below minimum and above maximum', () => {
      expect(clampColumnWidth(10)).toBe(DWL_COLUMN_WIDTH_MIN)
      expect(clampColumnWidth(9999)).toBe(DWL_COLUMN_WIDTH_MAX)
      expect(clampColumnWidth(180)).toBe(180)
    })
  })

  describe('matchesColumnFilter', () => {
    it('matches contains case-insensitively', () => {
      expect(matchesColumnFilter('Hello World', { operator: 'contains', value: 'world' })).toBe(true)
      expect(matchesColumnFilter('Hello', { operator: 'contains', value: 'xyz' })).toBe(false)
    })

    it('treats null values as empty for isNull/isNotNull', () => {
      expect(matchesColumnFilter(null, { operator: 'isNull', value: '' })).toBe(true)
      expect(matchesColumnFilter('x', { operator: 'isNotNull', value: '' })).toBe(true)
    })

    it('rejects unknown operators', () => {
      expect(
        matchesColumnFilter('x', { operator: 'bogus' as 'contains', value: 'x' }),
      ).toBe(false)
    })
  })

  describe('activeFilterEntries', () => {
    it('keeps isNull without value and drops empty contains', () => {
      const entries = activeFilterEntries({
        a: { operator: 'isNull', value: '' },
        b: { operator: 'contains', value: '  ' },
        c: { operator: 'eq', value: 'ok' },
      })
      expect(entries.map(([k]) => k).sort()).toEqual(['a', 'c'])
    })
  })

  describe('sanitizeRuntimeState', () => {
    it('drops invalid operators and non-finite widths', () => {
      const out = sanitizeRuntimeState({
        columnWidths: { ok: 200, bad: NaN, tiny: 10 },
        filters: {
          valid: { operator: 'eq', value: 'x' },
          invalid: { operator: 'DROP_TABLE', value: 'x' },
        },
      })
      expect(out.columnWidths).toEqual({ ok: 200, tiny: DWL_COLUMN_WIDTH_MIN })
      expect(out.filters).toEqual({ valid: { operator: 'eq', value: 'x' } })
    })

    it('returns empty state for non-object payloads', () => {
      expect(sanitizeRuntimeState({})).toEqual(createDefaultRuntime())
      expect(sanitizeRuntimeState(null as unknown as Partial<DesignerListRuntimeState>)).toEqual(
        createDefaultRuntime(),
      )
    })
  })
})
