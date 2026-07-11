import { relationTableApi } from '@/api/relationTable'
import type { LookupFilterCondition } from '@/utils/lookupFilterConditions'

const resolved = new Map<string, Record<string, any> | null>()
const inflight = new Map<string, Promise<Record<string, any> | null>>()

function makeCacheKey(tableId: number, pkField: string, value: string): string {
  return `${tableId}:${pkField}:${value}`
}

/**
 * 将流程变量里仅存的主键标量（常见为 UUID）解析为关联表/系统用户表完整行，供标签文案与回填视图使用。
 * 与 keyword 模糊搜不同，主键条件可走精确 AND，且不受系统用户表默认 searchFields 不含 id 的限制。
 */
export async function fetchLookupRowByPrimaryKey(
  tableId: number,
  scalar: string | number,
  options: {
    searchFields: string[]
    displayField: string
    filterConditions?: LookupFilterCondition[]
    /** 默认 id；与 {@link PortalRelationTableServiceImpl} 中列名白名单一致 */
    primaryKeyField?: string
  }
): Promise<Record<string, any> | null> {
  const pk = (options.primaryKeyField || 'id').trim() || 'id'
  const sv = String(scalar).trim()
  if (!sv || !Number.isFinite(Number(tableId))) return null

  const ck = makeCacheKey(tableId, pk, sv)
  if (resolved.has(ck)) return resolved.get(ck) ?? null
  if (inflight.has(ck)) return inflight.get(ck)!

  const promise = (async () => {
    try {
      const filters: LookupFilterCondition[] = [
        ...(options.filterConditions || []),
        { fieldName: pk, value: sv, matchType: 'eq' },
      ]
      const res = await relationTableApi.searchForLookup(tableId, {
        keyword: '',
        searchFields: options.searchFields || [],
        displayField: options.displayField || '',
        filterConditions: filters,
        // Server caps this at 200. Use the full cap, not 10: when the PK `eq` filter cannot be
        // applied server-side (e.g. the resolved pk field is not a column of the relation table,
        // so PortalRelationTableServiceImpl drops the predicate), the query degrades to "first N
        // rows ORDER BY id". A window of 10 then misses any value whose row sits past row 10,
        // and the code below must NOT paper over that with a wrong row.
        limit: 200
      })
      const list = res.data || []
      // Only accept an EXACT primary-key match. A near-miss (filter silently dropped → first-N
      // rows returned) must resolve to null so the caller keeps the raw scalar rather than
      // confidently showing an unrelated row (previously `?? list[0]` displayed the first option
      // for every value beyond the fetched window). See LookupField scalar/multi hydrate + list cells.
      const exact = list.find(
        r => String((r as Record<string, unknown>)[pk] ?? (r as Record<string, unknown>).id ?? '').trim() === sv
      )
      if (!exact) {
        resolved.set(ck, null)
        return null
      }
      resolved.set(ck, exact as Record<string, any>)
      return exact as Record<string, any>
    } catch {
      resolved.set(ck, null)
      return null
    } finally {
      inflight.delete(ck)
    }
  })()

  inflight.set(ck, promise)
  return promise
}
