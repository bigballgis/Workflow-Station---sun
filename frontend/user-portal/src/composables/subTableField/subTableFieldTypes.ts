import type { DialogColumn } from '@/components/subTableAddDialogHelpers'
import type { FormField, RowFormulaRule, SubTableValidationConfig } from '@/components/formRendererHelpers'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

export type Column = DialogColumn & {
  type?: DialogColumn['type'] | 'linkForm'
  props?: DialogColumn['props'] & {
    linkText?: string
    componentId?: number
    boundSubTableBindingId?: number
    boundSubTableName?: string
  }
}

export interface SubTableBinding {
  bindingId: number
  tableId?: number | null
  bindingType: string
  /** EDITABLE / READONLY（Table Design 的读写开关）。 */
  bindingMode: string
  /**
   * `structuralFk` / `miParticipantRow`（Manage Table Bindings 的 Link Mode）——
   * 与 {@link bindingMode} 名字相近但语义无关，FK/PK 运行时靠它识别 MI 参与者行。
   */
  bindingLinkMode?: string | null
  foreignKeyField?: string | null
  tableName: string
  physicalTableName?: string
  tableType: string
  tableDescription: string
  columns: Column[]
  /** Form-design canvas columns for Add/Edit dialog. */
  dialogColumns?: Column[]
  /** Designer PK field names (from admin tableBindings); preferred over hardcoded id/rowId. */
  primaryKeyFields?: string[]
  data: any[]
  formFields?: FormField[]
  formOptions?: Record<string, any>
  assignmentConfig?: AssignmentConfig
}

/**
 * Nested sub-table (sub-table-in-sub-table) rendered inside the parent's Add/Edit row
 * dialog. Rows live under the edited row's `__subTables__` (same convention as the
 * form-below-table / Link Form paths).
 */
export interface NestedSubTableDescriptor {
  bindingId: number
  tableName: string
  columns: Column[]
  dialogColumns?: Column[]
  primaryKeyFields?: string[]
  /**
   * FK/PK runtime inputs. Without these the nested field cannot build an allocate function,
   * so grandchild rows are saved with no auto primary key and no structural FK — the flat
   * `__subTables__` slice then gets a server-side key the nested copy never learns about,
   * and later edits to the nested row are dropped on reload.
   */
  tableId?: number | null
  fieldDefinitions?: BindingFieldDefinition[]
  physicalTableName?: string
  /** EDITABLE / READONLY（Table Design 的读写开关）。 */
  bindingMode?: string
  /**
   * `structuralFk` / `miParticipantRow`（Manage Table Bindings 的 Link Mode）——
   * 与 {@link bindingMode} 是**两个不同的字段**，只是名字相近。
   *
   * <p>FK 播种与主键分配靠它判断「这张表是不是 MI 参与者行」
   * （`filterStructuralFkMetasForBinding` / `applyMiParticipantRowSeedToInitialRow`
   * 都只认 `=== 'miParticipantRow'`）。此前 SubTableAddDialog 把 `bindingMode`
   * 接到了 `:binding-link-mode` 上，传下去的是 `EDITABLE`：恒不等于
   * `miParticipantRow`，于是嵌套的 MI collection 会被静默当成普通子表处理。
   */
  bindingLinkMode?: string | null
  foreignKeyField?: string | null
  formFields?: FormField[]
  formOptions?: Record<string, unknown> | null
  assignmentConfig?: AssignmentConfig
  /**
   * 逐操作权限，来自放置在父表单设计里的那个 subTable 组件（props.allowAdd/allowEdit/allowDelete）。
   * 与顶层一致：只有显式 false 才下发，undefined 表示放开（SubTableField 的 withDefaults 兜住）。
   */
  allowAdd?: boolean
  allowEdit?: boolean
  allowDelete?: boolean
}

