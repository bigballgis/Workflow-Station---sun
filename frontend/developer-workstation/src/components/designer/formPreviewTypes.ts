export interface PreviewSubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  rule: any[]
  option?: any
  columns: any[]
  subMode?: string
}

export type FormPreviewItem =
  | { kind: 'fields'; rule: any[]; modelKey: string }
  | { kind: 'subTable'; binding: PreviewSubTableBinding }
  | { kind: 'relationTable'; tableName: string; fields: Array<{ label: string; value: string }> }
  | { kind: 'lookup'; label: string; placeholder: string; searchFields: string[]; displayFields: string[]; selectedDisplayField?: string; filterConditions?: any[]; viewFields: any[]; fieldDefs: any[]; showBackfillView?: boolean; bindingId?: number }
  | { kind: 'card'; title: string; items: FormPreviewItem[]; modelKey: string }
