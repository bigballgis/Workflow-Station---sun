import type { FormField } from '@/components/formRendererHelpers'
import {
  mergeSubTableRowsByRowId,
  pullNestedRowsForBindingFromParentRows,
  stripLinkFormDesignerTableLabel
} from '@/composables/tasks/shared'
import type { SubTableBinding } from './subTableFieldTypes'
import { linkFormTableMatchKey, normalizeFkIdForMatch } from './subTableLinkFormRowMatch'

export function countLinkFormFields(formFields?: FormField[]): number {
  if (!formFields?.length) return 0
  let n = 0
  for (const f of formFields) {
    if (f.type === 'card') n += (f.children || []).length
    else n++
  }
  return n
}

export function rowValueForLinkedFormField(row: Record<string, any>, key: string): unknown {
  if (!row || typeof row !== 'object') return undefined
  if (Object.prototype.hasOwnProperty.call(row, key)) return row[key]
  const want = key.toLowerCase()
  const wantNorm = want.replace(/_/g, '')
  for (const rk of Object.keys(row)) {
    if (rk.startsWith('__')) continue
    const rkl = rk.toLowerCase()
    if (rkl === want) return row[rk]
    if (wantNorm.length > 0 && rkl.replace(/_/g, '') === wantNorm) return row[rk]
  }
  return undefined
}

export function isPresentLinkedModalValue(v: unknown): boolean {
  if (v === undefined || v === null) return false
  if (typeof v === 'boolean') return true
  if (typeof v === 'number') return !Number.isNaN(v)
  if (typeof v === 'string') return v.trim() !== ''
  return true
}

export function isPresentLinkedFormFieldValue(field: FormField, v: unknown): boolean {
  if (!isPresentLinkedModalValue(v)) return false
  if (field.type === 'number' || field.type === 'inputNumber') {
    if (typeof v === 'number') return !Number.isNaN(v)
    if (typeof v === 'string') {
      const t = v.trim()
      if (t === '') return false
      return !Number.isNaN(Number(t))
    }
    return false
  }
  return true
}

/** Count filled link-form fields on one row (used to pick {@code data[0]} vs a richer sibling row). */
export function scoreRowForLinkedFormFields(row: unknown, formFields?: FormField[]): number {
  if (!row || typeof row !== 'object' || !formFields?.length) return 0
  const o = row as Record<string, any>
  let s = 0
  for (const field of formFields) {
    if (field.type === 'card') {
      for (const c of field.children || []) {
        const v = rowValueForLinkedFormField(o, c.key)
        if (isPresentLinkedFormFieldValue(c, v)) s++
      }
    } else {
      const v = rowValueForLinkedFormField(o, field.key)
      if (isPresentLinkedFormFieldValue(field, v)) s++
    }
  }
  return s
}

/** When copied forms / Link merge use a different bindingId than variables, {@link subTableBindingMatches} may miss; score by form field keys vs row keys. */
export function collectLinkTargetFormFieldKeys(binding?: SubTableBinding): Set<string> {
  const keys = new Set<string>()
  if (!binding?.formFields?.length) return keys
  for (const f of binding.formFields) {
    if (f.type === 'card') {
      f.children?.forEach(c => {
        if (typeof c.key === 'string' && c.key) keys.add(c.key)
      })
    } else if (typeof f.key === 'string' && f.key) {
      keys.add(f.key)
    }
  }
  return keys
}

/** Max overlap score over all rows (row0 is often a MI placeholder; real payload may be at index 1+). */
export function maxFormFieldOverlapScore(rows: any[], fieldKeys: Set<string>): number {
  if (!Array.isArray(rows) || fieldKeys.size === 0) return -1
  let best = -1
  for (const r of rows) {
    if (!r || typeof r !== 'object') continue
    let score = 0
    for (const fk of fieldKeys) {
      const v = rowValueForLinkedFormField(r as Record<string, any>, fk)
      if (isPresentLinkedModalValue(v)) score++
    }
    if (score > best) best = score
  }
  return best
}

/** True when saved nested rows carry no real values for any designer link-form field (placeholders only). */
export function linkFormRowsLackFormPayload(rows: any[], formFields: FormField[] | undefined): boolean {
  if (!formFields?.length) return false
  if (!Array.isArray(rows) || rows.length === 0) return true
  const row0 = rows[0]
  if (!row0 || typeof row0 !== 'object') return true
  const score = scoreRowForLinkedFormFields(row0, formFields)
  if (score === 0) return true
  const total = countLinkFormFields(formFields)
  return total > 1 && score < total
}

export function peerSubTableDataByFormFieldOverlap(binding: SubTableBinding | undefined, peers: SubTableBinding[]): any[] {
  if (!binding || !peers.length) return []
  const fieldKeys = collectLinkTargetFormFieldKeys(binding)
  if (fieldKeys.size === 0) return []
  const threshold =
    fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))

  let best: any[] = []
  let bestScore = -1
  for (const p of peers) {
    if (!Array.isArray(p.data) || p.data.length === 0) continue
    const score = maxFormFieldOverlapScore(p.data, fieldKeys)
    if (score >= threshold && score > bestScore) {
      bestScore = score
      best = p.data
    }
  }
  return best
}

/**
 * BFS under {@code __subTables__} trees — COMPLETED MI rows often store link-form fields only on deeply nested slices
 * while every top-level key on the parent row references the same thin placeholder array (runtime: savedRowsLen=1, no sex/age).
 */
