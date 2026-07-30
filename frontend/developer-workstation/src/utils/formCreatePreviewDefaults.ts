/**
 * Preview default value sync + sanitize non-function on/hook entries for form-create.
 * Split from formCreatePreviewEvents to stay under file-length limits.
 */

import type { Ref } from 'vue'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import { walkFormCreateRules } from '@/utils/formDesigner'
import {
  createPortalFormApi,
  parseFormCreateEventHandler,
  type PortalFormApi,
} from '@/utils/formCreateEventRuntime'
import { hasMeaningfulFormValue } from '@/utils/formCreateRuleDefaults'

/**
 * Strip any non-function entry from `on` / `hook` on preview rules.
 *
 * Preview renders through the base @form-create/element-ui instance, which does NOT
 * understand the designer's `$FNX:` serialized-handler prefix — it calls the value as-is.
 * Real function handlers the preview pipeline installs are kept. `_on` / `_hook` are untouched.
 */
export function sanitizePreviewRuleHandlers(rules: unknown[]): void {
  if (!Array.isArray(rules) || rules.length === 0) return
  walkFormCreateRules(rules, (rule) => {
    for (const bucketKey of ['on', 'hook'] as const) {
      const bucket = rule[bucketKey]
      if (!bucket || typeof bucket !== 'object') continue
      const map = bucket as Record<string, unknown>
      for (const name of Object.keys(map)) {
        if (typeof map[name] !== 'function') delete map[name]
      }
      if (Object.keys(map).length === 0) delete rule[bucketKey]
    }
  })
}

export function sanitizePreviewItemsHandlers(items: FormPreviewItem[]): void {
  for (const item of items) {
    if (item.kind === 'fields') {
      sanitizePreviewRuleHandlers(item.rule)
    } else if (item.kind === 'card') {
      sanitizePreviewItemsHandlers(item.items)
    }
  }
}

function previewApiFromRef(previewData: Ref<Record<string, unknown>>): PortalFormApi {
  return createPortalFormApi(
    () => previewData.value,
    (patch) => {
      previewData.value = { ...previewData.value, ...patch }
    },
  )
}

function resolveFieldInitialValue(
  rule: Record<string, unknown>,
  previewData: Ref<Record<string, unknown>>,
): unknown {
  const field = rule.field != null ? String(rule.field) : ''
  if (!field) return undefined
  const fromModel = previewData.value[field]
  if (hasMeaningfulFormValue(fromModel)) return fromModel
  const fromRule = rule.value
  if (hasMeaningfulFormValue(fromRule)) return fromRule
  const props = rule.props as Record<string, unknown> | undefined
  if (hasMeaningfulFormValue(props?.value)) return props?.value
  return undefined
}

/** Per-field hook.load: form-create often ignores pre-mount v-model for select. */
export function injectPreviewFieldLoadDefaults(
  rules: unknown[],
  previewData: Ref<Record<string, unknown>>,
): void {
  const portalApi = previewApiFromRef(previewData)

  walkFormCreateRules(rules, (rule) => {
    const field = rule.field != null ? String(rule.field) : ''
    const initial = field ? resolveFieldInitialValue(rule, previewData) : undefined

    if (field && hasMeaningfulFormValue(initial)) {
      const hook = (rule.hook && typeof rule.hook === 'object'
        ? { ...(rule.hook as Record<string, unknown>) }
        : {}) as Record<string, unknown>
      const prevLoad = hook.load
      hook.load = (inject: { api?: PortalFormApi; rule?: Record<string, unknown> }) => {
        const api = (inject?.api && typeof inject.api.setValue === 'function'
          ? inject.api
          : portalApi) as PortalFormApi
        const f = inject?.rule?.field != null ? String(inject.rule.field) : field
        api.setValue(f, initial)
        if (typeof prevLoad === 'function') {
          try {
            (prevLoad as (arg: unknown) => void)(inject)
          } catch {
            /* ignore chained load errors */
          }
        }
      }
      rule.hook = hook
    }
  })
}

/** Form-level onMounted: push previewData into form-create after mount. */
export function attachPreviewMountedDefaultSync(
  option: Record<string, unknown>,
  previewData: Ref<Record<string, unknown>>,
): Record<string, unknown> {
  const merged = { ...option }
  const prevRaw = merged.onMounted
  const prevFn = typeof prevRaw === 'function'
    ? (prevRaw as (api: PortalFormApi) => void)
    : (prevRaw ? parseFormCreateEventHandler(prevRaw) : null)

  merged.onMounted = (rawApi: unknown) => {
    const portalApi = previewApiFromRef(previewData)
    const api = (rawApi && typeof (rawApi as PortalFormApi).setValue === 'function'
      ? (rawApi as PortalFormApi)
      : portalApi)
    for (const [field, val] of Object.entries(previewData.value)) {
      if (!hasMeaningfulFormValue(val)) continue
      api.setValue(field, val)
    }
    const onChangeFn = merged.onChange
    if (typeof onChangeFn === 'function') {
      try {
        onChangeFn('__bootstrap__', null, { api })
      } catch {
        /* ignore bootstrap onChange errors */
      }
    }
    if (prevFn) {
      try {
        prevFn((rawApi ?? portalApi) as PortalFormApi)
      } catch {
        /* ignore chained onMounted errors */
      }
    }
  }
  return merged
}

export function applyPreviewDefaultsToItemRules(
  items: FormPreviewItem[],
  previewData: Ref<Record<string, unknown>>,
): void {
  for (const item of items) {
    if (item.kind === 'fields') {
      injectPreviewFieldLoadDefaults(item.rule, previewData)
    } else if (item.kind === 'card') {
      applyPreviewDefaultsToItemRules(item.items, previewData)
    }
  }
}
