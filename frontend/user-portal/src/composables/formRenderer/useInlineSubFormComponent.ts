import { applyFieldDefinitionsToFormFields } from '../../utils/subTableRowRuntime'
import { findMiIsolatedParentRow } from '../tasks/miLinkChildRows'
import type { FormField } from '../../components/formRendererHelpers'
import type { SubTableBinding } from './useSubTableBindings'

/**
 * Runtime for the `inlineSubForm` widget: the bound SUB table's designed form laid out
 * IN PLACE on the host form — no grid above it, no dialog, no save button of its own.
 *
 * <p>Exactly one row of the target binding is edited — the current MI sub-task's own row when
 * {@code currentMiRowId} identifies one (matched via {@link findMiIsolatedParentRow}, the same
 * PK-based matcher `useSubTableBindings`/`useInlineSubTableForm` already use elsewhere), else
 * `rows[0]` for the plain non-MI single-row case. Multi-row bindings are common even outside MI
 * (e.g. an MI collection sub-table simply has one row per participant) — always reading `rows[0]`
 * silently showed and overwrote whichever row happened to be first, corrupting a DIFFERENT
 * participant's data on every edit (see #1524-class regression: editing "Name" on one task
 * changed another participant's Id/Assignee).
 *
 * <p>Deliberately NOT built on {@link useInlineSubTableForm}. That composable's row picking
 * handles link-target bindings, manual row selection and FK-seeding — none of which apply here;
 * feeding it a synthetic field would also drag in cross-binding row merging and readonly
 * quality-scoring, which would hand My Request a different row than To Do. Only the MI-row-match
 * piece is shared, via {@link findMiIsolatedParentRow} directly rather than the whole composable.
 *
 * <p><b>PK/FK timing</b>: this module never allocates a primary key. The main PK does not
 * exist while the user is typing — it is allocated lazily at submit (`ensureMainPrimaryKey`)
 * or at sub-table row save, and the Add dialog explicitly defers it
 * (`deferPkAllocationUntilSave`) so a cancelled dialog does not burn a sequence number
 * (issue R3). Allocating per keystroke would burn one on every form open. FK seeding is
 * therefore the submit path's job, not ours. See docs/design/inline-sub-form-component.md.
 */

interface InlineSubFormDeps {
  readonly: () => boolean
  resolveBinding: (id?: number) => SubTableBinding | undefined
  isBindingModeEditable: (bindingMode: string | undefined | null) => boolean
  /**
   * Page-specific reader for a binding's persisted rows out of `__subTables__`. Injected
   * rather than imported: To Do, My Requests and New Request each resolve the slice
   * differently, and hard-coding one makes the other two silently diverge.
   */
  getSavedRowsForBinding?: (binding: SubTableBinding) => SubTableRow[] | undefined
  /** Emits `update:subTableData` upward; the host page owns the `__subTables__` map. */
  handleSubTableUpdate: (bindingId: number, rows: SubTableRow[]) => void
  /**
   * Task-node field permissions (`TaskFormData.fieldPermissions`); composite
   * `${bindingId}:${fieldName}` entries mark individual inline-form fields READONLY, the same
   * way Process Design's per-field permission panel gates SubTableField's Add/Edit dialog.
   */
  fieldPermissions?: () => Record<string, string> | null | undefined
  /**
   * Current MI sub-task's own participant row id (typically `variables._currentItem.rowId`) —
   * `null`/absent for a plain non-MI single-row binding, which keeps the `rows[0]` fallback.
   */
  currentMiRowId?: () => number | string | null | undefined
}

/** A sub-table row: field name -> value, shape defined by the bound table's design. */
type SubTableRow = Record<string, unknown>

