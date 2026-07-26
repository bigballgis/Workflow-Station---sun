import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

export interface LookupPreviewMockFieldDef {
  fieldName: string
  dataType?: string
}

/**
 * Build Form Preview mock lookup rows.
 *
 * Important for cascade filter mode: do NOT copy filter condition values onto every
 * row (that made applyLookupFixedFilters a no-op). Join / filter columns get
 * per-row distinct Sample values so selecting a parent LOOKUP can narrow results.
 */
export function buildLookupPreviewMockRows(opts: {
  displayFields?: string[]
  searchFields?: string[]
  viewFields?: Array<{ fieldName: string }>
  fieldDefs?: LookupPreviewMockFieldDef[]
  /** Extra columns required for cascade joins (parent fromColumn / child toColumn). */
  ensureFields?: string[]
  filterConditions?: LookupFilterCondition[]
  count?: number
}): Record<string, unknown>[] {
  const count = opts.count ?? 3
  const fieldDefs = opts.fieldDefs || []
  const dataTypeOf = (fieldName: string) =>
    fieldDefs.find(d => d.fieldName === fieldName)?.dataType || 'VARCHAR'

  const columnNames = new Set<string>()
  for (const f of opts.displayFields || []) if (f) columnNames.add(f)
  for (const f of opts.searchFields || []) if (f) columnNames.add(f)
  for (const vf of opts.viewFields || []) if (vf?.fieldName) columnNames.add(vf.fieldName)
  for (const fd of fieldDefs) if (fd.fieldName) columnNames.add(fd.fieldName)
  for (const f of opts.ensureFields || []) if (f) columnNames.add(f)
  // Include filter field names so derived joins (e.g. status_code) exist — with DISTINCT values.
  for (const c of opts.filterConditions || []) {
    if (c?.fieldName) columnNames.add(c.fieldName)
  }

  if (columnNames.size === 0) return []

  const rows: Record<string, unknown>[] = []
  for (let i = 1; i <= count; i++) {
    const row: Record<string, unknown> = {}
    for (const name of columnNames) {
      row[name] = mockLookupPreviewCell(dataTypeOf(name), i)
    }
    rows.push(row)
  }
  return rows
}

export function mockLookupPreviewCell(dataType: string, index: number): string {
  const type = (dataType || '').toUpperCase()
  if (type.includes('INT') || type === 'BIGINT') return String(index)
  if (type.includes('DECIMAL') || type.includes('NUMERIC') || type.includes('FLOAT') || type.includes('DOUBLE')) {
    return (index * 100).toFixed(2)
  }
  if (type === 'BOOLEAN' || type === 'BOOL') return index % 2 === 0 ? 'true' : 'false'
  if (type === 'DATE') return `2026-01-0${index}`
  if (type.includes('TIMESTAMP') || type === 'DATETIME') return `2026-01-0${index} 00:00:00`
  if (type.includes('TIME')) return `0${index}:00:00`
  return `Sample ${index}`
}
