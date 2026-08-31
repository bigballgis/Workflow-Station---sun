export interface LookupViewField {
  fieldName: string
  displayLabel?: string
  columnWidth?: number
  sortOrder: number
  visible: boolean
}

export interface LookupVisibleColumn {
  prop: string
  label: string
  width: number | undefined
}

function labelForField(viewFields: LookupViewField[], fieldName: string): string {
  const vf = viewFields.find(v => v.fieldName === fieldName)
  const label = vf?.displayLabel?.trim()
  return label || fieldName
}

function widthForField(viewFields: LookupViewField[], fieldName: string): number | undefined {
  return viewFields.find(v => v.fieldName === fieldName)?.columnWidth
}

export function buildVisibleColumns(opts: {
  displayFields?: string[]
  searchFields: string[]
  displayField: string
  viewFields: LookupViewField[]
}): LookupVisibleColumn[] {
  const { displayFields, searchFields, displayField, viewFields } = opts
  if (displayFields && displayFields.length > 0) {
    return displayFields.map(f => ({
      prop: f,
      label: labelForField(viewFields, f),
      width: widthForField(viewFields, f),
    }))
  }
  if (searchFields.length > 0) {
    return searchFields.map(f => ({
      prop: f,
      label: labelForField(viewFields, f),
      width: widthForField(viewFields, f),
    }))
  }
  const cols = new Set<string>()
  if (displayField) cols.add(displayField)
  return Array.from(cols).map(f => ({
    prop: f,
    label: labelForField(viewFields, f),
    width: widthForField(viewFields, f),
  }))
}

export function lookupTableContentWidth(
  columns: LookupVisibleColumn[],
  multiple: boolean,
): number {
  const cols = columns.reduce((sum, col) => sum + (col.width || 120), 0)
  return cols + (multiple ? 40 : 0)
}
