export interface FieldFkMeta {
  fieldName: string
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  fkDisplayMode?: 'readonly' | 'hidden'
}

export function isFkReadonly(meta: FieldFkMeta): boolean {
  return !!meta.isForeignKey && (meta.fkDisplayMode == null || meta.fkDisplayMode === 'readonly')
}

export function isFkHidden(meta: FieldFkMeta): boolean {
  return !!meta.isForeignKey && meta.fkDisplayMode === 'hidden'
}
