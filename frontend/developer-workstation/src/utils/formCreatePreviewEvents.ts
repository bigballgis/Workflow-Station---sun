/**
 * Bind designer $FNX: / FORM-CREATE handlers onto form-create Preview rules so
 * component events (blur / change) run like User Portal FormRenderer.
 */

import type { Ref } from 'vue'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import { walkFormCreateRules } from '@/utils/formDesigner'
import {
  collectFieldComponentEventsFromRules,
  runComponentFieldEventsOnValueChange,
} from '@/utils/formCreateComponentEvents'
import {
  createPortalFormApi,
  isEmptyFormCreateHandler,
  parseFormCreateEventHandler,
  type PortalFormApi,
} from '@/utils/formCreateEventRuntime'
import { hasMeaningfulFormValue } from '@/utils/formCreateRuleDefaults'
import { REQUEST_ID_FIELD } from '@/utils/formFieldMeta'
import {
  mergeRuleHookHandlers,
  mergeRuleOnHandlers,
} from '@/utils/formCreateDefaultEvents'

const PREVIEW_DOM_ON_EVENTS = ['blur', 'change', 'focus'] as const

function bucketHasHandlers(bucket: Record<string, unknown>): boolean {
  return Object.values(bucket).some((v) => !isEmptyFormCreateHandler(v))
}

/**
 * form-create getRule() often drops designer `_on` / serialized handlers.
 * Overlay persisted config_json handlers onto live preview rules by field name.
 */
export function mergeComponentEventsFromSavedRules(
  liveRules: unknown[],
  savedRules: unknown[],
): void {
  if (!Array.isArray(liveRules) || liveRules.length === 0) return
  if (!Array.isArray(savedRules) || savedRules.length === 0) return
  const savedEvents = collectFieldComponentEventsFromRules(savedRules)
  if (savedEvents.size === 0) return

  walkFormCreateRules(liveRules, (rule) => {
    const field = rule.field != null ? String(rule.field) : ''
    if (!field) return
    const saved = savedEvents.get(field)
    if (!saved) return

    const liveOn = mergeRuleOnHandlers(rule)
    if (!bucketHasHandlers(liveOn) && bucketHasHandlers(saved.on)) {
      rule.on = { ...saved.on }
    }
    const liveHook = mergeRuleHookHandlers(rule)
    if (!bucketHasHandlers(liveHook) && bucketHasHandlers(saved.hook)) {
      rule.hook = { ...saved.hook }
    }
  })
}

/** fc-designer toolbar Preview reads `on`/`hook`; merge designer `_on`/`_hook` before openPreview. */
export function syncDesignerComponentEventsForFcPreview(rules: unknown[]): void {
  if (!Array.isArray(rules) || rules.length === 0) return
  walkFormCreateRules(rules, (rule) => {
    const on = mergeRuleOnHandlers(rule)
    if (bucketHasHandlers(on)) {
      rule.on = on
    }
    const hook = mergeRuleHookHandlers(rule)
    if (bucketHasHandlers(hook)) {
      rule.hook = hook
    }
  })
}

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
  const previous = on[eventName]
  const wrapped = (...args: unknown[]) => {
    if (typeof previous === 'function') {
      try {
        ;(previous as (...a: unknown[]) => void)(...args)
      } catch {
        /* keep form-create / Element Plus handlers (e.g. blur validation) */
      }
    }
    fn({
      field,
      value: api.getValue(field),
      api,
      rule,
    })
  }
  ;(wrapped as { __hermesFormEventSource?: unknown }).__hermesFormEventSource = stored
  on[eventName] = wrapped
}

type PreviewDataBox = { value: Record<string, unknown> }

export interface PreviewFieldChangeOptions {
  requestIdConfig?: { fieldNames?: string[]; separator?: string } | null
  requestIdRecompute?: (model: Record<string, unknown>) => string | undefined
}

/** Update preview model and run component change/blur/value hooks (form-create segments + standalone lookup). */
export function dispatchPreviewFieldValueChange(
  segmentRules: unknown[],
  field: string,
  value: unknown,
  previewData: PreviewDataBox,
  options?: PreviewFieldChangeOptions,
): void {
  if (!field) return
  const patch: Record<string, unknown> = { [field]: value }
  const nextModel = { ...previewData.value, ...patch }
  const fieldNames = options?.requestIdConfig?.fieldNames
  if (fieldNames?.includes(field) && options?.requestIdRecompute) {
    patch[REQUEST_ID_FIELD] = options.requestIdRecompute(nextModel)
  }
  previewData.value = { ...previewData.value, ...patch }
  const api = createPortalFormApi(
    () => previewData.value,
    (p) => {
      previewData.value = { ...previewData.value, ...p }
    },
  )
  const ev = collectFieldComponentEventsFromRules(segmentRules).get(field)
  runComponentFieldEventsOnValueChange(ev, {
    field,
    value,
    api,
    onEvent: 'change',
    hookEvent: 'value',
    fieldType: ev?.rule?.type != null ? String(ev.rule.type) : undefined,
  })
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
    walkFormCreateRules(items, (rule) => {
      const field = rule.field != null ? String(rule.field) : ''
      const ev = field ? eventMap.get(field) : undefined
      if (ev) {
        for (const name of PREVIEW_DOM_ON_EVENTS) {
          bindDomOnEvent(rule, name, ev.on[name], api)
        }
      }
    })
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
    walkFormCreateRules(items, (rule) => {
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
