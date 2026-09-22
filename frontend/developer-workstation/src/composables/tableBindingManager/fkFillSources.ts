import type { FieldDefinition, TableBinding } from '@/api/functionUnit'
import type { FkFillKind } from '@/utils/tableFkRuntime'

export type { FkFillKind }

export interface FkFillSourceDraft {
  fieldId: number
  kind: FkFillKind
  ancestorBindingId?: number
}

export function ancestorBindingOptions(
  bindings: TableBinding[],
  editingId: number | undefined,
  tables: Array<{ id: number; tableName?: string; tableDisplayName?: string }>,
  t: (key: string) => string,
): Array<{ id: number; label: string }> {
  return bindings
    .filter(b => b.bindingType === 'SUB' && b.id != null && b.id !== editingId)
    .map(b => {
      const table = tables.find(item => item.id === b.tableId)
      const tableLabel = table?.tableDisplayName || table?.tableName || b.tableName || String(b.tableId)
      const filter = b.foreignKeyField ? ` · ${b.foreignKeyField}` : ''
      return { id: b.id as number, label: `${tableLabel}${filter}` || t('tableBinding.unknownTable') }
    })
}

export function kindOfField(sources: FkFillSourceDraft[] | undefined, fieldId: number): FkFillKind | 'AUTO' {
  const hit = sources?.find(s => s.fieldId === fieldId)
  return hit?.kind ?? 'AUTO'
}

export function ancestorIdOfField(sources: FkFillSourceDraft[] | undefined, fieldId: number): number | undefined {
  return sources?.find(s => s.fieldId === fieldId)?.ancestorBindingId
}

export function setFieldFillKind(
  sources: FkFillSourceDraft[] | undefined,
  field: Pick<FieldDefinition, 'id'>,
  kind: FkFillKind | 'AUTO',
  ancestorBindingId?: number,
): FkFillSourceDraft[] | undefined {
  if (field.id == null) return sources
  const rest = (sources ?? []).filter(s => s.fieldId !== field.id)
  if (kind === 'AUTO') return rest.length > 0 ? rest : undefined
  rest.push({
    fieldId: field.id,
    kind,
    ancestorBindingId: kind === 'ANCESTOR' ? ancestorBindingId : undefined,
  })
  return rest
}
