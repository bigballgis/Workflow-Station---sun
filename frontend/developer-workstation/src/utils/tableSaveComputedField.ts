import {
  findNumericFormulaOnTextColumn,
  parseComputedFieldFromApi,
  type ComputedFieldColumnLike,
} from '@/utils/computedFieldConfig'
import {
  pickHttpErrorBodyMessage,
  pickHttpErrorCode,
  resolveUserFacingHttpMessage,
} from '@/utils/httpErrorMessage'

const COMPUTED_FIELD_NAME_IN_MESSAGE = /Computed field '([^']+)'/i

export function computedFieldSaveWarning(
  fields: ComputedFieldColumnLike[] | undefined,
  t: (key: string, params?: Record<string, unknown>) => string,
): string | undefined {
  const missing = (fields || []).find(
    (field) => field.isComputed && !parseComputedFieldFromApi(field.computedField ?? field.computedFieldJson)?.source?.trim(),
  )
  if (missing) {
    return t('table.computedField.missingFormula', {
      name: missing.displayName || missing.fieldName,
    })
  }
  const mismatch = findNumericFormulaOnTextColumn(fields)
  if (mismatch) {
    return t('table.computedField.typeMismatch', {
      name: mismatch.displayName || mismatch.fieldName,
    })
  }
  return undefined
}

export function resolveTableSaveErrorMessage(
  error: unknown,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  const ax = error as { response?: { data?: unknown } }
  const code = pickHttpErrorCode(ax.response?.data)
  if (code === 'COMPUTED_FIELD_TYPE_MISMATCH') {
    const fromBody = pickHttpErrorBodyMessage(ax.response?.data)
    const matched = fromBody?.match(COMPUTED_FIELD_NAME_IN_MESSAGE)
    return t('table.computedField.typeMismatch', {
      name: matched?.[1] || 'field',
    })
  }
  return resolveUserFacingHttpMessage(error, (key) => t(key)) || t('common.error')
}
