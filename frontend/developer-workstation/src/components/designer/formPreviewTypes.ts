import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

export interface PreviewSubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  /**
   * 以下四项直通 SubTableField 的 `config`（见 FormPreviewItems.vue），是子表 FK/PK
   * 运行时解析外键与字段元数据的输入。缺任意一项，保存态预览里的子表就拿不到外键上下文。
   * 与 SubTableConfig（composables/designerSubTableField/types.ts）同名同义。
   */
  tableId?: number
  fieldDefinitions?: unknown[]
  bindingLinkMode?: string
  bindingForeignKeyField?: string | null
  rule: any[]
  option?: any
  columns: any[]
  subMode?: string
  /**
   * 子表逐操作权限（来自放置组件 rule.props）。undefined => 放开（SubTableField 回退 editable）；false => 隐藏该操作。
   */
  allowAdd?: boolean
  allowEdit?: boolean
  allowDelete?: boolean
  /** Presentation designed on the canvas (rule.props). */
  compactCells?: boolean
  assignmentConfig?: AssignmentConfig
}

/** Render sub-table preview (table + Add) when list columns or sub-form rule exist. */
export function hasSubTablePreviewSurface(binding: PreviewSubTableBinding): boolean {
  return (binding.columns?.length ?? 0) > 0 || (binding.rule?.length ?? 0) > 0
}

export type FormPreviewItem =
  | { kind: 'fields'; rule: any[]; modelKey: string }
  /** sourceRule = placed form-create subTable node (carries on/hook for Preview events). */
  | { kind: 'subTable'; binding: PreviewSubTableBinding; sourceRule?: Record<string, unknown> }
  /**
   * Inline Form (`inlineSubForm`): the bound sub-table's form rendered in place. Distinct
   * from `subTable` so preview shows the form alone — no grid, no Add button.
   */
  | { kind: 'inlineSubForm'; binding: PreviewSubTableBinding; modelKey: string; sourceRule?: Record<string, unknown> }
  | { kind: 'relationTable'; tableName: string; fields: Array<{ label: string; value: string }> }
  | { kind: 'lookup'; field: string; rule: Record<string, unknown>; label: string; placeholder: string; searchFields: string[]; displayFields: string[]; selectedDisplayField?: string; filterConditions?: any[]; derivedFrom?: import('@/utils/lookupCascade').LookupDerivedFrom; multiple?: boolean; viewFields: any[]; fieldDefs: any[]; showBackfillView?: boolean; bindingId?: number; readonly?: boolean }
  | { kind: 'card'; title: string; items: FormPreviewItem[]; modelKey: string }
