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

/**
 * The designer's Readonly toggle on the Assignment Mode block fixes this row's
 * assignment, so it must reach the pickers the block owns — the block itself renders no
 * control, so marking only the marker changed nothing on screen.
 *
 * `isColDisabled` already reads `column.readonly`, so stamping it here is all the
 * dialog needs; a column that is readonly for its own reasons is left as it is.
 */
function applyAssignmentReadonly(
  items: DialogLayoutItem[],
  readonly: boolean,
): DialogLayoutItem[] {
  if (!readonly) return items
  return items.map(item => item.type === 'column'
    ? { ...item, column: { ...item.column, readonly: true } }
    : item)
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
      items.push(...applyAssignmentReadonly(
        collectLayoutItems(ch.children || [], colByField, used),
        ch.readonly === true,
      ))
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
    // Designed columns are the only truth (subtable-columns-dw-parity): a form that
    // references any column has authored its field set, so columns it left out were
    // left out on purpose and must not reappear. Only a design that references
    // nothing at all falls back to the whole table — otherwise a sub-table whose
    // physical columns outnumber its designed fields (e.g. an `assignee` column the
    // author never placed) would render fields DW Form Preview never shows.
    const rest = visibleColumns
      .filter(column => !used.has(column.field))
      .map(column => ({ type: 'column', key: column.field, column }) as DialogLayoutItem)
    return [{
      key: 'flat',
      title: null,
      items: ordered.length > 0 ? ordered : rest,
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
        if (f.hidden) {
          // Designer "Hide" toggle — the whole block goes, owned fields included.
          // Must use collectLayoutItems (discarding its result), NOT walk(): walk
          // promotes anything matching a column into its own group, which would
          // re-surface the very fields the Hide toggle is meant to remove.
          // collectLayoutItems only marks them used, so they also stay out of the
          // empty-design fallback below.
          collectLayoutItems(f.children || [], colByField, used)
          continue
        }
        // Emit the marker together with the fields it owns, mirroring the flat
        // path's collectLayoutItems. Pushing the marker alone rendered the block
        // as an empty frame while its picker went missing entirely.
        groups.push({
          key: f.key,
          title: null,
          items: [
            { type: 'miAssignment', key: f.key },
            ...applyAssignmentReadonly(
              collectLayoutItems(f.children || [], colByField, used),
              f.readonly === true,
            ),
          ],
        })
      }
    }
  }

  walk(formFields!)
  // Same parity rule as the flat path above: groups exist ⇒ the design authored its
  // field set, so undesigned physical columns stay out. The empty-design fallback
  // below still shows the whole table.
  return groups.length > 0
    ? groups
    : [{
        key: 'flat',
        title: null,
        items: visibleColumns.map(column => ({ type: 'column', key: column.field, column })),
      }]
}
