/**
 * Form Designer Utilities
 * Pure functions for form designer operations — no Vue reactivity dependency.
 */

import type { FormDefinition, FormType } from '@/api/functionUnit'

const FORM_TYPE_SORT_ORDER: Record<FormType, number> = {
  PROCESS: 0,
  TASK: 1,
  ACTION: 2,
}

/** Sort forms for Form Design list: Process → Task → Action, then by name. */
export function sortFormsByType(forms: FormDefinition[]): FormDefinition[] {
  return [...forms].sort((a, b) => {
    const typeDiff = FORM_TYPE_SORT_ORDER[a.formType] - FORM_TYPE_SORT_ORDER[b.formType]
    if (typeDiff !== 0) return typeDiff
    return a.formName.localeCompare(b.formName, undefined, { sensitivity: 'base' })
  })
}

/**
 * Deep-clone form rules via JSON round-trip. Falls back to shallow copy on failure.
 */
export function cloneFormRules(rules: any[]): any[] {
  if (!Array.isArray(rules) || rules.length === 0) return []
  try {
    return JSON.parse(JSON.stringify(rules))
  } catch {
    return rules.slice()
  }
}

/**
 * Walk rule tree and inject upload button labels so legacy saved rules
 * (which omit uploadText) never show hardcoded fallback text.
 */
export function injectUploadButtonLabels(rules: any[], uploadText: string): void {
  const walk = (items: any[]) => {
    for (const r of items) {
      if (!r || typeof r !== 'object') continue
      if (r.type === 'upload') {
        r.props = r.props || {}
        if (r.props.uploadText == null || r.props.uploadText === '') {
          r.props.uploadText = uploadText
        }
      }
      if (Array.isArray(r.children) && r.children.length) walk(r.children)
    }
  }
  walk(rules)
}

import {
  applyUploadFileDisplayMeta,
  extractUploadNameFromResponse,
  extractUploadUrlFromResponse,
  getFilenameFromUrl,
  syncFormCreateUploadFieldValue,
} from '@/components/designer/uploadFieldUtils'
import { ensureEmptyFormOptionsEvents } from '@/utils/formCreateDefaultEvents'

/**
 * Collect upload rules from a form-create rule tree (including nested layout children).
 */
export function collectUploadRulesFromTree(rules: any[]): Array<{ field: string; type: 'upload'; props?: Record<string, unknown> }> {
  const out: Array<{ field: string; type: 'upload'; props?: Record<string, unknown> }> = []
  const walk = (items: any[]) => {
    for (const r of items || []) {
      if (!r || typeof r !== 'object') continue
      if (r.type === 'upload' && r.field) {
        out.push({ field: String(r.field), type: 'upload', props: r.props })
      }
      const children = getRuleChildren(r)
      if (children.length) walk(children)
    }
  }
  walk(rules)
  return out
}

/**
 * Wire form-create upload rules so successful uploads persist URL strings on formData.
 * form-create requires assigning file.url in onSuccess — otherwise v-model stays empty.
 */
