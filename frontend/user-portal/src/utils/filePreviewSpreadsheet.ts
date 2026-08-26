export const TABLE_MAX_ROWS = 1000
export const TABLE_MAX_COLS = 80

export type BoundedTable = {
  rows: string[][]
  truncated: boolean
}

export type SpreadsheetPreview = {
  sheets: Array<{ name: string; rows: string[][] }>
  truncated: boolean
}

export function boundSpreadsheetMatrix(raw: unknown[][]): BoundedTable {
  const truncated = raw.length > TABLE_MAX_ROWS
    || raw.some(row => Array.isArray(row) && row.length > TABLE_MAX_COLS)
  const rows = raw.slice(0, TABLE_MAX_ROWS).map((row) => {
    const cells = Array.isArray(row) ? row : []
    return cells.slice(0, TABLE_MAX_COLS).map((cell) => {
      if (cell == null) return ''
      return String(cell)
    })
  })
  return { rows, truncated }
}

export async function parseSpreadsheetPreview(buffer: ArrayBuffer): Promise<SpreadsheetPreview> {
  const XLSX = await import('xlsx')
  const wb = XLSX.read(buffer, { type: 'array', cellDates: true })
  let truncated = false
  const sheets = (wb.SheetNames || []).map((name) => {
    const sheet = wb.Sheets[name]
    const raw = sheet
      ? (XLSX.utils.sheet_to_json(sheet, { header: 1, raw: false, defval: '' }) as unknown[][])
      : []
    const bounded = boundSpreadsheetMatrix(raw)
    if (bounded.truncated) truncated = true
    return { name: name || 'Sheet', rows: bounded.rows }
  })
  return { sheets, truncated }
}
