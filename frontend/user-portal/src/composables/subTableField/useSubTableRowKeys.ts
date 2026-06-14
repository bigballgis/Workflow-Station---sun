import type { SubTableFieldProps } from './subTableFieldTypes'

const ROW_KEY_MERGE_SEP = '\u001f'

/**
 * Stable key matching {@link com.platform.common.jdbc.SubTableRowKeySupport#canonicalRowKeyString}
 * for server-provided {@code rowKey} on sub-table sync payloads.
 */
export function canonicalRowKeyFromPayload(r: Record<string, unknown>): string | null {
  const rk = r.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    const o = rk as Record<string, unknown>
    return Object.keys(o)
      .sort()
      .map(k => `${k}=${o[k]}`)
      .join(ROW_KEY_MERGE_SEP)
  }
  return null
}

export function sameValue(a: unknown, b: unknown): boolean {
  const sa = a == null ? '' : String(a).trim().toLowerCase()
  const sb = b == null ? '' : String(b).trim().toLowerCase()
  return sa !== '' && sb !== '' && sa === sb
}

export function useSubTableRowKeys(props: SubTableFieldProps) {
  /**
   * Sub-table row primary key for assignment APIs and client-side row matching.
   * Prefers designer single-column PK when provided; otherwise legacy id / rowId / MI heuristics.
   */
  function resolveSubTableRowPk(row: Record<string, unknown> | null | undefined): string | number | null {
    if (!row) return null
    const r = row as Record<string, unknown>
    const pks = props.primaryKeyFields
    if (Array.isArray(pks) && pks.length === 1) {
      const v = r[pks[0]!]
      if (v != null && v !== '') return v as string | number
    }
    const candidates: unknown[] = [
      r.id,
      r.rowId,
      r.id_idw,
      r.participant_id,
      r.participantId,
      (r as { ID?: unknown }).ID,
      (r as { RowId?: unknown }).RowId
    ]
    for (const v of candidates) {
      if (v != null && v !== '') return v as string | number
    }
    return null
  }

  function resolveSubTableRowMergeKey(row: Record<string, unknown> | null | undefined): string | number | null {
    const c = canonicalRowKeyFromPayload(row || {})
    if (c != null && c !== '') return c
    return resolveSubTableRowPk(row)
  }

  return { resolveSubTableRowPk, resolveSubTableRowMergeKey }
}
