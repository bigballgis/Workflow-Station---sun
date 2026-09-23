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

/**
 * A binding owns its declared filter FK. Additional FK columns participate in automatic
 * fill only when the designer explicitly lists them in fkFillSources. Bindings without a
 * declared filter keep the legacy all-structural-FK behavior for old single-binding forms.
 */
export function selectBindingOwnedFkMetas(
  metas: FieldFkMeta[],
  filterFkFieldName?: string | null,
  sources?: FkFillSourceConfig[] | null,
): FieldFkMeta[] {
  const owner = String(filterFkFieldName ?? '').trim().toLowerCase()
  if (!owner) return metas
  const allowed = new Set<string>([owner])
  for (const source of sources ?? []) {
    const field = String(source?.fieldName ?? '').trim().toLowerCase()
    if (field) allowed.add(field)
  }
  return metas.filter(meta => allowed.has(String(meta.fieldName ?? '').trim().toLowerCase()))
}
