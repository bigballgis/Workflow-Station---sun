import type { FormField } from '@/components/formRendererHelpers'
import { isCannotDownload, uploadPropsBlockDownload } from '@/utils/filePreview'

const DEFAULT_UPLOAD_URL = '/api/v1/upload'
const EMPTY_BLOCKED_KEYS = new Set<string>()
const blockedKeysCache = new WeakMap<object, Set<string>>()

type FormLike = { data?: unknown; configJson?: unknown }

/**
 * Copy designer upload props onto a FormField (action/accept/limit + cannotDownload).
 * Honors both `cannotDownload` (designer switch) and form-create native `canNotDownload`.
 * `blockedFieldKeys` covers FU scene copies that never received the switch (My Request / TASK).
 */
export function applyUploadPropsFromRule(
  field: FormField,
  rule: { type?: string; props?: Record<string, unknown>; cannotDownload?: unknown; canNotDownload?: unknown },
  blockedFieldKeys?: Set<string>,
): void {
  if (rule.type !== 'upload') return
  const props = rule.props && typeof rule.props === 'object' ? rule.props : {}
  const action = props.action
  field.uploadUrl = (typeof action === 'string' && action && action !== '/')
    ? action
    : DEFAULT_UPLOAD_URL
  field.uploadAccept = typeof props.accept === 'string' ? props.accept : ''
  field.uploadLimit = typeof props.limit === 'number' ? props.limit : 1
  if (uploadRuleBlocksDownload(rule) || (field.key != null && blockedFieldKeys?.has(field.key))) {
    field.cannotDownload = true
  }
}

export function uploadRuleBlocksDownload(
  rule: { type?: string; props?: Record<string, unknown>; cannotDownload?: unknown; canNotDownload?: unknown } | null | undefined,
): boolean {
  if (!rule || rule.type !== 'upload') return false
  return uploadPropsBlockDownload(rule.props)
    || isCannotDownload(rule.cannotDownload)
    || isCannotDownload(rule.canNotDownload)
}

export function stampCannotDownloadProp(
  target: Record<string, unknown>,
  sourceProps: Record<string, unknown> | undefined,
  fieldKey?: string,
  blockedFieldKeys?: Set<string>,
): void {
  if (uploadPropsBlockDownload(sourceProps) || (fieldKey != null && blockedFieldKeys?.has(fieldKey))) {
    target.cannotDownload = true
  }
}

/** Cached per `content.forms` array instance (loaders set it once per FU fetch). */
export function cannotDownloadFieldKeysFromForms(forms: FormLike[] | null | undefined): Set<string> {
  if (!forms?.length) return EMPTY_BLOCKED_KEYS
  const hit = blockedKeysCache.get(forms)
  if (hit) return hit
  const keys = collectCannotDownloadFieldKeysFromForms(forms)
  blockedKeysCache.set(forms, keys)
  return keys
}

export function collectCannotDownloadFieldKeysFromForms(forms: FormLike[] | null | undefined): Set<string> {
  const keys = new Set<string>()
  if (!forms?.length) return keys
  for (const form of forms) {
    collectFromConfig(keys, form.data ?? form.configJson)
  }
  return keys
}

function collectFromConfig(keys: Set<string>, raw: unknown): void {
  let cfg: unknown = raw
  if (typeof raw === 'string') {
    try {
      cfg = JSON.parse(raw)
    } catch {
      // FALLBACK(ux): malformed form JSON skips cannot-download enrichment only.
      return
    }
  }
  if (!cfg || typeof cfg !== 'object') return
  const obj = cfg as Record<string, unknown>
  walkUploadRules(keys, obj.rule)
  const subForms = obj.subForms
  if (!subForms || typeof subForms !== 'object') return
  for (const sub of Object.values(subForms as Record<string, unknown>)) {
    if (sub && typeof sub === 'object') {
      walkUploadRules(keys, (sub as Record<string, unknown>).rule)
    }
  }
}

function walkUploadRules(keys: Set<string>, rules: unknown): void {
  if (!Array.isArray(rules)) return
  const stack = [...rules] as Array<Record<string, unknown>>
  while (stack.length) {
    const rule = stack.pop()
    if (!rule || typeof rule !== 'object') continue
    const children = rule.children
    if (Array.isArray(children)) {
      for (const child of children) {
        if (child && typeof child === 'object') stack.push(child as Record<string, unknown>)
      }
    }
    if (typeof rule.field !== 'string') continue
    if (uploadRuleBlocksDownload(rule as { type?: string; props?: Record<string, unknown> })) {
      keys.add(rule.field)
    }
  }
}
