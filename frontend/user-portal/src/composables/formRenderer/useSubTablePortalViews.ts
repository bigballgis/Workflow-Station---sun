import { nextTick, type ComputedRef } from 'vue'
import {
  mergeSubTablePortalViewsForRuntime,
  resolveSubTableDisplayMode,
  shouldSuppressStandaloneSubTableInInitiatorRequest,
} from '../../components/formRendererHelpers'
import {
  isMiDashboardSubTableBinding,
} from '../tasks/shared'
import { applyFieldDefinitionsToFormFields } from '../../utils/subTableRowRuntime'
import type {
  FormField,
  PortalViewContext,
  SubTablePortalViews,
} from '../../components/formRendererHelpers'
import type { SubTableBinding } from './useSubTableBindings'

interface PortalViewsDeps {
  viewContext: () => PortalViewContext | undefined
  nativeSubTableBindingIds: () => number[] | undefined
  formConfig: () => Record<string, unknown> | null | undefined
  readonly: () => boolean
  resolveBinding: (id?: number) => SubTableBinding | undefined
  linkableSubTableBindings: ComputedRef<SubTableBinding[] | undefined>
  isBindingModeEditable: (bindingMode: string | undefined | null) => boolean
}

export function useSubTablePortalViews(deps: PortalViewsDeps) {
  const { resolveBinding, isBindingModeEditable } = deps

  function subTableMode(field: FormField): 'tableOnly' | 'formBelowTable' | 'summaryWithLinkFormModal' {
    const binding = resolveBinding(field._bindingId)
    const merged = mergeSubTablePortalViewsForRuntime(field.portalViews, binding?.portalViews)
    return resolveSubTableDisplayMode(merged, deps.viewContext() ?? 'initiatorRequest')
  }

  function mergedPortalViewsForSubTable(field: FormField): SubTablePortalViews {
    const binding = resolveBinding(field._bindingId)
    return mergeSubTablePortalViewsForRuntime(field.portalViews, binding?.portalViews)
  }

  /** My Request: link-form targets (e.g. subtable2) render only via Link Form modal, not duplicate tables. */
  function shouldRenderPlacedSubTableField(field: FormField): boolean {
    if (deps.viewContext() !== 'initiatorRequest') return true
    if (field._bindingId == null) return true
    const binding = resolveBinding(field._bindingId)
    if (!binding) return false
    const merged = mergeSubTablePortalViewsForRuntime(field.portalViews, binding?.portalViews)
    const nativeBindingIds = deps.nativeSubTableBindingIds()
    const nativeIds = nativeBindingIds?.length
      ? new Set(nativeBindingIds.map(Number))
      : null
    return !shouldSuppressStandaloneSubTableInInitiatorRequest(
      field._bindingId,
      deps.linkableSubTableBindings.value ?? [],
      merged,
      nativeIds,
      deps.formConfig(),
    )
  }

  /** 发起人「汇总 + Link/Details」：子表单元格内不展开 lookup / 用户快照明细，与设计师意图一致。 */
  function subTableCompactLookupCells(field: FormField): boolean {
    if (deps.viewContext() !== 'initiatorRequest') return false
    return subTableMode(field) === 'summaryWithLinkFormModal'
  }

  /**
   * 办理人待办 + 表格下内联表单：无论「表单来源」是 subForm 还是 Link 子表，只要列上存在 linkForm，
   * 点击链接只滚动到下方内联区，不打开 Link 弹层（与设计师 form below table 单一路径一致）。
   */
  function linkFormScrollToInlineEnabled(field: FormField): boolean {
    if (deps.viewContext() !== 'assigneeTodo') return false
    return subTableMode(field) === 'formBelowTable'
  }

  const subTableInlineAnchors = new Map<number, HTMLElement>()
  function setSubTableInlineAnchor(bindingId: number | undefined, el: HTMLElement | null) {
    if (bindingId == null) return
    if (el) subTableInlineAnchors.set(bindingId, el)
    else subTableInlineAnchors.delete(bindingId)
  }

  function scrollSubTableInlineIntoView(bindingId: number | undefined) {
    if (bindingId == null) return
    nextTick(() => {
      subTableInlineAnchors.get(bindingId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
  }

  function subTableShowTaskStatusInitiator(field: FormField): boolean {
    // Assignee To Do: MI collection (e.g. Assign Task "Title" card → Sub Task) shows per-row Status,
    // matching the runtime MI dashboard. Rendered in-place inside the designer card (design parity).
    if (deps.viewContext() === 'assigneeTodo') {
      const binding = resolveBinding(field._bindingId)
      return !!binding && isMiDashboardSubTableBinding(binding)
    }
    if (deps.viewContext() !== 'initiatorRequest') return false
    if (subTableMode(field) !== 'summaryWithLinkFormModal') return false
    // Initiator + summary+Link Form: list columns come from designer `subListViews`; runtime Status/Actions
    // duplicate MI state and designer Actions/Detail columns (My Request / subform_copy).
    return false
  }

  function subTableShowViewDetailInitiator(field: FormField): boolean {
    if (deps.viewContext() !== 'initiatorRequest') return false
    if (subTableMode(field) !== 'summaryWithLinkFormModal') return false
    return false
  }

  /** Form-below 「表单来源」— follows merged portal views (aligned with developer-workstation preview). */
  function resolveAssigneeTodoFormSource(field: FormField): {
    type: 'subForm' | 'linkForm' | 'formId'
    formId?: number | string | null
    linkFormColumnId?: number | string | null
  } {
    const src = mergedPortalViewsForSubTable(field).assigneeTodoFormSource ?? {
      type: 'subForm',
      formId: null,
      linkFormColumnId: null
    }
    return {
      type: src.type,
      formId: src.formId ?? null,
      linkFormColumnId: src.linkFormColumnId ?? null
    }
  }

  /**
   * For a placed sub-table `field`, resolve which Link Form column on the binding's
   * list view drives the inline form-below-table when `assigneeTodoFormSource.type === 'linkForm'`,
   * then return that column's target sub-table binding.
   *
   * Selection precedence:
   *   1. Explicit `assigneeTodoFormSource.linkFormColumnId` (designer pick) — matches the
   *      column whose `props.componentId` equals the configured id.
   *   2. Legacy fallback — the first `type='linkForm'` column on the binding.
   *
   * Returns null when no Link Form column is configured or the target binding isn't loaded;
   * caller falls back to the binding's own subForm in that case.
   */
  function findLinkFormTargetBinding(field: FormField): SubTableBinding | null {
    const binding = resolveBinding(field._bindingId)
    if (!binding) return null
    const cols = Array.isArray(binding.columns) ? binding.columns : []
    const source = resolveAssigneeTodoFormSource(field)
    const picked = source.linkFormColumnId
    const pickedKey = picked != null && String(picked).trim() !== '' ? String(picked) : null

    // Helper: read `componentId` off a column regardless of whether it's nested under
    // `props` (live designer state) or hoisted directly (some serialized shapes).
    const componentIdOf = (col: any): string | null => {
      const cid = col?.props?.componentId ?? col?.componentId
      return cid != null ? String(cid) : null
    }
    const targetBindingIdOf = (col: any): number | null => {
      const t = col?.props?.boundSubTableBindingId ?? col?.boundSubTableBindingId
      return t != null ? Number(t) : null
    }

    if (pickedKey) {
      for (const col of cols) {
        if (!col || col.type !== 'linkForm') continue
        if (componentIdOf(col) !== pickedKey) continue
        const targetId = targetBindingIdOf(col)
        if (targetId == null) continue
        const target = resolveBinding(targetId)
        if (target) {
          return target
        }
      }
      // Picked id no longer exists (e.g. column was removed) — fall through to legacy first-match.
    }

    for (const col of cols) {
      if (!col || col.type !== 'linkForm') continue
      const targetId = targetBindingIdOf(col)
      if (targetId == null) continue
      const target = resolveBinding(targetId)
      if (target) {
        return target
      }
    }
    return null
  }

  /**
   * Decide which binding's data should actually back the inline form-below-table.
   * - `subForm` (or unsupported `formId`): keep the field's own binding.
   * - `linkForm`: switch to the Link Form's target binding so the inline form mirrors
   *   exactly what would show in the Link Form modal — keeping designer and runtime
   *   contracts aligned. Falls back to the own binding when no Link Form column exists,
   *   so a misconfiguration never produces an empty section.
   */
  function resolveInlineFormSourceBinding(field: FormField): SubTableBinding | null {
    const own = resolveBinding(field._bindingId)
    if (!own) return null
    const source = resolveAssigneeTodoFormSource(field)
    // Only an explicit designer pick of `linkForm` switches the inline schema.
    // A leftover Link Form *column* on the list must not steal this binding's
    // sub-form options — Add/Edit dialog always uses the placed binding, and
    // form-below-table Event scripts live on that same `formOptions`.
    if (source.type === 'linkForm') {
      const target = findLinkFormTargetBinding(field)
      if (target) {
        return target
      }
    }
    // `formId` is not yet runtime-resolved here (would need cross-form schema lookup); fall through.
    return own
  }

  /**
   * 「表格下内联表单」应对齐 Link 目标子表/自身子表的 bindingMode，而不是 {@code primaryReadOnly}
   * （主表只读时子表仍可编辑 — 与同页的 {@code SubTableField} 一致）。
   */
  function inlineSubTableFormReadonly(field: FormField): boolean {
    if (deps.readonly()) return true
    const src = resolveInlineFormSourceBinding(field)
    if (!src) return true
    return !isBindingModeEditable(src.bindingMode)
  }

  /** 内联表单标题：与字段/schema 来源一致（linkForm→subtable2 时显示子表名，而非父表）。 */
  function resolveInlineFormTableTitle(field: FormField): string {
    const src = resolveInlineFormSourceBinding(field)
    if (src?.tableName) return String(src.tableName)
    const own = resolveBinding(field._bindingId)
    return own?.tableName ? String(own.tableName) : ''
  }

  /**
   * Resolve the form schema for the inline form-below-table. Per the designer contract:
   *   - `subForm` (default): use the binding's own `formFields`
   *   - `linkForm`: use the Link Form target binding's `formFields`
   *   - `formId`: not yet runtime-supported; falls back to `subForm`
   */
  function resolveInlineFormFields(field: FormField): FormField[] {
    const source = resolveInlineFormSourceBinding(field)
    const own = resolveBinding(field._bindingId)
    const fields = Array.isArray(source?.formFields) ? source!.formFields : []
    return applyFieldDefinitionsToFormFields(fields, source?.fieldDefinitions ?? own?.fieldDefinitions)
  }

  return {
    subTableMode,
    mergedPortalViewsForSubTable,
    shouldRenderPlacedSubTableField,
    subTableCompactLookupCells,
    linkFormScrollToInlineEnabled,
    setSubTableInlineAnchor,
    scrollSubTableInlineIntoView,
    subTableShowTaskStatusInitiator,
    subTableShowViewDetailInitiator,
    resolveAssigneeTodoFormSource,
    findLinkFormTargetBinding,
    resolveInlineFormSourceBinding,
    inlineSubTableFormReadonly,
    resolveInlineFormTableTitle,
    resolveInlineFormFields,
  }
}
