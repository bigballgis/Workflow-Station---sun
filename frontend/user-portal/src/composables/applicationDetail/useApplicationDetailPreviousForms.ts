import type { FormField, FormTab } from '@/components/FormRenderer.vue'
import {
  resolveSubTablePrimaryKeyFields,
  hydrateChildSubTablesFromParentsNestedRows,
  buildBindingIdToRelationTableIdMap,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  enrichChildBindingRowsFromParentsNestedSubTables,
  applySharedAttachmentFinalizeAndMaterialize,
} from '@/composables/tasks/shared'
import { getSavedSubTableRowsFromVariables } from './subTableRowHelpers'
import type { PreviousFormEntry } from './useApplicationDetailState'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailPreviousFormsFns {
  buildPreviousFormEntry: (
    prevForm: any,
    options: { isKnownMiSubTask: boolean; isActiveMiSubTaskStep?: boolean },
    allContentForms?: any[],
    bindingRelationTableMap?: Map<number, number | null>,
  ) => PreviousFormEntry
}

export function createApplicationDetailPreviousForms(ctx: ApplicationDetailCtx): ApplicationDetailPreviousFormsFns {
  const { formData, formLabelWidth } = ctx

  /** Build a read-only PreviousFormEntry from designer form metadata (shared by history + live MI step). */
  function buildPreviousFormEntry(
    prevForm: any,
    options: { isKnownMiSubTask: boolean; isActiveMiSubTaskStep?: boolean },
    allContentForms?: any[],
    bindingRelationTableMap?: Map<number, number | null>,
  ): PreviousFormEntry {
    const savedSubTables = formData.value.__subTables__
    // My Request only consumes previousForms[*].subTableBindings (align / link-form); skip rule walks.
    const parsedFields: FormField[] = []
    const parsedTabs: FormTab[] = []

    let prevFormConfig: Record<string, any> = {}
    try {
      const cfg = typeof prevForm.data === 'string' ? JSON.parse(prevForm.data) : (prevForm.data || {})
      prevFormConfig = cfg || {}
    } catch { /* ignore */ }
    const prevBindings: PreviousFormEntry['subTableBindings'] = []
    for (const b of (prevForm.tableBindings || [])) {
      if (b.bindingType === 'PRIMARY') continue
      const cols = ctx.resolveSubTableBindingColumnsForPortal(b, prevFormConfig, allContentForms)
      if (!Array.isArray(cols) || cols.length === 0) continue
      const prevSubForms = prevFormConfig.subForms || {}
      const subFormDesign = ctx.resolveSubFormDesign(b, prevSubForms)
      const binding = {
        bindingId: b.bindingId,
        tableId: b.tableId != null ? Number(b.tableId) : null,
        bindingType: b.bindingType,
        bindingMode: b.bindingMode,
        foreignKeyField: b.foreignKeyField,
        tableName: b.tableDisplayName || b.tableName,
        physicalTableName: b.tableName,
        // 规范 key 的命名空间靠这两个字段判定 DW / RT；不带上则 rt: 切片解析不到
        relationTableId: (b as { relationTableId?: number | null }).relationTableId ?? null,
        relationTableName: (b as { relationTableName?: string | null }).relationTableName ?? null,
        tableType: b.tableType,
        tableDescription: b.tableDescription,
        columns: cols,
        data: [] as any[],
        subMode: b.subMode,
        formFields: subFormDesign.formFields,
        formOptions: subFormDesign.formOptions,
        assignmentConfig: b.assignmentConfig,
        primaryKeyFields: resolveSubTablePrimaryKeyFields(
          b.primaryKeyFields,
          b.bindingId,
          prevFormConfig
        )
      }
      if (savedSubTables) {
        const saved = getSavedSubTableRowsFromVariables(
          savedSubTables,
          {
            bindingId: b.bindingId,
            tableName: b.tableName,
            tableDisplayName: b.tableDisplayName,
            // 规范 key 的命名空间由「绑的是 DW 还是 RT 表」决定，缺了就会去 dw: 里找 rt: 的数据
            relationTableId: (b as { relationTableId?: number | null }).relationTableId,
            relationTableName: (b as { relationTableName?: string | null }).relationTableName
          },
          binding.primaryKeyFields
        )
        if (Array.isArray(saved)) binding.data = saved
      }
      prevBindings.push(binding)
    }

    if (allContentForms?.length) {
      ctx.mergeLinkFormTargetBindingsInto(prevBindings, allContentForms, prevFormConfig, prevFormConfig.subForms || {})
      ctx.stripLinkOnlySubTableFieldsFromBindings(
        prevBindings,
        prevFormConfig.subForms || {},
        prevFormConfig.rule,
        prevFormConfig,
      )
    }
    if (savedSubTables && typeof savedSubTables === 'object') {
      for (const binding of prevBindings) {
        const raw = (prevForm.tableBindings || []).find((x: any) => Number(x.bindingId) === Number(binding.bindingId))
        const saved = getSavedSubTableRowsFromVariables(
          savedSubTables,
          {
            bindingId: binding.bindingId,
            tableName: raw?.tableName ?? (binding as { physicalTableName?: string }).physicalTableName,
            tableDisplayName: raw?.tableDisplayName ?? binding.tableName,
            relationTableId: raw?.relationTableId ?? (binding as { relationTableId?: number | null }).relationTableId,
            relationTableName: raw?.relationTableName ?? (binding as { relationTableName?: string | null }).relationTableName
          },
          binding.primaryKeyFields
        )
        if (saved) binding.data = saved
      }
      const rtMap =
        bindingRelationTableMap ?? buildBindingIdToRelationTableIdMap(allContentForms || [])
      hydrateChildSubTablesFromParentsNestedRows(prevBindings as any, savedSubTables as Record<string, unknown>, rtMap)
      hydrateBindingsRowsFromVariablesBySharedRelationTableId(
        prevBindings as any,
        savedSubTables as Record<string, unknown>,
        rtMap
      )
      enrichChildBindingRowsFromParentsNestedSubTables(prevBindings as any)
      applySharedAttachmentFinalizeAndMaterialize(prevBindings, formData.value as Record<string, unknown>, {
        flattened: savedSubTables as Record<string, unknown>,
        bindingTableById: rtMap,
      })
    }

    return {
      formId: String(prevForm.id),
      formName: prevForm.name,
      labelWidth: formLabelWidth.value,
      fields: parsedFields,
      tabs: parsedTabs,
      isMiSubTask: options.isKnownMiSubTask,
      ...(options.isActiveMiSubTaskStep === true ? { isActiveMiSubTaskStep: true } : {}),
      subTableBindings: prevBindings
    }
  }

  return {
    buildPreviousFormEntry,
  }
}
