import { describe, expect, it } from 'vitest'
import { buildComputedFieldDefinition } from '@/utils/computedFieldConfig'
import {
  computedFieldSaveWarning,
  resolveTableSaveErrorMessage,
} from '@/utils/tableSaveComputedField'

const t = (key: string, params?: Record<string, unknown>) => {
  if (key === 'table.computedField.typeMismatch') return `type-mismatch:${params?.name}`
  if (key === 'table.computedField.missingFormula') return `missing:${params?.name}`
  if (key === 'common.error') return 'Operation failed'
  return key
}

describe('tableSaveComputedField', () => {
  it('warns before save when SUM is stored on VARCHAR', () => {
    const built = buildComputedFieldDefinition('SUM(date_info.day)', 'aggregate', 'fail')
    expect(built.ok).toBe(true)
    if (!built.ok) return
    expect(computedFieldSaveWarning([
      {
        fieldName: 'day',
        displayName: 'Day',
        dataType: 'VARCHAR',
        isComputed: true,
        computedField: built.value,
      },
    ], t)).toBe('type-mismatch:Day')
  })

  it('maps COMPUTED_FIELD_TYPE_MISMATCH to the type-mismatch copy', () => {
    const message = resolveTableSaveErrorMessage({
      response: {
        data: {
          success: false,
          error: {
            code: 'COMPUTED_FIELD_TYPE_MISMATCH',
            message: "Computed field 'day' produces a number but the column is declared as VARCHAR.",
          },
        },
      },
    }, t)
    expect(message).toBe('type-mismatch:day')
  })

  it('does not fall back to Operation failed when ApiResponse.error.message is present', () => {
    const message = resolveTableSaveErrorMessage({
      response: {
        status: 400,
        data: {
          success: false,
          error: {
            code: 'COMPUTED_FIELD_UNKNOWN_SUB_TABLE',
            message: "aggregates 'DayInfo', which is not a sub-table of this Function Unit",
          },
        },
      },
    }, t)
    expect(message).toContain('DayInfo')
    expect(message).not.toBe('Operation failed')
  })
})
