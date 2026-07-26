import type { FormField } from '../formRendererHelpers'
import type { DialogColumn } from './types'

/** One vertical block in the Add/Edit dialog — optional card chrome around columns. */
export type DialogLayoutGroup = {
  key: string
  /** Non-null → wrap columns in el-card (DW Form Preview elCard parity). */
  title: string | null
  columns: DialogColumn[]
}

function collectLeafColumns(
  fields: FormField[],
  colByField: Map<string, DialogColumn>,
  used: Set<string>,
): DialogColumn[] {
  const cols: DialogColumn[] = []
  for (const ch of fields) {
    if (
      ch.type === 'card' ||
      ch.type === 'row' ||
      ch.type === 'col' ||
      ch.type === 'tabs' ||
      ch.type === 'collapse'
    ) {
      const nested = ch.children || []
      const fromTabs =
        Array.isArray((ch as { tabs?: Array<{ fields?: FormField[] }> }).tabs)
          ? (ch as { tabs: Array<{ fields?: FormField[] }> }).tabs.flatMap((t) => t.fields || [])
          : []
      cols.push(...collectLeafColumns([...nested, ...fromTabs], colByField, used))
      continue
    }
    if (ch.key && colByField.has(ch.key) && !used.has(ch.key)) {
      cols.push(colByField.get(ch.key)!)
      used.add(ch.key)
    }
  }
  return cols
}

/**
 * Group dialog columns by Designer form layout (elCard → card groups).
 * When there is no card in formFields, returns a single flat group (legacy path).
 */
export function buildDialogLayoutGroups(
  formFields: FormField[] | undefined,
  visibleColumns: DialogColumn[],
): DialogLayoutGroup[] {
  const colByField = new Map(visibleColumns.map((c) => [c.field, c]))
  const used = new Set<string>()
  const hasCard = Array.isArray(formFields) && formFields.some((f) => f.type === 'card')
  if (!hasCard) {
    return [{ key: 'flat', title: null, columns: visibleColumns }]
  }

  const groups: DialogLayoutGroup[] = []

  const walk = (fields: FormField[]) => {
    for (const f of fields) {
      if (f.type === 'card') {
        const cols = collectLeafColumns(f.children || [], colByField, used)
        if (cols.length > 0) {
          groups.push({
            key: f.key || `card-${groups.length}`,
            title: f.label ?? '',
            columns: cols,
          })
        }
        continue
      }
      if (f.type === 'row' || f.type === 'col') {
        walk(f.children || [])
        continue
      }
      if (f.type === 'tabs' && Array.isArray((f as { tabs?: unknown }).tabs)) {
        for (const tab of (f as { tabs: Array<{ fields?: FormField[] }> }).tabs) {
          walk(tab.fields || [])
        }
        continue
      }
      if (f.type === 'collapse' && Array.isArray((f as { collapsePanels?: unknown }).collapsePanels)) {
        for (const panel of (f as { collapsePanels: Array<{ fields?: FormField[] }> }).collapsePanels) {
          walk(panel.fields || [])
        }
        continue
      }
      if (f.key && colByField.has(f.key) && !used.has(f.key)) {
        groups.push({
          key: f.key,
          title: null,
          columns: [colByField.get(f.key)!],
        })
        used.add(f.key)
      }
    }
  }

  walk(formFields!)
  const rest = visibleColumns.filter((c) => !used.has(c.field))
  if (rest.length > 0) {
    groups.push({ key: 'rest', title: null, columns: rest })
  }
  return groups.length > 0 ? groups : [{ key: 'flat', title: null, columns: visibleColumns }]
}
