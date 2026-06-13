import {
  resolveSubTableDisplayMode,
  filterLinkOnlyStandaloneSubTableFields,
} from '@/components/formRendererHelpers'
import {
  resolveSubTablePrimaryKeyFields,
  buildBindingIdToRelationTableIdMap,
  cloneSubTableRows,
  pullNestedRowsForBindingFromParentRows,
  enrichChildBindingRowsFromParentsNestedSubTables,
} from '@/composables/tasks/shared'
import { isMultiInstanceStyleSubTableRow } from './subTableRowHelpers'
import type { ApplicationDetailState } from './useApplicationDetailState'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailSubTaskDialogFns {
  shouldShowBindingDetailsModal: (binding: { portalViews?: any; data?: any[] }) => boolean
  shouldShowBindingTaskStatus: (binding: { portalViews?: any; data?: any[] }) => boolean
  shouldShowBindingFormBelow: (binding: { portalViews?: any; formFields?: any[] }) => boolean
  bindingCompactLookupCells: (binding: { portalViews?: any }) => boolean
  resolveSubTaskSiblingRows: (siblingRowsOverride?: any[] | null) => any[]
  shouldMergeProcessVariablesIntoSubTaskDetailRow: (row: any, siblingRowsOverride?: any[] | null) => boolean
  buildSubTableBindingsForForm: (
    formMeta: { tableBindings?: any[] },
    formConfig: Record<string, any>,
    parentRow?: Record<string, any> | null,
  ) => ApplicationDetailState['subTaskDetailSubTableBindings']['value']
  openSubTaskDetailDialog: (row: any, siblingRowsOverride?: any[] | null) => void
}