/** Structural mirror of SubTableField.vue props — composables accept the component's props object. */
export interface SubTableFieldProps {
  title: string
  columns: Column[]
  /** Form-design canvas columns for Add/Edit row dialog (Designer parity). */
  dialogColumns?: Column[]
  /** This binding's numeric id — resolves this binding's `${bindingId}:${fieldName}` entries in fieldPermissions. */
  bindingId?: number | null
  /** Task-node field permissions (`TaskFormData.fieldPermissions`); composite-keyed entries gate Add/Edit dialog fields. */
  fieldPermissions?: Record<string, string> | null
  /** This binding's own form-design fields — nested subTable widgets here render inside the Add/Edit dialog. */
  formFields?: FormField[]
  /** Sub-form Form Design options for Add/Edit dialog lifecycle events. */
  formOptions?: Record<string, unknown> | null
  assignmentConfig?: AssignmentConfig
  modelValue?: any[]
  editable?: boolean
  loading?: boolean
  rowFormulas?: RowFormulaRule[]
  summaryColumns?: string[]
  summaryAggregations?: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'>
  validationConfig?: SubTableValidationConfig
  uploadUrl?: string
  // Multi-instance assignment props
  taskId?: string
  assigneeField?: string
  canAssign?: boolean
  showAssignButton?: boolean
  // Real-time sync props
  enablePolling?: boolean
  pollingInterval?: number
  enableWebSocket?: boolean
  // View detail props (application detail read-only mode)
  showViewDetail?: boolean
  showTaskStatus?: boolean
  // Fill form button (todo detail for MI subtask)
  showFillButton?: boolean
  fillButtonLabel?: string
  linkedSubTableBindings?: SubTableBinding[]
  suppressLinkFormInitialData?: boolean
  /** Task To Do only: show Cancel/Save on Link Form detail (completed / My Request omit). */
  showLinkFormDialogFooter?: boolean
  /**
   * My Request + 「汇总列表 + Link/Details」：表格内 lookup / 用户快照只显示摘要标签，不在单元格内展开 el-descriptions，
   * 避免与「详情走 Link 弹层」的设计冲突（否则看起来像待办的 inline 表单区）。
   */
  compactLookupCells?: boolean
  /**
   * 表设计器在 dw_field_definitions 中标记的主键列名（经 admin-center 随 tableBindings 下发）。
   * 仅单列时参与 assignment / 行定位；多列主键仍回退到既有 id/rowId 等待办路径。
   */
  primaryKeyFields?: string[]
  /** Field FK/PK metadata from tableBindings (PRD S5). */
  fieldDefinitions?: BindingFieldDefinition[]
  tableId?: number | null
  functionUnitId?: string
  primaryFormData?: Record<string, unknown>
  subTableBindingsForContext?: Array<{
    tableId?: number | null
    bindingType?: string
    tableName?: string
    tableDisplayName?: string
  }>
  parentRow?: Record<string, unknown> | null
  parentTableId?: number | null
  primaryTableDisplayName?: string
  primaryTableId?: number | null
  parentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  /** PRD S6: structural FK vs MI participant row link. */
  bindingLinkMode?: 'structuralFk' | 'miParticipantRow' | string
  bindingForeignKeyField?: string | null
  /** Flowable MI element id — seeds attachment/link-child row_id on Add (To Do sub form2). */
  miParticipantRowId?: string | number | null
  miParentParticipantRow?: Record<string, unknown> | null
  miParentTableId?: number | null
}

/** Structural mirror of SubTableField.vue emits — composables accept the component's emit function. */
export interface SubTableFieldEmit {
  (e: 'update:modelValue', val: any[]): void
  (e: 'update:primaryFormData', val: Record<string, unknown>): void
  /** Nested sub-table: auto PK allocated on the (still unsaved) parent row while saving a child. */
  (e: 'update:parentRow', val: Record<string, unknown>): void
  (e: 'assignmentChanged'): void
  (e: 'dataRefreshed', rows: any[]): void
  (e: 'viewDetail', row: any, index: number): void
  (e: 'fillForm', row: any, index: number): void
  (e: 'update:linkedSubTableData', bindingId: number, rows: any[]): void
}

/** Loose i18n translate signature (same shape as utils/subTableRowRuntime). */
export type SubTableFieldT = (key: string, params?: Record<string, unknown>) => string