export function injectPreviewUploadHandlers(
  rules: any[],
  formData: { value: Record<string, unknown> },
  uploadSession?: { value: Record<string, { url: string; name?: string }> },
): void {
  const walk = (items: any[]) => {
    for (const r of items) {
      if (!r || typeof r !== 'object') continue
      if (r.type === 'upload' && r.field) {
        r.props = r.props || {}
        if (!r.props.uploadType) r.props.uploadType = 'file'
        if (!r.props.action || r.props.action === '/') r.props.action = '/api/v1/upload'
        const field = String(r.field)
        const nameTarget = r.props.fileNameTargetField as string | undefined

        r.props.onSuccess = (
          res: unknown,
          file?: { url?: string; name?: string; value?: unknown; response?: unknown },
        ) => {
          const url = extractUploadUrlFromResponse(res)
          const displayName = extractUploadNameFromResponse(res, file) || (url ? getFilenameFromUrl(url) : '')
          if (file && url && displayName) {
            applyUploadFileDisplayMeta(file, url, displayName)
          }
          if (url && uploadSession) {
            uploadSession.value = {
              ...uploadSession.value,
              [field]: { url, name: displayName },
            }
          }
          if (nameTarget && displayName) {
            formData.value[nameTarget] = displayName
          }
        }
        // fcUpload handleChange runs after onSuccess and may emit a bare URL (UUID display name).
        // Sync formData once here — not in onSuccess — to avoid double re-render / filename flash.
        r.props.onChange = (
          file?: { url?: string; name?: string; value?: unknown; response?: unknown; status?: string },
          fileList?: Array<{ url?: string; name?: string; value?: unknown; response?: unknown; status?: string }>,
        ) => {
          if (file?.status !== 'success') return
          const src = file
            || (fileList || []).slice().reverse().find((f) => f.status === 'success')
          if (!src) return
          const url = extractUploadUrlFromResponse(src.response) || String(src.url || '').trim()
          if (!url) return
          const displayName = extractUploadNameFromResponse(src.response, src) || getFilenameFromUrl(url)
          applyUploadFileDisplayMeta(src, url, displayName)
          syncFormCreateUploadFieldValue(formData, field, url, displayName)
          if (uploadSession) {
            uploadSession.value = {
              ...uploadSession.value,
              [field]: { url, name: displayName },
            }
          }
          if (nameTarget && displayName) {
            formData.value[nameTarget] = displayName
          }
        }
        r.props.onRemove = () => {
          formData.value[field] = []
          if (uploadSession) {
            const next = { ...uploadSession.value }
            delete next[field]
            uploadSession.value = next
          }
          if (nameTarget) formData.value[nameTarget] = ''
        }
      }
      const children = getRuleChildren(r)
      if (children.length) walk(children)
    }
  }
  walk(rules)
}

/**
 * Depth-first walk of a form-create rule tree with cycle detection.
 * fc-designer layout nodes (el-row / el-col / card) can share object references;
 * unguarded recursion on Preview paths caused main-thread hangs.
 */
export function walkFormCreateRules(
  items: unknown[],
  visit: (rule: Record<string, unknown>) => void,
  visited: WeakSet<object> = new WeakSet<object>(),
): void {
  if (!Array.isArray(items)) return
  for (const raw of items) {
    if (!raw || typeof raw !== 'object') continue
    const rule = raw as Record<string, unknown>
    if (visited.has(rule)) continue
    visited.add(rule)
    visit(rule)
    walkFormCreateRules(getRuleChildren(rule), visit, visited)
  }
}

/**
 * Deep-clone designer rules before Preview transforms so getRule() references
 * are never mutated in place (ensureFormCreateRulesValidationDeep mutates).
 */
export function snapshotRulesForPreview(rules: unknown[] | null | undefined): any[] {
  return cloneFormRules(Array.isArray(rules) ? rules : [])
}

/**
 * Get children from any known nesting pattern in a form rule item.
 */
export function getRuleChildren(item: any): any[] {
  const childSources = [
    item?.children,
    item?.props?.children,
    item?.props?.list,
    item?.props?.items,
    item?.props?.fields,
  ]
  return childSources.find(children => Array.isArray(children)) || []
}

/** Layout containers whose children are real field rules (card set + FC_SKIP_PREVIEW). */
const LAYOUT_CONTAINER_TYPES = new Set([
  'el-card', 'elCard', 'card',
  'el-row', 'elRow', 'row',
  'el-col', 'elCol', 'col',
  'group', 'subForm', 'tableForm', 'tableFormColumn',
])

/**
 * Expand layout containers (Card/Row/Col/…) into their children so field rules nested
 * inside them still participate in sub-table column derivation. Field-bearing rules and
 * placeholders (subTable/linkForm) pass through untouched, in document order.
 */
export function flattenRuleLayoutContainers(rules: any[]): any[] {
  if (!Array.isArray(rules)) return []
  const out: any[] = []
  const walk = (items: any[]) => {
    for (const item of items) {
      if (item && typeof item === 'object' && !item.field && LAYOUT_CONTAINER_TYPES.has(String(item.type))) {
        walk(getRuleChildren(item))
      } else {
        out.push(item)
      }
    }
  }
  walk(rules)
  return out
}

