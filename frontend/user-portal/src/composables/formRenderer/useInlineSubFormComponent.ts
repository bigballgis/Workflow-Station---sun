import { applyFieldDefinitionsToFormFields } from '../../utils/subTableRowRuntime'
import { findMiIsolatedParentRow } from '../tasks/miLinkChildRows'
import {
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
} from '../tasks/subTableBindingKinds'
import {
  miLinkChildRowBelongsToParticipant,
  miChildFkConfigOfBinding,
  resolveMiChildStructuralParentFk,
} from '../tasks/miLinkChildIdentity'
import { normalizeMiLinkMatchId } from '../tasks/internal'
import {
  resolveMiBindingKindFromConfig,
  type MiKindContext,
} from '../tasks/miBindingKindFromConfig'
import type { FormField } from '../../components/formRendererHelpers'
import { resolveSubTablePrimaryKeyFields } from '../tasks/useMiConfig'
import type { SubTableBinding } from './useSubTableBindings'

/**
 * Runtime for the `inlineSubForm` widget: the bound SUB table's designed form laid out
 * IN PLACE on the host form — no grid above it, no dialog, no save button of its own.
 *
 * <p>Exactly one row of the target binding is edited — the current MI sub-task's own row when
 * {@code currentMiRowId} identifies one, else `rows[0]` for the plain non-MI single-row case.
 * Which matcher finds "its own row" depends on how the bound table is keyed: an MI collection row
 * carries the participant key itself (matched via {@link findMiIsolatedParentRow}, the same PK-based
 * matcher `useSubTableBindings`/`useInlineSubTableForm` use), whereas a participant-scoped CHILD
 * table (People-style) is keyed by a structural FK and is matched by participant ownership with no
 * index-0 fallback — see {@link resolveTargetRowIndex}. Multi-row bindings are common even outside MI
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
  /**
   * MI collection / 主表的 tableId —— binding 分类的配置判据（见 {@link resolveMiBindingKindFromConfig}）。
   * 省略时分类退回列名启发式，所以能提供的调用方都应该传。
   */
  miKindContext?: () => MiKindContext | null | undefined
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
   *
   * <p><b>Exception — participant-scoped child tables.</b> When the bound table is a link-child
   * whose rows belong to individual MI participants (People-style, structural FK to the participant
   * row), `rows` is the cross-participant pool and index 0 is very likely SOMEONE ELSE's row.
   * Falling back to it does not merely display the wrong data: {@link handleInlineSubFormUpdate}
   * merges the edit into that same index, so typing here overwrites another sub-task's row. There
   * is no safe default in that case — return -1 so the form renders blank and a first edit creates
   * this participant's own row instead. The index-0 fallback is kept for every other binding, where
   * a single-row table genuinely is "the" row (and MI collection rows still match by PK above).
   */
  function resolveTargetRowIndex(rows: SubTableRow[], binding: SubTableBinding): number {
    if (rows.length === 0) return -1
    const miRowId = deps.currentMiRowId?.()
    if (miRowId != null && String(miRowId).trim() !== '') {
      /**
       * Link-child rows are keyed to their participant by a structural FK (`sub_task_id`), NOT by
       * `id_idw`/`id` — those are the row's OWN pk. {@link findMiIsolatedParentRow} matches on the
       * latter and additionally degrades to "the only row" when there is exactly one, so on a
       * link-child pool it both fails to find this participant's real row AND happily returns a
       * sibling's. Match on participant ownership instead, and accept no substitute.
       */
      if (bindingIsMiLinkChild(binding)) {
        // FK 列名来自设计器字段定义（binding.fieldDefinitions），不猜列名 —— demo FU 把
        // sub_task_id 改名成 sub_task_idk 后，猜名字的旧实现两个方向都答错了。
        const fkConfig = miChildFkConfigOfBinding(binding)
        const idx = rows.findIndex(r => miLinkChildRowBelongsToParticipant(r, miRowId, fkConfig))
        if (idx >= 0) return idx
        // A row with no participant identity yet is this participant's own in-progress row
        // (the FK is only seeded at save), so it is writable — but a FOREIGN row never is.
        // 「有没有自己的主键」按设计器 PK 判定，**不猜 'id_idw'**：猜错会恒判为「无 PK」，
        // 把别人已保存的行当成自己的空行接着编辑（覆盖他人数据）。
        // 无主键时返回 -1（渲染空表单、首次编辑创建自己的行），而不是抛错 ——
        // 抛错会中断整个 Save。
        const pkNames = (binding.primaryKeyFields ?? [])
          .map(f => String(f ?? '').trim())
          .filter(Boolean)
        if (pkNames.length === 0) return -1
        const rowHasOwnPk = (r: SubTableRow): boolean =>
          pkNames.some(name => !!normalizeMiLinkMatchId((r as Record<string, unknown>)[name]))
        const fresh = rows.findIndex(
          r => !resolveMiChildStructuralParentFk(r, fkConfig) && !rowHasOwnPk(r),
        )
        return fresh
      }
      // 按表的种类解析主键：子任务表缺主键 = 配置错误（抛错）；其它表（共享附件 main_id、
      // 非 MI 单行子表）允许没有主键 —— 它们本就不是按参与者分片的，跳到下面的 index-0 分支。
      const pk = resolveSubTablePrimaryKeyFields(binding)
      if (pk) {
        const matched = findMiIsolatedParentRow(rows, miRowId, pk)
        if (matched) {
          const idx = rows.indexOf(matched)
          if (idx >= 0) return idx
        }
      }
    }
    return 0
  }

  /**
   * A participant-scoped child table (People, subtable2, …) as opposed to the MI collection itself
   * or a shared process-level table. The collection's own rows are matched by PK above, so only
   * child bindings need participant-ownership matching.
   */
  function bindingIsMiLinkChild(binding: SubTableBinding): boolean {
    const ctx = deps.miKindContext?.()
    // 配置判据优先：collection / shared 都不是 link-child，且都不该按参与者 FK 找行。
    // 这正是本 bug 的修复点 —— 真 collection 的 foreignKeyField='id' 曾让列名启发式判成
    // link-child，去找一个不存在的参与者 FK，于是 Inline Form 空白 + 保存追加孤儿行。
    const kind = resolveMiBindingKindFromConfig(binding, ctx)
    if (kind != null) return kind === 'participant-child'
    return (
      isMiParticipantScopedSubTableBinding(binding, ctx)
      && !isMiDashboardSubTableBinding(binding)
    )
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
    const idx = resolveTargetRowIndex(rows, binding)
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
   * <p>When {@link resolveTargetRowIndex} finds no row this participant may write to, the edit
   * APPENDS a new row rather than being coerced into index 0. Clamping a "no match" to 0 meant an
   * MI participant whose own row was absent from a participant-scoped child binding silently
   * overwrote whichever sibling's row happened to sort first — a cross-sub-task data loss, not just
   * a display glitch.
   */
  function handleInlineSubFormUpdate(field: FormField, mergedRow: SubTableRow): void {
    const binding = resolveBinding(field._bindingId)
    if (!binding) return
    const rows = [...currentRows(binding)]
    const idx = rows.length > 0 ? resolveTargetRowIndex(rows, binding) : -1
    if (idx >= 0) {
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
