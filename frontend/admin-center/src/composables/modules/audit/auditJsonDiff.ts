/**
 * 审计日志 - JSON 解析、diff 计算与高亮渲染（纯函数）
 *
 * 从 useAudit.ts 抽出，行为逐字不变。
 * 不依赖 Vue / store / i18n，仅依赖 actionCategory 进行前/后值取舍。
 */

import DOMPurify from 'dompurify'
import type { AuditLog } from '@/api/audit'
import { actionCategory } from './auditMappings'

export const SYSTEM_AUDIT_FIELDS = new Set<string>([
  'updatedAt', 'createdAt', 'timestamp',
  'lastModifiedAt', 'lastModifiedDate', 'modifiedAt',
  'lastUpdatedAt', 'updateTime', 'createTime', 'createdDate',
  'updatedBy', 'createdBy',
  'lastModifiedBy', 'modifiedBy',
  'createBy', 'updateBy',
  'version',
])

export const parseJson = (s: string | null | undefined): Record<string, unknown> | null => {
  if (!s) return null
  try { return JSON.parse(s) } catch { return { value: s } }
}

export const getDiffJson = (
  oldStr: string | null | undefined,
  newStr: string | null | undefined,
  side: 'before' | 'after'
): Record<string, unknown> => {
  const parseOrWrap = (s: string) => {
    try { return JSON.parse(s) } catch { return { value: s } }
  }
  if (!oldStr && !newStr) return {}
  if (!oldStr) return parseOrWrap(newStr!)
  if (!newStr) return parseOrWrap(oldStr!)
  try {
    const oldObj = parseOrWrap(oldStr)
    const newObj = parseOrWrap(newStr)
    const oldKeys = Object.keys(oldObj)
    const newKeys = Object.keys(newObj)
    const allKeys = new Set([...oldKeys, ...newKeys])
    const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
    const maxKeys = Math.max(oldKeys.length, newKeys.length)
    if (maxKeys > 2 && sharedKeys / maxKeys < 0.3) {
      return side === 'before' ? oldObj : newObj
    }
    const diff: Record<string, unknown> = {}
    for (const key of allKeys) {
      if (SYSTEM_AUDIT_FIELDS.has(key)) continue
      if (JSON.stringify(oldObj[key]) !== JSON.stringify(newObj[key])) {
        diff[key] = side === 'before' ? oldObj[key] : newObj[key]
      }
    }
    return Object.keys(diff).length > 0 ? diff : (side === 'before' ? oldObj : newObj)
  } catch {
    return parseOrWrap(side === 'before' ? oldStr : newStr)
  }
}

export const getBeforeData = (log: AuditLog): Record<string, unknown> | null => {
  const cat = actionCategory(log.action)
  if (cat === 'create' || cat === 'query') return null
  if (cat === 'delete') return parseJson(log.oldValue)
  const old = parseJson(log.oldValue)
  const nw  = parseJson(log.newValue)
  if (!old) return null
  if (!nw)  return old
  const oldKeys    = Object.keys(old)
  const newKeys    = Object.keys(nw)
  const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
  const maxKeys    = Math.max(oldKeys.length, newKeys.length)
  if (maxKeys > 2 && sharedKeys / maxKeys >= 0.7) {
    return getDiffJson(log.oldValue, log.newValue, 'before')
  }
  return old
}

export const getAfterData = (log: AuditLog): Record<string, unknown> | null => {
  const cat = actionCategory(log.action)
  if (cat === 'delete' || cat === 'query') return null
  if (cat === 'create') return parseJson(log.newValue)
  const old = parseJson(log.oldValue)
  const nw  = parseJson(log.newValue)
  if (!nw)  return null
  if (!old) return nw
  const oldKeys    = Object.keys(old)
  const newKeys    = Object.keys(nw)
  const sharedKeys = oldKeys.filter(k => newKeys.includes(k)).length
  const maxKeys    = Math.max(oldKeys.length, newKeys.length)
  if (maxKeys > 2 && sharedKeys / maxKeys >= 0.7) {
    return getDiffJson(log.oldValue, log.newValue, 'after')
  }
  return nw
}

