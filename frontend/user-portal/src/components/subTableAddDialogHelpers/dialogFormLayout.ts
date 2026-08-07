import type { FormField } from '../formRendererHelpers'
import type { DialogColumn } from './types'

/** One vertical block in the Add/Edit dialog — optional card chrome around columns. */
export type DialogLayoutItem =
  | {
      type: 'column'
      key: string
      column: DialogColumn
      /** Rendered inside the Assignment Mode block; `last` closes the box. */
      assignmentSlot?: 'owned' | 'last'
    }
  | { type: 'miAssignment'; key: string }

export type DialogLayoutGroup = {
  key: string
  /** Non-null → wrap items in el-card (DW Form Preview elCard parity). */
  title: string | null
  items: DialogLayoutItem[]
}

function collectLayoutItems(
  fields: FormField[],
  colByField: Map<string, DialogColumn>,
  used: Set<string>,
): DialogLayoutItem[] {
  const items: DialogLayoutItem[] = []
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
      const fromCollapse =
        Array.isArray(ch.collapsePanels)
          ? ch.collapsePanels.flatMap(panel => panel.fields || [])
          : []
      items.push(...collectLayoutItems([...nested, ...fromTabs, ...fromCollapse], colByField, used))
      continue
    }
    if (ch.type === 'miAssignment') {
      if (ch.hidden) {
        // Designer "Hide" toggle — the whole block (marker + owned fields) disappears
        // together. Still mark the owned fields as used (discarding the layout items)
        // so they don't leak into `rest` as ordinary unwrapped columns below.
        collectLayoutItems(ch.children || [], colByField, used)
        continue
      }
      items.push({ type: 'miAssignment', key: ch.key })
      // The container owns its fields as children — emit them immediately after
      // the marker so they render inside the block. groupAssignmentFieldsUnderMarker
      // then only has to tag them; it finds them already adjacent.
      items.push(...collectLayoutItems(ch.children || [], colByField, used))
      continue
    }
    if (ch.key && colByField.has(ch.key) && !used.has(ch.key)) {
      items.push({ type: 'column', key: ch.key, column: colByField.get(ch.key)! })
      used.add(ch.key)
    }
  }
  return items
}

/**
 * Move the assignment-driven columns (assignee / role / BU) so they immediately
 * follow the `miAssignment` marker, letting the dialog render them INSIDE the
 * Assignment Mode block instead of stranding them wherever the designer happened
 * to place them.
 *
 * Only reorders — never adds or drops items, so field bindings, validation and
 * the designer's ownership of placement all stay intact. When the marker is
 * absent (no MI config) the list is returned untouched.
 */
export function groupAssignmentFieldsUnderMarker(
  items: DialogLayoutItem[],
  assignmentFields: string[],
): DialogLayoutItem[] {
  const markerAt = items.findIndex((item) => item.type === 'miAssignment')
  if (markerAt < 0) return items
  const owned = new Set(assignmentFields.filter(Boolean))
  if (owned.size === 0) return items

  const pulled = items.filter(
    (item): item is Extract<DialogLayoutItem, { type: 'column' }> =>
      item.type === 'column' && owned.has(item.column.field),
  )
  if (pulled.length === 0) return items

  const tagged = pulled.map((item, index) => ({
    ...item,
    assignmentSlot: index === pulled.length - 1 ? ('last' as const) : ('owned' as const),
  }))
  const rest = items.filter((item) => !pulled.includes(item as never))
  const restMarkerAt = rest.findIndex((item) => item.type === 'miAssignment')
  return [...rest.slice(0, restMarkerAt + 1), ...tagged, ...rest.slice(restMarkerAt + 1)]
}

/**
 * Place the Assignment Mode block for sub-forms whose design carries no
 * `miAssignment` marker (they were saved before the component existed).
 *
 * The block is inserted where its own fields already sit — at the first owned
 * field — so the dialog's field order is preserved and the block simply frames
 * the controls the reader was going to meet there anyway. When the design has a
 * marker this is not called: the designer's placement wins.
 *
 * Returns the groups untouched when nothing is owned, so a misconfigured contract
 * can never strand a headless block in the dialog.
 */
export function ensureAssignmentBlockPlaced(
  groups: DialogLayoutGroup[],
  assignmentFields: string[],
): DialogLayoutGroup[] {
  const owned = new Set(assignmentFields.filter(Boolean))
  if (owned.size === 0) return groups
  if (groups.some(group => group.items.some(item => item.type === 'miAssignment'))) return groups

  const hostIndex = groups.findIndex(group =>
    group.items.some(item => item.type === 'column' && owned.has(item.column.field)))
  if (hostIndex < 0) return groups

  const host = groups[hostIndex]!
  const anchorAt = host.items.findIndex(
    item => item.type === 'column' && owned.has(item.column.field))
  const items = [
    ...host.items.slice(0, anchorAt),
    { type: 'miAssignment', key: '__mi_assignment_block__' } as DialogLayoutItem,
    ...host.items.slice(anchorAt),
  ]
  return groups.map((group, index) =>
    index === hostIndex
      ? { ...group, items: groupAssignmentFieldsUnderMarker(items, assignmentFields) }
      : group)
}

/**
 * Group dialog items by Designer form layout (elCard → card groups), preserving
 * the exact position of the non-data `miAssignment` marker through all containers.
 */
export function buildDialogLayoutGroups(
  formFields: FormField[] | undefined,
  visibleColumns: DialogColumn[],
): DialogLayoutGroup[] {
  const colByField = new Map(visibleColumns.map((c) => [c.field, c]))
  const used = new Set<string>()
  const hasCard = Array.isArray(formFields) && formFields.some((f) => f.type === 'card')
  if (!hasCard) {
    const ordered = Array.isArray(formFields)
      ? collectLayoutItems(formFields, colByField, used)
      : []
    const rest = visibleColumns
      .filter(column => !used.has(column.field))
      .map(column => ({ type: 'column', key: column.field, column }) as DialogLayoutItem)
    return [{
      key: 'flat',
      title: null,
      items: ordered.length > 0 ? [...ordered, ...rest] : rest,
    }]
  }

  const groups: DialogLayoutGroup[] = []

  const walk = (fields: FormField[]) => {
    for (const f of fields) {
      if (f.type === 'card') {
        const items = collectLayoutItems(f.children || [], colByField, used)
        if (items.length > 0) {
          groups.push({
            key: f.key || `card-${groups.length}`,
            title: f.label ?? '',
            items,
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
          items: [{ type: 'column', key: f.key, column: colByField.get(f.key)! }],
        })
        used.add(f.key)
      } else if (f.type === 'miAssignment') {
        groups.push({
          key: f.key,
          title: null,
          items: [{ type: 'miAssignment', key: f.key }],
        })
      }
    }
  }

  walk(formFields!)
  const rest = visibleColumns.filter((c) => !used.has(c.field))
  if (rest.length > 0) {
    groups.push({
      key: 'rest',
      title: null,
      items: rest.map(column => ({ type: 'column', key: column.field, column })),
    })
  }
  return groups.length > 0
    ? groups
    : [{
        key: 'flat',
        title: null,
        items: visibleColumns.map(column => ({ type: 'column', key: column.field, column })),
      }]
}
