import {
  collectPlacedSubTableBindingIds,
  mergeMissingSubTableFieldsIntoLayout,
  ensureSubTableBindingsOnFormLayout,
  legacyBindingIdAliases,
} from '@/components/formRendererHelpers'
import {
  resolveSubTablePrimaryKeyFields,
  isMiDashboardSubTableBinding,
  finalizeMiCollectionSubTableBindingRows,
} from '@/composables/tasks/shared'
import {
  cloneSubTableRows,
  bindingIdsPreferStrictSubTableLookup,
} from './subTableRowUtils'
import type { TaskDetailState } from './useTaskDetailState'
import type { TaskDetailCtx } from './context'

export interface TaskDetailLayoutSyncFns {
  ensureSubTableBindingsFromLayoutAndConfig: (
    bindings: TaskDetailState['subTableBindings']['value'],
    formConfig: Record<string, any>,
  ) => void
  syncFormLayoutWithSubTableBindings: () => void
}

export function createTaskDetailLayoutSync(ctx: TaskDetailCtx): TaskDetailLayoutSyncFns {
  const {
    subTableBindings,
    fuFormSubTableFields,
    mainFormConfig,
  } = ctx
  const { formFields, formTabs, formFieldsAfterTabs } = ctx.taskForm

  /** Synthesize bindings for canvas subTable widgets when FU tableBindings metadata is missing/stale. */
  function ensureSubTableBindingsFromLayoutAndConfig(
    bindings: typeof subTableBindings.value,
    formConfig: Record<string, any>,
  ) {
    const placed = collectPlacedSubTableBindingIds(
      formFields.value,
      formTabs.value,
      formFieldsAfterTabs.value,
    )
    if (placed.size === 0) return
    const have = new Set(bindings.map(b => Number(b.bindingId)))
    const subForms = (formConfig?.subForms ?? {}) as Record<string, any>

    for (const bid of placed) {
      if (legacyBindingIdAliases(bid).some(alias => have.has(alias))) continue
      const hit = ctx.findRawBindingInForms(ctx.cachedContentForms, bid)
      const cfg = hit?.formConfig ?? formConfig
      const raw = hit?.raw ?? {
        bindingId: bid,
        bindingType: 'SUB',
        bindingMode: 'EDITABLE',
        tableName: '',
        tableDisplayName: '',
      }
      const columns = ctx.deriveColumnsFromBinding(raw, cfg.subForms ?? subForms, cfg)
      if (!Array.isArray(columns) || columns.length === 0) continue
      const subFormDesign = ctx.resolveSubFormDesign(raw, cfg.subForms ?? subForms)
      bindings.push({
        bindingId: bid,
        tableId: raw.tableId ?? null,
        bindingType: raw.bindingType ?? 'SUB',
        bindingMode: raw.bindingMode ?? 'EDITABLE',
        foreignKeyField: raw.foreignKeyField ?? null,
        tableName: raw.tableDisplayName || raw.tableName || `Sub Table ${bid}`,
        physicalTableName: raw.tableName,
        tableType: raw.tableType ?? '',
        tableDescription: raw.tableDescription ?? '',
        columns,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        assignmentConfig: raw.assignmentConfig,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(raw.primaryKeyFields, bid, cfg),
        fieldDefinitions: raw.fieldDefinitions ?? [],
        bindingLinkMode: raw.bindingLinkMode,
        data: [],
      } as any)
      have.add(bid)
    }

    const saved = ctx.miSubTaskSubTablesLoadSource()
    if (saved) {
      const ambiguous = bindingIdsPreferStrictSubTableLookup(bindings)
      for (const binding of bindings) {
        if (Array.isArray(binding.data) && binding.data.length > 0) continue
        const rows = ctx.getSavedSubTableRows(saved, binding, ambiguous.has(binding.bindingId))
        if (rows?.length) {
          binding.data = cloneSubTableRows(
            isMiDashboardSubTableBinding(binding)
              ? finalizeMiCollectionSubTableBindingRows(rows, binding)
              : rows,
          )
        }
      }
    }
  }

  function syncFormLayoutWithSubTableBindings() {
    ensureSubTableBindingsFromLayoutAndConfig(subTableBindings.value, mainFormConfig.value)

    const layout = {
      fields: formFields.value,
      tabs: formTabs.value,
      fieldsAfterTabs: formFieldsAfterTabs.value,
    }
    if (fuFormSubTableFields.value.length > 0) {
      const activeIds = new Set(
        subTableBindings.value.map(b => Number(b.bindingId)).filter(n => Number.isFinite(n)),
      )
      mergeMissingSubTableFieldsIntoLayout(layout, fuFormSubTableFields.value, activeIds)
    }
    ensureSubTableBindingsOnFormLayout(
      layout,
      subTableBindings.value,
      mainFormConfig.value,
    )
    // Native SUB tables (e.g. Assign Task "Title" card → Sub Task / Attachment) render in-place inside
    // the designer card via FormRenderer — design parity with developer-workstation Form Preview.
    formFields.value = layout.fields
    formTabs.value = layout.tabs
    formFieldsAfterTabs.value = layout.fieldsAfterTabs
    /** Layout sync may synthesize bindings + reload slices — re-scope MI sub-task participant rows. */
    ctx.reScopeMiSubTaskParticipantBindings()
    ctx.forceSeedMiCollectionBindingForCurrentParticipant()
  }

  return {
    ensureSubTableBindingsFromLayoutAndConfig,
    syncFormLayoutWithSubTableBindings,
  }
}
