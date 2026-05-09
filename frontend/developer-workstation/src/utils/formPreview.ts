/**
 * Form Preview Utilities
 * Pure functions for form preview — no Vue reactivity dependency.
 */

/**
 * Parse a JSON lookup configuration string, returning {} on failure.
 */
export function parseLookupConfig(raw?: string): any {
  if (!raw) return {}
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

/**
 * Generate a mock value for a given SQL data type (used in preview).
 */
export function getMockValueForType(dataType: string): string {
  const type = (dataType || '').toUpperCase()
  if (type.includes('INT') || type === 'BIGINT') return '1'
  if (type.includes('DECIMAL') || type.includes('NUMERIC') || type.includes('FLOAT') || type.includes('DOUBLE')) return '100.00'
  if (type === 'BOOLEAN' || type === 'BOOL') return 'true'
  if (type === 'DATE') return '2026-01-01'
  if (type.includes('TIMESTAMP') || type === 'DATETIME') return '2026-01-01 00:00:00'
  if (type.includes('TIME')) return '00:00:00'
  return 'Sample'
}

/**
 * Generate default preview columns for a given table type.
 */
export function derivePreviewColumns(
  tableType: string,
  t: (key: string) => string
): Array<{ field: string; label: string; type?: string }> {
  const defaults: Record<string, Array<{ field: string; label: string; type?: string }>> = {
    'SUB': [
      { field: 'item_name', label: t('preview.itemName') },
      { field: 'quantity', label: t('preview.quantity'), type: 'number' },
      { field: 'unit_price', label: t('preview.unitPrice'), type: 'number' },
      { field: 'amount', label: t('preview.amount'), type: 'number' },
      { field: 'remark', label: t('preview.remark') }
    ],
    'ACTION': [
      { field: 'action_type', label: t('preview.actionType') },
      { field: 'action_result', label: t('preview.actionResult') },
      { field: 'operator', label: t('preview.operator') },
      { field: 'action_time', label: t('preview.actionTime'), type: 'datetime' },
    ],
    'RELATED': [
      { field: 'title', label: t('preview.title') },
      { field: 'status', label: t('preview.status') },
      { field: 'created_at', label: t('preview.createdAt'), type: 'datetime' },
    ],
  }
  return defaults[tableType] || []
}
