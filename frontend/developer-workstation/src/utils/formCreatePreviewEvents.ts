/**
 * Bind designer $FNX: / FORM-CREATE handlers onto form-create Preview rules so
 * component events (blur / change) run like User Portal FormRenderer.
 */

import type { Ref } from 'vue'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import { getRuleChildren } from '@/utils/formDesigner'
import { collectFieldComponentEventsFromRules } from '@/utils/formCreateComponentEvents'
import {
  createPortalFormApi,
  isEmptyFormCreateHandler,
  parseFormCreateEventHandler,
  type PortalFormApi,
} from '@/utils/formCreateEventRuntime'
import { hasMeaningfulFormValue } from '@/utils/formCreateRuleDefaults'

const PREVIEW_DOM_ON_EVENTS = ['blur', 'change', 'focus'] as const

function normalizeHandler(raw: unknown): unknown {
  if (Array.isArray(raw)) return raw[0]
  return raw
}

function bindDomOnEvent(
  rule: Record<string, unknown>,
  eventName: string,
  raw: unknown,
  api: PortalFormApi,
): void {
  const stored = normalizeHandler(raw)
  if (stored == null || isEmptyFormCreateHandler(stored)) return
  const fn = parseFormCreateEventHandler(stored)
  if (!fn) return
  const field = String(rule.field)
  const on = (rule.on && typeof rule.on === 'object'
    ? { ...(rule.on as Record<string, unknown>) }
    : {}) as Record<string, unknown>
  rule.on = on
  on[eventName] = () => {
    fn({
      field,
      value: api.getValue(field),
      api,
      rule,
    })
  }
}

export function materializePreviewComponentEvents(
  rules: unknown[],
  previewData: Ref<Record<string, unknown>>,
): void {
  if (!Array.isArray(rules) || rules.length === 0) return
  const eventMap = collectFieldComponentEventsFromRules(rules)
  const api = createPortalFormApi(
    () => previewData.value,
    (patch) => {
      previewData.value = { ...previewData.value, ...patch }
    },
  )

  function walk(items: unknown[]) {
    for (const raw of items) {
      if (!raw || typeof raw !== 'object') continue
      const rule = raw as Record<string, unknown>
      const field = rule.field != null ? String(rule.field) : ''
      const ev = field ? eventMap.get(field) : undefined
      if (ev) {
        for (const name of PREVIEW_DOM_ON_EVENTS) {
          bindDomOnEvent(rule, name, ev.on[name], api)
        }
      }
      walk(getRuleChildren(rule))
    }
  }
  walk(rules)
}

export function materializePreviewItemsEvents(
  items: FormPreviewItem[],
  previewData: Ref<Record<string, unknown>>,
): void {
  for (const item of items) {
    if (item.kind === 'fields') {
      materializePreviewComponentEvents(item.rule, previewData)
    } else if (item.kind === 'card') {
      materializePreviewItemsEvents(item.items, previewData)
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

  function walk(items: unknown[]) {
    for (const raw of items) {
      if (!raw || typeof raw !== 'object') continue
      const rule = raw as Record<string, unknown>
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

      walk(getRuleChildren(rule))
    }
  }
  walk(rules)
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
