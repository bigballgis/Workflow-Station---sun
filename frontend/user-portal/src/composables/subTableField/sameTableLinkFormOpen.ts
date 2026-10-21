import type { Column, SubTableBinding, SubTableFieldProps } from './subTableFieldTypes'
import { isSameLinkFormTable } from './linkFormBindingScope'
import { isLinkFormBoundToHostGrid } from './subTableLinkFormRowMatch'
import { sameTableLinkFormRow } from './subTableLinkFormSeed'

/** A same-table target is either the current scope or a unique shared row, never an arbitrary sibling. */
export function openSameTableLinkForm(input: {
  col: Column
  row: Record<string, unknown>
  host: SubTableFieldProps
  binding: SubTableBinding | undefined
  open: (rows: Record<string, unknown>[]) => void
  unmatched: () => void
}): boolean {
  const { col, row, host, binding, open, unmatched } = input
  if (isLinkFormBoundToHostGrid(col, host, binding)) {
    open([{ ...row }])
    return true
  }
  if (!binding || !isSameLinkFormTable(binding, host)) return false
  const matched = sameTableLinkFormRow(host, binding, row)
  if (matched) open([{ ...matched }])
  else unmatched()
  return true
}
