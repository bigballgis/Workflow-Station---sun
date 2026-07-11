import { relationTableDataApi, type LookupFilterCondition } from '@/api/relationTable'

const resolved = new Map<string, Record<string, any> | null>()
const inflight = new Map<string, Promise<Record<string, any> | null>>()

function makeCacheKey(tableId: number, pkField: string, value: string): string {
  return `${tableId}:${pkField}:${value}`
}

/**
 * Resolve a stored scalar PK into the full relation-table row (for tag text + backfill panel).
 * Uses an exact matchType:'eq' filter on the PK field. admin-center variant: searchForLookup
 * returns the raw row array (not wrapped in { data }).
 */
export async function fetchLookupRowByPrimaryKey(
  tableId: number,
  scalar: string | number,
  options: {
    searchFields: string[]
    displayField: string
    filterConditions?: LookupFilterCondition[]
    primaryKeyField?: string
  }
): Promise<Record<string, any> | null> {
  const pk = (options.primaryKeyField || options.searchFields?.[0] || 'id').trim() || 'id'
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
      const list = await relationTableDataApi.searchForLookup(tableId, {
        keyword: '',
        searchFields: options.searchFields || [],
        displayField: options.displayField || '',
        filterConditions: filters,
        // Server caps this at 200. Use the full cap, not 10: when the PK `eq` filter cannot be
        // applied server-side (resolved pk field is not a column of the relation table, so the
        // predicate is dropped), the query degrades to "first N rows ORDER BY id". A window of 10
        // then misses any value whose row sits past row 10; the code below must not paper over it.
        limit: 200
      }) || []
      // Only accept an EXACT primary-key match. A near-miss (filter silently dropped → first-N
      // rows returned) must resolve to null so the caller keeps the raw scalar rather than
      // confidently showing an unrelated row (previously `?? list[0]` displayed the first option
      // for every value beyond the fetched window).
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
