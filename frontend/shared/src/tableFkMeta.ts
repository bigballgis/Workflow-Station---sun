export type FkFillKind = 'PARENT' | 'PRIMARY' | 'ANCESTOR'

export interface FkFillSourceConfig {
  fieldName: string
  kind: FkFillKind
  ancestorBindingId?: number | string | null
  ancestorTableName?: string | null
  ancestorFilterFkFieldName?: string | null
}

export interface FieldFkMeta {
  fieldName: string
  isForeignKey?: boolean
  refTableId?: number
  refPrimaryKeyFields?: string[]
  fkDisplayMode?: 'readonly' | 'hidden'
  fkFillKind?: FkFillKind
  ancestorBindingId?: number | string
  ancestorTableName?: string
  ancestorFilterFkFieldName?: string
}

export function isFkReadonly(meta: FieldFkMeta): boolean {
  return !!meta.isForeignKey && (meta.fkDisplayMode == null || meta.fkDisplayMode === 'readonly')
}

export function isFkHidden(meta: FieldFkMeta): boolean {
  return !!meta.isForeignKey && meta.fkDisplayMode === 'hidden'
}

export function declaredFkFillSources(b: {
  fkFillSources?: FkFillSourceConfig[] | null
} | null | undefined): { fkFillSources?: FkFillSourceConfig[] } {
  const list = b?.fkFillSources
  if (!Array.isArray(list) || list.length === 0) return {}
  const out: FkFillSourceConfig[] = []
  for (const source of list) {
    if (!source || typeof source.fieldName !== 'string' || !source.fieldName.trim()) continue
    if (source.kind !== 'PARENT' && source.kind !== 'PRIMARY' && source.kind !== 'ANCESTOR') continue
    out.push({
      fieldName: source.fieldName.trim(),
      kind: source.kind,
      ancestorBindingId: source.ancestorBindingId ?? null,
      ancestorTableName: source.ancestorTableName ?? null,
      ancestorFilterFkFieldName: source.ancestorFilterFkFieldName ?? null,
    })
  }
  return out.length > 0 ? { fkFillSources: out } : {}
}

export function applyFkFillSources(
  metas: FieldFkMeta[],
  sources?: FkFillSourceConfig[] | null,
): FieldFkMeta[] {
  if (!metas?.length || !sources?.length) return metas
  const byField = new Map<string, FkFillSourceConfig>()
  for (const source of sources) {
    if (source?.fieldName) byField.set(source.fieldName, source)
  }
  return metas.map(meta => {
    const source = byField.get(meta.fieldName)
    if (!source) return meta
    return {
      ...meta,
      fkFillKind: source.kind,
      ancestorBindingId: source.ancestorBindingId ?? undefined,
      ancestorTableName: source.ancestorTableName ?? undefined,
      ancestorFilterFkFieldName: source.ancestorFilterFkFieldName ?? undefined,
    }
  })
}