export function useInlineSubFormComponent(deps: InlineSubFormDeps) {
  const { resolveBinding, isBindingModeEditable } = deps

  /**
   * Fields of the bound sub-form, with FK/PK metadata overlaid.
   *
   * <p>Cycle guard: an Inline Form's own sub-form can itself contain another Inline Form widget
   * (nested placement is reachable — see docs/design/inline-sub-form-component.md §关键约束 3 —
   * DW's designer-time restriction only covers the main-vs-sub-designer canvas, not depth within
   * a single form's own rule tree). A direct self-reference (binding A embeds an Inline Form
   * pointing at A) or an indirect cycle (A embeds one pointing at B, whose own form embeds one
   * pointing back at A) would recurse forever once the renderer resolves a nested inlineSubForm
   * field's own fields by calling back into this function. `visitedBindingIds` accumulates every
   * binding already resolved along the current render path — passed in by the caller each time
   * it recurses into a nested inlineSubForm field — and any binding revisited within it is pruned
   * instead of expanded. Defaults to an empty set for a fresh top-level call.
   */
  function resolveInlineSubFormFields(
    field: FormField,
    visitedBindingIds: ReadonlySet<number> = new Set<number>(),
  ): FormField[] {
    const binding = resolveBinding(field._bindingId)
    if (!binding) return []
    const fields = Array.isArray(binding.formFields) ? binding.formFields : []
    const selfId = Number(binding.bindingId)
    const nextVisited = new Set(visitedBindingIds)
    nextVisited.add(selfId)
    const pruned = stripVisitedReferences(fields, nextVisited)
    const withFieldDefs = applyFieldDefinitionsToFormFields(pruned, binding.fieldDefinitions)
    return applyFieldPermissionsToInlineFields(withFieldDefs, selfId, deps.fieldPermissions?.())
  }

  /**
   * Marks fields READONLY per composite `${bindingId}:${fieldName}` permission key. No entry
   * for this binding at all → every field unchanged (backward-compatible default for Function
   * Units that never configured sub-table field permissions).
   */
  function applyFieldPermissionsToInlineFields(
    fields: FormField[],
    bindingId: number,
    fieldPermissions: Record<string, string> | null | undefined,
  ): FormField[] {
    if (!fieldPermissions) return fields
    const prefix = `${bindingId}:`
    const hasAnyForBinding = Object.keys(fieldPermissions).some(key => key.startsWith(prefix))
    if (!hasAnyForBinding) return fields
    const walk = (list: FormField[]): FormField[] =>
      list.map(f => {
        const next = Array.isArray(f?.children) && f.children.length
          ? { ...f, children: walk(f.children) }
          : f
        if (!next.key) return next
        const permission = fieldPermissions[`${prefix}${next.key}`]
        if (permission != null && String(permission).toUpperCase() === 'READONLY') {
          return { ...next, readonly: true }
        }
        return next
      })
    return walk(fields)
  }

  /**
   * Drops any nested `inlineSubForm` field whose `_bindingId` is already in `visitedBindingIds`
   * (direct self-reference OR an indirect cycle back to an ancestor along this render path)
   * instead of leaving it for the caller to expand into infinite recursion.
   */
  function stripVisitedReferences(fields: FormField[], visitedBindingIds: ReadonlySet<number>): FormField[] {
    let warned = false
    const walk = (list: FormField[]): FormField[] => {
      const out: FormField[] = []
      for (const f of list) {
        if (f?.type === 'inlineSubForm' && visitedBindingIds.has(Number(f._bindingId))) {
          if (!warned) {
            warned = true
            console.warn(
              `[inlineSubForm] binding ${f._bindingId} revisits an ancestor already on this render path; nested copy dropped to avoid infinite recursion`,
            )
          }
          continue
        }
        if (Array.isArray(f?.children) && f.children.length) {
          out.push({ ...f, children: walk(f.children) })
          continue
        }
        out.push(f)
      }
      return out
    }
    return walk(fields)
  }

  /** Rows currently backing the bound sub-table: live binding data first, persisted slice second. */
  function currentRows(binding: SubTableBinding): SubTableRow[] {
    if (Array.isArray(binding.data) && binding.data.length > 0) return binding.data as SubTableRow[]
    const saved = deps.getSavedRowsForBinding?.(binding)
    return Array.isArray(saved) ? saved : []
  }

  /**
   * Index into `rows` of the row this form edits: the current MI sub-task's own row when
   * `currentMiRowId` identifies one, else index 0 (the plain non-MI single-row case — also the
   * safe fallback when MI matching finds nothing, since {@link findMiIsolatedParentRow} itself
   * degrades to "the only row" when there is exactly one and it doesn't contradict `miRowId`).
   */
  function resolveTargetRowIndex(rows: SubTableRow[]): number {
    if (rows.length === 0) return -1
    const miRowId = deps.currentMiRowId?.()
    if (miRowId != null && String(miRowId).trim() !== '') {
      const matched = findMiIsolatedParentRow(rows, miRowId)
      if (matched) {
        const idx = rows.indexOf(matched)
        if (idx >= 0) return idx
      }
    }
    return 0
  }

  /**
   * The single row this form edits. `null` when the sub-table has no rows yet —
   * SubTableInlineForm maps that to an empty model, which is exactly the blank
   * editable form we want.
   */
  function resolveInlineSubFormRow(field: FormField): SubTableRow | null {
    const binding = resolveBinding(field._bindingId)
    if (!binding) return null
    const rows = currentRows(binding)
    const idx = resolveTargetRowIndex(rows)
    const target = idx >= 0 ? rows[idx] : undefined
    return target && typeof target === 'object' ? { ...target } : null
  }

  /**
   * Follows the bound sub-table's own bindingMode rather than the host form's, matching
   * SubTableField on the same page: a read-only main table can still host an editable sub-table.
   *
   * <p>ACTION bindings are always view-only, regardless of bindingMode — same rule as
   * SubTableField's `editable` computed (`SubTableField.vue`'s `bindingType === 'ACTION'`
   * override). An Action Form Table is a record of what an action produced, not host-editable
   * data, so this must not follow the EDITABLE/READONLY toggle at all.
   */
  function inlineSubFormReadonly(field: FormField): boolean {
    if (deps.readonly()) return true
    const binding = resolveBinding(field._bindingId)
    if (!binding) return true
    if (binding.bindingType === 'ACTION') return true
    return !isBindingModeEditable(binding.bindingMode)
  }

  /** Title shown above the inline form: the bound table's name. */
  function resolveInlineSubFormTitle(field: FormField): string {
    const binding = resolveBinding(field._bindingId)
    return binding?.tableName ? String(binding.tableName) : ''
  }

  /**
   * Merge an edit back into the current MI sub-task's own row (or row 0 for the non-MI case),
   * creating that row on first input when the binding has no rows at all yet. No PK is allocated
   * and no FK is written here — see the PK/FK timing note above.
   *
   * <p>A multi-row binding with no MI row match at all (shouldn't happen in practice — see
   * {@link resolveTargetRowIndex}'s fallback) still merges into index 0 rather than silently
   * dropping the edit; that mirrors the pre-fix behavior for the cases this fix does not change.
   */
  function handleInlineSubFormUpdate(field: FormField, mergedRow: SubTableRow): void {
    const binding = resolveBinding(field._bindingId)
    if (!binding) return
    const rows = [...currentRows(binding)]
    if (rows.length > 0) {
      const idx = Math.max(resolveTargetRowIndex(rows), 0)
      rows[idx] = { ...rows[idx], ...mergedRow }
    } else {
      rows.push({ ...mergedRow })
    }
    deps.handleSubTableUpdate(Number(binding.bindingId), rows)
  }

  return {
    resolveInlineSubFormFields,
    resolveInlineSubFormRow,
    resolveInlineSubFormTitle,
    inlineSubFormReadonly,
    handleInlineSubFormUpdate,
  }
}
