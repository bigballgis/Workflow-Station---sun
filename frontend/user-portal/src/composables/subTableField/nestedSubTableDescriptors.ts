import { collectSubTableFieldsFromLayout } from '@/components/formRendererHelpers'
import type { FormField } from '@/components/formRendererHelpers'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import type { NestedSubTableDescriptor, SubTableBinding } from './subTableFieldTypes'

/**
 * Nested sub-tables (sub-table-in-sub-table) placed in a binding's own form design.
 * They render inside that binding's Add/Edit row dialog; rows persist under the edited
 * row's `__subTables__`.
 *
 * Each descriptor pairs the placed widget (which carries the designer's per-operation
 * switches) with the binding from the linked pool (which carries columns / FK-PK runtime
 * inputs). A placed widget whose binding is missing from the pool is skipped, and the first
 * placement of a binding wins so a duplicated widget cannot render the same table twice.
 */
export function buildNestedSubTableDescriptors(
  formFields: FormField[] | undefined | null,
  pool: SubTableBinding[] | undefined | null,
): NestedSubTableDescriptor[] {
  if (!Array.isArray(formFields) || formFields.length === 0) return []
  const bindings = pool ?? []
  const out: NestedSubTableDescriptor[] = []
  for (const placed of collectSubTableFieldsFromLayout(formFields)) {
    if (placed._bindingId == null) continue
    const b = bindings.find(x => Number(x.bindingId) === Number(placed._bindingId))
    if (!b || out.some(d => d.bindingId === Number(b.bindingId))) continue
    out.push({
      bindingId: Number(b.bindingId),
      tableName: b.tableName,
      columns: b.columns,
      dialogColumns: b.dialogColumns,
      primaryKeyFields: b.primaryKeyFields,
      // FK/PK runtime inputs — the nested field needs them to allocate its own auto PK
      // and to seed the structural FK back to this binding's row.
      tableId: b.tableId ?? null,
      fieldDefinitions: (b as { fieldDefinitions?: BindingFieldDefinition[] }).fieldDefinitions,
      designerTableName: b.designerTableName,
      bindingMode: b.bindingMode,
      // Link Mode（structuralFk / miParticipantRow）—— 与 bindingMode 是两个字段。
      // 漏传会让嵌套子表拿不到 MI 声明，FK 播种与主键分配按普通子表处理。
      bindingLinkMode: b.bindingLinkMode ?? null,
      foreignKeyField: b.foreignKeyField,
      formFields: b.formFields,
      formOptions: b.formOptions ?? null,
      assignmentConfig: b.assignmentConfig,
      // Per-op switches follow the placed widget, exactly like a top-level sub-table:
      // forward only an explicit false so an unset switch keeps the operation open.
      ...(placed.allowAdd === false ? { allowAdd: false } : {}),
      ...(placed.allowEdit === false ? { allowEdit: false } : {}),
      ...(placed.allowDelete === false ? { allowDelete: false } : {}),
    })
  }
  return out
}
