import type { MainTableViewFieldColumn, MainTableViewDataRow } from '@/api/mainTableView'

export function csvEscape(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`
  }
  return value
}

export function formatMainTableViewCell(value: unknown): string {
  if (value == null) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export function downloadMainTableViewRowsAsCsv(
  rows: MainTableViewDataRow[],
  columns: MainTableViewFieldColumn[],
  baseName: string,
): void {
  const header = [
    csvEscape('processInstanceId'),
    ...columns.map(col => csvEscape(col.displayLabel)),
  ].join(',')
  const lines = rows.map(row =>
    [
      csvEscape(row.processInstanceId),
      ...columns.map(col => csvEscape(formatMainTableViewCell(row.values[col.fieldName]))),
    ].join(','),
  )
  const content = `\uFEFF${[header, ...lines].join('\n')}`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${baseName}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
