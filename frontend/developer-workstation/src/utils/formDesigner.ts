/**
 * Form Designer Utilities
 * Pure functions for form designer operations — no Vue reactivity dependency.
 */

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
  return ensureEmptyFormOptionsEvents({
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
}