/**
 * Recursively collect all subTable-type rules from a rule tree.
 */
export function collectSubTableRules(items: any[]): any[] {
  const result: any[] = []
  for (const item of items || []) {
    if (!item) continue
    if (item.type === 'subTable') result.push(item)
    const children = getRuleChildren(item)
    if (children.length) result.push(...collectSubTableRules(children))
  }
  return result
}

/**
 * Recursively collect the scope of every recordNote rule in a rule tree
 * (normalized to 'TABLE' | 'RECORD'); used to enforce one component per scope.
 */
export function collectRecordNoteScopes(items: any[]): string[] {
  const result: string[] = []
  for (const item of items || []) {
    if (!item) continue
    if (item.type === 'recordNote') {
      result.push(item.props?.scope === 'TABLE' ? 'TABLE' : 'RECORD')
    }
    const children = getRuleChildren(item)
    if (children.length) result.push(...collectRecordNoteScopes(children))
  }
  return result
}

/**
 * Copy top-level `_bindingId` into `props._bindingId` on every subTable rule (non-mutating).
 * Persisted rules keep `_bindingId` only at top level (the drag rule's parseRule strips the
 * props copy on save), but SubTablePlaceholderWidget reads props — so preview surfaces that
 * feed saved rules straight into form-create must run this or nested placeholders render
 * as "unconfigured".
 */
export function withSubTableBindingIdInProps(items: any[]): any[] {
  const visited = new WeakSet<object>()

  function mapList(list: any[]): any[] {
    return (list || []).map((item) => mapItem(item))
  }

  function mapItem(item: any): any {
    if (!item || typeof item !== 'object') return item
    if (visited.has(item)) return item
    visited.add(item)

    let next = item
    if (item.type === 'subTable' && item._bindingId != null && item.props?._bindingId == null) {
      next = { ...item, props: { ...(item.props || {}), _bindingId: item._bindingId } }
    }
    const children = getRuleChildren(next)
    if (!children.length) return next

    const mapped = mapList(children)
    if (mapped === children) return next
    next = next === item ? { ...item } : next
    if (Array.isArray(next.children)) next.children = mapped
    else if (next.props?.children) next.props = { ...next.props, children: mapped }
    else if (Array.isArray(next.props?.list)) next.props = { ...next.props, list: mapped }
    else if (Array.isArray(next.props?.items)) next.props = { ...next.props, items: mapped }
    else if (Array.isArray(next.props?.fields)) next.props = { ...next.props, fields: mapped }
    return next
  }

  return mapList(items)
}

/**
 * Check if a rule item is a card/layout container.
 */
export function isCardRule(item: any): boolean {
  return ['el-card', 'elCard', 'card'].includes(item?.type)
}

/**
 * Extract a human-readable label from a card/layout rule item.
 */
export function getLayoutLabel(item: any): string {
  return String(item?.title || item?.props?.header || item?.props?.title || '')
}

/**
 * Merge persisted form-create options with defaults, ensuring upload labels are injected.
 */
export function mergeLoadedFormOptions(
  stored: Record<string, any> | undefined,
  defaults: Record<string, any>,
  clickToUploadText: string,
): Record<string, any> {
  const baseDefaults = ensureEmptyFormOptionsEvents(defaults)
  if (!stored || Object.keys(stored).length === 0) {
    return { ...baseDefaults }
  }
  const merged = ensureEmptyFormOptionsEvents({
    ...baseDefaults,
    ...stored,
    form: { ...baseDefaults.form, ...(stored.form || {}) },
    language: {
      ...(baseDefaults.language as Record<string, unknown>),
      ...(stored.language || {}),
      en: {
        ...((baseDefaults.language as { en?: Record<string, string> })?.en || {}),
        ...((stored.language as { en?: Record<string, string> } | undefined)?.en || {}),
        clickToUpload: clickToUploadText,
      },
    },
  })
  return merged
}