export function createApplicationDetailSubTaskDialog(ctx: ApplicationDetailCtx): ApplicationDetailSubTaskDialogFns {
  const {
    t,
    formData,
    linkableSubTableBindings,
    subTaskDetailVisible,
    subTaskDetailTitle,
    subTaskDetailFields,
    subTaskDetailData,
    subTaskDetailSubTableBindings,
    subTaskFormSchema,
    subTaskFormId,
    hasSubTaskFormSchema,
  } = ctx

  /**
   * Decide whether a bottom (unplaced) sub-table binding should expose a "Details" link
   * that opens the sub-task form modal. Per the designer-driven contract:
   *   - Explicit binding-level `initiatorRequest=summaryWithLinkFormModal` → always show
   *   - Explicit binding-level `tableOnly` or `mirrorTodo` → never show summary modal
   *   - No binding-level config → legacy heuristic preserved (hasSubTaskFormSchema && hasTaskStatusData)
   */
  function shouldShowBindingDetailsModal(binding: { portalViews?: any; data?: any[] }): boolean {
    const mode = binding?.portalViews?.initiatorRequest
    // Match FormRenderer: designer list owns Actions/Detail when initiator uses summary+Link Form.
    if (mode === 'summaryWithLinkFormModal') return false
    if (mode === 'tableOnly' || mode === 'mirrorTodo') return false
    return hasSubTaskFormSchema.value && ctx.hasTaskStatusData(binding.data || [])
  }

  /** Task status column for unplaced bindings; aligns with placed sub-tables in FormRenderer (summary MI view). */
  function shouldShowBindingTaskStatus(binding: { portalViews?: any; data?: any[] }): boolean {
    const mode = binding?.portalViews?.initiatorRequest
    if (mode === 'summaryWithLinkFormModal') return false
    if (mode === 'tableOnly' || mode === 'mirrorTodo') return false
    return hasSubTaskFormSchema.value && ctx.hasTaskStatusData(binding.data || [])
  }

  /**
   * Decide whether to render an inline form below an unplaced sub-table in My Request.
   * Mirrors the rendering rule used by FormRenderer for placed sub-tables: `formBelowTable`
   * (either explicitly via initiatorRequest, or via mirrorTodo → assigneeTodo). When the
   * binding has no portalViews configured, defaults to NO inline form (legacy behavior).
   */
  function shouldShowBindingFormBelow(binding: { portalViews?: any; formFields?: any[] }): boolean {
    if (!Array.isArray(binding.formFields) || binding.formFields.length === 0) return false
    return resolveSubTableDisplayMode(binding.portalViews, 'initiatorRequest') === 'formBelowTable'
  }

  /** Aligns with FormRenderer: summary + Link/Details → compact cells (no inline lookup backfill block). */
  function bindingCompactLookupCells(binding: { portalViews?: any }): boolean {
    return resolveSubTableDisplayMode(binding.portalViews, 'initiatorRequest') === 'summaryWithLinkFormModal'
  }

  /**
   * Rows considered for MI / variable-merge decisions. Prefer the emitting sub-table's `data`
   * so we do not mix unrelated bindings from {@link getMiRows}.
   */
  function resolveSubTaskSiblingRows(siblingRowsOverride?: any[] | null): any[] {
    if (Array.isArray(siblingRowsOverride)) return siblingRowsOverride
    return ctx.getMiRows()
  }

  /**
   * Merge process variables only when safe: terminal MI rows (legacy payload on variables), or non-MI rows.
   * Open MI participants — including bare relation rows that only have `id` beside completed siblings — must not
   * inherit another row's submission from {@link formData.value}.
   */
  function shouldMergeProcessVariablesIntoSubTaskDetailRow(row: any, siblingRowsOverride?: any[] | null): boolean {
    const ts = String(row.task_status ?? '').toUpperCase()
    if (ts === 'COMPLETED' || ts === 'REJECTED') return true

    const siblings = resolveSubTaskSiblingRows(siblingRowsOverride)
    const processUsesPerRowTaskStatus = siblings.some(
      (r: any) => r && r.task_status !== undefined && r.task_status !== null
    )
    if (processUsesPerRowTaskStatus) return false

    // Same sub-table has 2+ rows: instance-level variables often hold the last submitter only (API may omit task_status).
    if (siblings.length >= 2) return false

    if (isMultiInstanceStyleSubTableRow(row)) return false

    const hasMiSibling = siblings.some(
      (r: any) => r && r !== row && isMultiInstanceStyleSubTableRow(r)
    )
    if (hasMiSibling && row && typeof row === 'object') {
      const pk = row.id
      if (pk != null && pk !== '') return false
    }

    return true
  }

  /** Build sub-table bindings for a nested form (MI sub-task / Link Form target) — parity with main form load path. */
  function buildSubTableBindingsForForm(
    formMeta: { tableBindings?: any[] },
    formConfig: Record<string, any>,
    parentRow?: Record<string, any> | null,
  ): typeof subTaskDetailSubTableBindings.value {
    const bindings: typeof subTaskDetailSubTableBindings.value = []
    const subFormsPayload = formConfig.subForms || {}
    const subTablePortalViewsPayload = formConfig.subTablePortalViews || {}
    for (const b of formMeta.tableBindings || []) {
      if (b.bindingType === 'PRIMARY') continue
      const columns = ctx.deriveColumnsFromBinding(b, formConfig)
      if (!Array.isArray(columns) || columns.length === 0) continue
      const subFormDesign = ctx.resolveSubFormDesign(b, subFormsPayload)
      const bindingPortalViews =
        subTablePortalViewsPayload[b.bindingId]
        ?? subTablePortalViewsPayload[String(b.bindingId)]
        ?? null
      bindings.push({
        bindingId: b.bindingId,
        tableId: b.tableId != null ? Number(b.tableId) : null,
        bindingType: b.bindingType,
        bindingMode: b.bindingMode,
        foreignKeyField: b.foreignKeyField,
        tableName: b.tableDisplayName || b.tableName,
        physicalTableName: b.tableName,
        tableType: b.tableType,
        tableDescription: b.tableDescription,
        columns,
        data: [] as any[],
        subMode: b.subMode,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        portalViews: bindingPortalViews,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(
          b.primaryKeyFields,
          b.bindingId,
          formConfig,
        ),
      })
    }
    ctx.mergeLinkFormTargetBindingsInto(
      bindings,
      ctx.cachedContentForms,
      formConfig,
      subFormsPayload,
    )
    ctx.stripLinkOnlySubTableFieldsFromBindings(bindings, subFormsPayload, formConfig.rule, formConfig)
    const rtMap = buildBindingIdToRelationTableIdMap(ctx.cachedContentForms)
    if (parentRow && typeof parentRow === 'object') {
      for (const binding of bindings) {
        const nested = pullNestedRowsForBindingFromParentRows(
          {
            bindingId: binding.bindingId,
            tableName: binding.tableName,
            physicalTableName: binding.physicalTableName,
            tableId: binding.tableId ?? null,
          },
          [parentRow],
          rtMap.size > 0 ? rtMap : undefined,
        )
        if (nested.length > 0) {
          binding.data = cloneSubTableRows(nested)
        }
      }
      enrichChildBindingRowsFromParentsNestedSubTables(bindings)
    }
    for (const binding of bindings) {
      if (Array.isArray(binding.data) && binding.data.length > 0) continue
      const peer = linkableSubTableBindings.value.find(
        x => Number(x.bindingId) === Number(binding.bindingId),
      )
      if (peer?.data?.length) {
        binding.data = cloneSubTableRows(peer.data)
      }
    }
    return bindings
  }

  function openSubTaskDetailDialog(row: any, siblingRowsOverride?: any[] | null) {
    if (!subTaskFormSchema.value) return
    const schema = subTaskFormSchema.value
    const formRules =
      schema.rule && Array.isArray(schema.rule) ? schema.rule : (Array.isArray(schema) ? schema : [])

    const formMeta =
      (subTaskFormId.value
        ? ctx.cachedContentForms.find((f: any) => String(f.id) === subTaskFormId.value)
        : null)
      ?? (schema._formName
        ? ctx.cachedContentForms.find((f: any) => f.name === schema._formName)
        : null)
    subTaskDetailSubTableBindings.value = formMeta
      ? buildSubTableBindingsForForm(formMeta, schema, row)
      : []

    const rawFields = ctx.extractFieldsRecursive(formRules)
    subTaskDetailFields.value = filterLinkOnlyStandaloneSubTableFields(
      rawFields,
      subTaskDetailSubTableBindings.value,
      formRules,
      undefined,
      schema,
    )

    const mergedData: Record<string, any> = { ...row }
    // Fallback: for MI form fields absent from the row, use process-level variables.
    // Legacy saves only; never for open MI rows — variables often mirror another participant's submission.
    const allowVarFallback = shouldMergeProcessVariablesIntoSubTaskDetailRow(row, siblingRowsOverride)
    if (allowVarFallback) {
      for (const f of rawFields) {
        if (f.key && (mergedData[f.key] === undefined || mergedData[f.key] === null)) {
          if (formData.value[f.key] !== undefined) {
            mergedData[f.key] = formData.value[f.key]
          }
        }
      }
    }
    subTaskDetailData.value = mergedData

    const formTitle = subTaskFormSchema.value._formName || t('applicationDetail.subTaskFormTitle')
    subTaskDetailTitle.value = row.assignee_display_name
      ? `${formTitle} — ${row.assignee_display_name}`
      : formTitle
    subTaskDetailVisible.value = true
  }

  return {
    shouldShowBindingDetailsModal,
    shouldShowBindingTaskStatus,
    shouldShowBindingFormBelow,
    bindingCompactLookupCells,
    resolveSubTaskSiblingRows,
    shouldMergeProcessVariablesIntoSubTaskDetailRow,
    buildSubTableBindingsForForm,
    openSubTaskDetailDialog,
  }
}