const escapeHtml = (s: string): string =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const IDENTITY_KEYS = ['id', 'uuid', 'code', 'key', 'name', 'fieldName'] as const
const isPlainObject = (x: unknown): x is Record<string, unknown> =>
  x !== null && typeof x === 'object' && !Array.isArray(x)
const getIdentity = (x: unknown): { key: string; value: unknown } | null => {
  if (!isPlainObject(x)) return null
  for (const k of IDENTITY_KEYS) {
    const v = x[k]
    if (v !== undefined && v !== null && (typeof v === 'string' || typeof v === 'number')) {
      return { key: k, value: v }
    }
  }
  return null
}
const hasIdentityKey = (x: unknown): boolean => getIdentity(x) !== null
const findArrayMatch = (item: unknown, arr: unknown[]): unknown => {
  const id = getIdentity(item)
  if (!id) return undefined
  return arr.find(c => {
    const cid = getIdentity(c)
    return cid !== null && cid.key === id.key && cid.value === id.value
  })
}

const renderJsonValue = (
  value: unknown,
  compare: unknown,
  depth: number,
  forceChanged = false
): string => {
  const pad = '  '.repeat(depth)
  const padInner = '  '.repeat(depth + 1)
  if (value === null) return '<span class="jnull">null</span>'
  if (typeof value === 'boolean') return `<span class="jb">${value}</span>`
  if (typeof value === 'number') return `<span class="jn">${value}</span>`
  if (typeof value === 'string') {
    return `<span class="js">${escapeHtml(JSON.stringify(value))}</span>`
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]'
    const cmpArr = !forceChanged && Array.isArray(compare) ? compare : null
    const items = value.map((item, idx) => {
      let cmpItem: unknown = undefined
      let itemForced = forceChanged
      if (cmpArr !== null) {
        const matched = findArrayMatch(item, cmpArr)
        if (matched !== undefined) {
          cmpItem = matched
        } else if (!hasIdentityKey(item)) {
          cmpItem = cmpArr[idx]
        } else {
          itemForced = true
        }
      }
      return `${padInner}${renderJsonValue(item, cmpItem, depth + 1, itemForced)}`
    })
    return `[\n${items.join(',\n')}\n${pad}]`
  }
  if (typeof value === 'object') {
    const record = value as Record<string, unknown>
    const keys = Object.keys(record)
    if (keys.length === 0) return '{}'
    const cmpRecord =
      !forceChanged && compare && typeof compare === 'object' && !Array.isArray(compare)
        ? (compare as Record<string, unknown>)
        : undefined
    const entries = keys.map((k) => {
      const v = record[k]
      const cmpV = cmpRecord ? cmpRecord[k] : undefined
      const changed =
        !SYSTEM_AUDIT_FIELDS.has(k) &&
        (forceChanged ||
          (cmpRecord !== undefined &&
            JSON.stringify(v) !== JSON.stringify(cmpV)))
      const keyClass = changed ? 'jk jk-changed' : 'jk'
      const keyJson = escapeHtml(JSON.stringify(k))
      return `${padInner}<span class="${keyClass}">${keyJson}:</span> ${renderJsonValue(v, cmpV, depth + 1, forceChanged)}`
    })
    return `{\n${entries.join(',\n')}\n${pad}}`
  }
  return `<span class="jn">${escapeHtml(String(value))}</span>`
}

export const formatJsonHighlight = (
  obj: Record<string, unknown> | null,
  compareAgainst?: Record<string, unknown> | null
): string => {
  if (!obj || Object.keys(obj).length === 0) return '{}'
  const html = renderJsonValue(obj, compareAgainst ?? undefined, 0)
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['span'],
    ALLOWED_ATTR: ['class'],
  })
}