export function deepCollectLinkFormFieldRows(root: unknown, binding: SubTableBinding, maxDepth = 10): any[] {
  const hits: any[] = []
  const seen = new Set<object>()
  const ff = binding.formFields
  if (!ff?.length) return hits

  const consider = (obj: object) => {
    if (seen.has(obj)) return
    seen.add(obj)
    const rec = obj as Record<string, unknown>
    const hasNonIdField = ff.some(field => {
      if (field.type === 'card') {
        return (field.children || []).some(c => {
          if (c.key === 'id') return false
          return isPresentLinkedModalValue(rowValueForLinkedFormField(rec, c.key))
        })
      }
      if (field.key === 'id') return false
      return isPresentLinkedModalValue(rowValueForLinkedFormField(rec, field.key))
    })
    if (hasNonIdField && scoreRowForLinkedFormFields(rec, ff) > 0) hits.push(rec)
  }

  const walk = (node: unknown, depth: number) => {
    if (depth > maxDepth || node == null) return
    if (Array.isArray(node)) {
      for (const el of node) {
        if (el && typeof el === 'object') {
          consider(el)
          const nest = (el as Record<string, unknown>).__subTables__
          if (nest && typeof nest === 'object') walk(nest, depth + 1)
        }
      }
      return
    }
    if (typeof node === 'object') {
      for (const v of Object.values(node as Record<string, unknown>)) walk(v, depth + 1)
    }
  }
  walk(root, 0)
  return hits
}

export function resolveLinkFormFieldValueForModal(
  field: FormField,
  raw: unknown,
  opts?: { readonly?: boolean },
): unknown {
  if (!isPresentLinkedModalValue(raw)) return field.defaultValue ?? null
  if (field.type === 'number') {
    if (typeof raw === 'number') return Number.isNaN(raw) ? (field.defaultValue ?? null) : raw
    if (typeof raw === 'string') {
      const t = raw.trim()
      if (t === '') return field.defaultValue ?? null
      const n = Number(t)
      if (!Number.isNaN(n)) return n
      /** Readonly Details: designer PK may be number while runtime stores UUID / Test-xxxx scalar. */
      if (opts?.readonly) return t
      return field.defaultValue ?? null
    }
    return field.defaultValue ?? null
  }
  return raw
}

/** Link-child {@code id} is an allocated row PK (UUID / numeric). Parent MI {@code id_idw} belongs in {@code sub_task_id} only. */
export function isAllocatedLinkChildBusinessId(
  childId: unknown,
  parentKey: string | null,
): boolean {
  const n = normalizeFkIdForMatch(childId)
  if (n == null) return false
  if (parentKey != null && n === parentKey) return false
  if (/^test-\d+$/i.test(n)) return false
  return true
}

/** Merge every nested slice on the parent row that targets this link-form binding (all key variants). */
export function collectNestedSavedRowsForLinkForm(
  rowSub: Record<string, any>,
  binding: SubTableBinding | undefined,
  boundId: unknown,
  boundName: string | undefined
): any[] {
  const seenArr = new Set<any>()
  const chunks: any[][] = []
  const addArr = (v: unknown) => {
    if (!Array.isArray(v) || v.length === 0 || seenArr.has(v)) return
    seenArr.add(v)
    chunks.push(v as any[])
  }
  if (boundId != null) {
    addArr(rowSub[boundId as string | number])
    addArr(rowSub[String(boundId)])
  }
  if (boundName) {
    const raw = String(boundName).trim()
    const stripped = stripLinkFormDesignerTableLabel(raw)
    for (const k of [raw, stripped]) addArr(rowSub[k])
    const want = linkFormTableMatchKey(raw)
    for (const rk of Object.keys(rowSub)) {
      if (linkFormTableMatchKey(rk) === want) addArr(rowSub[rk])
    }
  }
  let merged: any[] = []
  for (const chunk of chunks) {
    merged = mergeSubTableRowsByRowId(merged, chunk, binding?.primaryKeyFields ?? null)
  }
  /** MI parent rows may nest link-form payload under a sibling binding id (e.g. 30) while the column binds 69. */
  if (binding?.formFields?.length) {
    const fieldKeys = collectLinkTargetFormFieldKeys(binding)
    if (fieldKeys.size > 0) {
      const threshold =
        fieldKeys.size <= 2 ? 1 : Math.min(fieldKeys.size, Math.max(2, Math.ceil(fieldKeys.size * 0.25)))
      for (const v of Object.values(rowSub)) {
        if (!Array.isArray(v) || v.length === 0 || seenArr.has(v)) continue
        if (maxFormFieldOverlapScore(v, fieldKeys) >= threshold) addArr(v)
      }
      merged = []
      for (const chunk of chunks) {
        merged = mergeSubTableRowsByRowId(merged, chunk, binding?.primaryKeyFields ?? null)
      }
    }
  }
  return merged
}

export function linkFormBindingDef(binding: SubTableBinding) {
  return {
    bindingId: Number(binding.bindingId),
    tableName: String(binding.tableName ?? ''),
    physicalTableName: (binding as { physicalTableName?: string }).physicalTableName,
    tableId: binding.tableId ?? null
  }
}

/** Child rows may store the real link-form payload only under their own {@code __subTables__} (COMPLETED MI). */
export function enrichLinkFormRowsFromNestedSubTables(
  rows: any[],
  binding: SubTableBinding,
  peerMap: Map<number, number | null>
): any[] {
  const def = linkFormBindingDef(binding)
  return rows.map(r => {
    if (!r || typeof r !== 'object') return r
    const fromSelf = pullNestedRowsForBindingFromParentRows(def, [r], peerMap)
    if (fromSelf.length === 0) return r
    const merged = mergeSubTableRowsByRowId([r], fromSelf, binding.primaryKeyFields ?? null)
    return merged[0] ?? r
  })
}
