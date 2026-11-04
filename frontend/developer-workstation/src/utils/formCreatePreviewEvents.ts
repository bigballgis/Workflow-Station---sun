/**
 * Bind designer $FNX: / FORM-CREATE handlers onto form-create Preview rules so
 * component events (blur / change) run like User Portal FormRenderer.
 */

import type { Ref } from 'vue'
import type { FormPreviewItem } from '@/components/designer/formPreviewTypes'
import { walkFormCreateRules } from '@/utils/formDesigner'
import {
  collectFieldComponentEventsFromRules,
  resolveComponentEventFieldKey,
  runComponentFieldEventsOnValueChange,
  shouldMirrorBlurOnChange,
} from '@/utils/formCreateComponentEvents'
import {
  createFormEventOptionsBridge,
  createPortalFormApi,
  isEmptyFormCreateHandler,
  parseFormCreateEventHandler,
  runFormOnChangeHandler,
  type PortalFormApi,
  type PortalFormRequiredState,
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'
import type { PortalFormApiOverlays } from '@/utils/formCreateEventOverlays'
import { bindPreviewEventExecution, runPreviewFieldExecution } from './previewEventExecution'
import { eventSequence } from '@platform-shared/formEvents/async'
import { runNativeFormEvent } from '@platform-shared/formEvents/native'
import { REQUEST_ID_FIELD } from '@/utils/formFieldMeta'
import {
  mergeRuleHookHandlers,
  mergeRuleOnHandlers,
} from '@/utils/formCreateDefaultEvents'

export {
  applyPreviewDefaultsToItemRules,
  attachPreviewMountedDefaultSync,
  injectPreviewFieldLoadDefaults,
  sanitizePreviewItemsHandlers,
  sanitizePreviewRuleHandlers,
} from '@/utils/formCreatePreviewDefaults'

function bucketHasHandlers(bucket: Record<string, unknown>): boolean {
  return Object.values(bucket).some((v) => !isEmptyFormCreateHandler(v))
}

/**
 * form-create getRule() often drops designer `_on` / serialized handlers.
 * Overlay persisted config_json handlers onto live preview rules by field name.
 *
 * IMPORTANT: saved handlers are serialized `$FNX:` / function-body STRINGS, not
 * compiled functions. They MUST land in the designer shadow buckets `_on` / `_hook`,
 * NEVER on `on` / `hook` — form-create executes `rule.on` / `rule.hook` directly via
 * `forEach(hooks, w => w(...))`, so a raw string there throws `w is not a function`
 * on every render tick and freezes Form Preview (the loading spinner never clears).
 * The preview pipeline consumes `_on` / `_hook` later: `collectFieldComponentEventsFromRules`
 * merges both buckets and `parseFormCreateEventHandler` compiles the strings before running them.
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
    const field = resolveComponentEventFieldKey(rule)
    if (!field) return
    const saved = savedEvents.get(field)
    if (!saved) return

    const liveOn = mergeRuleOnHandlers(rule)
    if (!bucketHasHandlers(liveOn) && bucketHasHandlers(saved.on)) {
      rule._on = { ...saved.on }
    }
    const liveHook = mergeRuleHookHandlers(rule)
    if (!bucketHasHandlers(liveHook) && bucketHasHandlers(saved.hook)) {
      rule._hook = { ...saved.hook }
    }
  })
}

/**
 * Copy serialized `$FNX:` / FORM-CREATE strings from `on`/`hook` into `_on`/`_hook`
 * before sanitize strips them — otherwise SubTable form-create dialogs lose events.
 */
export function preserveSerializedHandlersInShadowBuckets(rules: unknown[]): void {
  if (!Array.isArray(rules) || rules.length === 0) return
  walkFormCreateRules(rules, (rule) => {
    for (const [liveKey, shadowKey] of [['on', '_on'], ['hook', '_hook']] as const) {
      const live = (rule[liveKey] && typeof rule[liveKey] === 'object'
        ? rule[liveKey] as Record<string, unknown>
        : {})
      const shadow = (rule[shadowKey] && typeof rule[shadowKey] === 'object'
        ? { ...(rule[shadowKey] as Record<string, unknown>) }
        : {})
      let changed = false
      for (const [name, raw] of Object.entries(live)) {
        const stored = unwrapHermesHandlerSource(normalizeHandler(raw))
        if ((typeof stored !== 'string' && !Array.isArray(stored)) || isEmptyFormCreateHandler(stored)) continue
        const existing = unwrapHermesHandlerSource(normalizeHandler(shadow[name]))
        if ((typeof existing === 'string' || Array.isArray(existing)) && !isEmptyFormCreateHandler(existing)) continue
        shadow[name] = stored
        changed = true
      }
      if (changed) rule[shadowKey] = shadow
    }
  })
}

/** fc-designer toolbar Preview reads `on`/`hook` as real functions (not `$FNX:` strings). */
export function syncDesignerComponentEventsForFcPreview(rules: unknown[], hooks?: Parameters<typeof runNativeFormEvent>[4]): void {
  if (!Array.isArray(rules) || rules.length === 0) return

  walkFormCreateRules(rules, (rule) => {
    const field = resolveComponentEventFieldKey(rule)
    if (!field) return

    const onSources = pickFcPreviewHandlerSources(rule, 'on')
    const hookSources = pickFcPreviewHandlerSources(rule, 'hook')
    if (!bucketHasHandlers(onSources) && !bucketHasHandlers(hookSources)) return

    // form-create injects { api, self, args, ... } as the first arg when inject=true
    rule.inject = true

    const onOut: Record<string, unknown> = {}
    for (const [name, raw] of Object.entries(onSources)) {
      const wrapped = compileFcPreviewInjectHandler(rule, field, raw, hooks, true)
      if (wrapped) onOut[name] = wrapped
    }
    // Select-like: designer often only fills on.blur; native Select fires change more reliably.
    if (shouldMirrorBlurOnChange(rule) && onSources.blur && isEmptyFormCreateHandler(onSources.change)) {
      const blurWrapped = compileFcPreviewInjectHandler(rule, field, onSources.blur, hooks, true)
      if (blurWrapped) onOut.change = blurWrapped
    }
    if (Object.keys(onOut).length) rule.on = onOut

    const hookOut: Record<string, unknown> = {}
    for (const [name, raw] of Object.entries(hookSources)) {
      const wrapped = compileFcPreviewInjectHandler(rule, field, raw, hooks)
      if (wrapped) hookOut[name] = wrapped
    }
    if (Object.keys(hookOut).length) rule.hook = hookOut
  })
}

/** Prefer designer shadow `_on`/`_hook` strings over previously compiled `on`/`hook` functions. */
function pickFcPreviewHandlerSources(
  rule: Record<string, unknown>,
  kind: 'on' | 'hook',
): Record<string, unknown> {
  const shadowKey = kind === 'on' ? '_on' : '_hook'
  const shadow = (rule[shadowKey] && typeof rule[shadowKey] === 'object'
    ? rule[shadowKey] as Record<string, unknown>
    : {})
  const live = (rule[kind] && typeof rule[kind] === 'object'
    ? rule[kind] as Record<string, unknown>
    : {})
  const keys = new Set([...Object.keys(shadow), ...Object.keys(live)])
  const out: Record<string, unknown> = {}
  for (const name of keys) {
    const fromShadow = unwrapHermesHandlerSource(normalizeHandler(shadow[name]))
    const fromLive = unwrapHermesHandlerSource(normalizeHandler(live[name]))
    if (typeof fromShadow === 'string' && !isEmptyFormCreateHandler(fromShadow)) {
      out[name] = fromShadow
    } else if (typeof fromLive === 'string' && !isEmptyFormCreateHandler(fromLive)) {
      out[name] = fromLive
    } else if (fromShadow != null && !isEmptyFormCreateHandler(fromShadow)) {
      out[name] = fromShadow
    } else if (fromLive != null && !isEmptyFormCreateHandler(fromLive)) {
      out[name] = fromLive
    }
  }
  return out
}

function unwrapHermesHandlerSource(raw: unknown): unknown {
  if (typeof raw === 'function') {
    const tagged = raw as { __hermesFormEventSource?: unknown; __json?: unknown }
    if (tagged.__hermesFormEventSource != null) return tagged.__hermesFormEventSource
    if (typeof tagged.__json === 'string' || Array.isArray(tagged.__json)) return tagged.__json
  }
  return raw
}

/**
 * Compile `$FNX:` / FORM-CREATE strings into functions form-create can invoke.
 * Call signature: `handler(inject)` when rule.inject=true (FcDesigner EventConfig).
 */
type FcPreviewHandlerFn = ((...args: unknown[]) => void) & {
  __hermesFormEventSource?: unknown
  /** form-create toJson reads this — required so openPreview getJson→parseJson keeps $FNX: */
  __json?: unknown
  __inject?: boolean
}

function tagFcPreviewHandlerForJsonRoundtrip(
  handler: FcPreviewHandlerFn,
  source: unknown,
): FcPreviewHandlerFn {
  handler.__hermesFormEventSource = source
  // fc-designer openPreview does getJson→parseJson. Without __json, toJson stringifies the
  // closure (`[[FORM-CREATE-PREFIX-function…]]`) and parseFn rebuilds a broken function that
  // no longer closes over api/field — Case Type (and other) events then appear to "do nothing".
  if ((typeof source === 'string' || Array.isArray(source)) && !isEmptyFormCreateHandler(source)) {
    handler.__json = source
    handler.__inject = true
  }
  return handler
}

function compileFcPreviewInjectHandler(
  rule: Record<string, unknown>,
  field: string,
  raw: unknown,
  hooks?: Parameters<typeof runNativeFormEvent>[4],
  controlEvent = false,
): ((...args: unknown[]) => void) | undefined {
  const stored = unwrapHermesHandlerSource(normalizeHandler(raw))
  if (stored == null || isEmptyFormCreateHandler(stored)) return undefined
  if (typeof stored === 'function') {
    const existing = stored as FcPreviewHandlerFn
    const source = existing.__hermesFormEventSource ?? existing.__json
    if (source != null) tagFcPreviewHandlerForJsonRoundtrip(existing, source)
    return existing
  }
  const fn = parseFormCreateEventHandler(stored)
  if (!fn) return undefined

  const wrapped: FcPreviewHandlerFn = (...args: unknown[]) => {
    const first = args[0]
    const inject =
      first && typeof first === 'object'
        ? (first as Record<string, unknown>)
        : null
    const fcApi =
      inject?.api && typeof (inject.api as PortalFormApi).setValue === 'function'
        ? (inject.api as PortalFormApi)
        : args.find(
          (a) => a && typeof a === 'object' && typeof (a as PortalFormApi).setValue === 'function',
        ) as PortalFormApi | undefined
    if (!fcApi) {
      console.warn('[formCreatePreviewEvents] missing form-create api for field event', field)
      return
    }

    const api = createFormEventOptionsBridge(fcApi, rule)
    return runNativeFormEvent(fcApi, stored, {
      field,
      formEvent: controlEvent ? '__control__' : undefined,
      value: resolveFcPreviewHandlerValue(args, api, field),
      api,
      rule,
      args: Array.isArray(inject?.args) ? inject.args as unknown[] : args,
    }, parseFormCreateEventHandler, hooks ?? { changed: (pending, error) => activePreviewVisibilityBridge?.executionChanged?.(pending, error),
      register: host => activePreviewVisibilityBridge?.registerExecution?.(host) })
  }
  return tagFcPreviewHandlerForJsonRoundtrip(wrapped, stored)
}

function normalizeHandler(raw: unknown): unknown {
  return raw
}

/** Prefer the live change/blur argument; getValue is often still the previous model. */
function resolveFcPreviewHandlerValue(
  args: unknown[],
  api: PortalFormApi,
  field: string,
): unknown {
  const first = args[0]
  const inject = first && typeof first === 'object' ? first as Record<string, unknown> : null
  const injectArgs = Array.isArray(inject?.args) ? inject.args as unknown[] : args
  let value: unknown = inject?.value
  if (value === undefined) value = injectArgs[0]
  if (value && typeof value === 'object' && 'target' in (value as object)) {
    value = api.getValue(field)
  }
  if (value === undefined && typeof api.getValue === 'function') {
    value = api.getValue(field)
  }
  return value
}

function bindDomOnEvent(
  rule: Record<string, unknown>,
  eventName: string,
  raw: unknown,
  api: PortalFormApi,
): void {
  const stored = normalizeHandler(raw)
  if (stored == null || isEmptyFormCreateHandler(stored)) return
  const source = unwrapHermesHandlerSource(stored)
  if (typeof source === 'function') return // Native parser handler, not a persisted user script.
  const fn = parseFormCreateEventHandler(source)
  if (!fn) return
  const field = String(rule.field)
  const on = (rule.on && typeof rule.on === 'object'
    ? { ...(rule.on as Record<string, unknown>) }
    : {}) as Record<string, unknown>
  rule.on = on
  const wrapped = (...args: unknown[]) => {
    return fn({
      field,
      value: resolveFcPreviewHandlerValue(args, api, field),
      api,
      rule,
      args,
    })
  }
  ;(wrapped as { __hermesFormEventSource?: unknown }).__hermesFormEventSource = source
  on[eventName] = wrapped
}

type PreviewDataBox = { value: Record<string, unknown> }

/** Optional bridge so Preview `api.hidden` / `api.display` can hide subTables (parity with Portal). */
export interface PreviewVisibilityBridge {
  readonlyField?: (field: string) => boolean
  assertWritable?: (field: string) => void
  readonly scopeKey?: object
  fieldErrors?: { setFieldError(field: string, message: string): void; clearFieldError(field: string): void }
  snapshot?: () => unknown
  restore?: (saved: unknown) => void
  executionChanged?: (pending: boolean, error: unknown) => void
  registerExecution?: (host: { cancel(): void; wait(): Promise<boolean> }) => void
  state: PortalFormVisibilityState
  notify: () => void
  getAllFieldKeys: () => string[]
  requiredState?: PortalFormRequiredState
  notifyRequired?: () => void
  overlays?: PortalFormApiOverlays
}

/**
 * materializePreviewComponentEvents runs before FormPreviewItems mounts.
 * Root preview registers here so rule.on handlers can call api.hidden afterwards.
 */
let activePreviewVisibilityBridge: PreviewVisibilityBridge | null = null

export function registerPreviewVisibilityBridge(bridge: PreviewVisibilityBridge): void {
  activePreviewVisibilityBridge = bridge
}

export function unregisterPreviewVisibilityBridge(bridge: PreviewVisibilityBridge): void {
  if (activePreviewVisibilityBridge === bridge) activePreviewVisibilityBridge = null
}

/** Deferred bridge for APIs created at preview-build time (state resolves after mount). */
export function createDeferredPreviewVisibilityBridge(): PreviewVisibilityBridge {
  return {
    get scopeKey() { return activePreviewVisibilityBridge ?? undefined },
    assertWritable: field => activePreviewVisibilityBridge?.assertWritable?.(field),
    readonlyField: field => activePreviewVisibilityBridge?.readonlyField?.(field) ?? false,
    snapshot: () => activePreviewVisibilityBridge?.snapshot?.(),
    restore: saved => activePreviewVisibilityBridge?.restore?.(saved),
    executionChanged: (pending, error) => activePreviewVisibilityBridge?.executionChanged?.(pending, error),
    registerExecution: host => activePreviewVisibilityBridge?.registerExecution?.(host),
    get state() {
      return activePreviewVisibilityBridge?.state as PortalFormVisibilityState
    },
    notify: () => {
      activePreviewVisibilityBridge?.notify()
    },
    getAllFieldKeys: () => activePreviewVisibilityBridge?.getAllFieldKeys() ?? [],
    get requiredState() {
      return activePreviewVisibilityBridge?.requiredState
    },
    notifyRequired: () => {
      activePreviewVisibilityBridge?.notifyRequired?.()
    },
    get overlays() {
      return activePreviewVisibilityBridge?.overlays
    },
    get fieldErrors() { return activePreviewVisibilityBridge?.fieldErrors },
  }
}

function deferredOverlayBags(): PortalFormApiOverlays {
  return {
    get info() { return activePreviewVisibilityBridge?.overlays?.info },
    get disabled() {
      return activePreviewVisibilityBridge?.overlays?.disabled
    },
    get options() {
      return activePreviewVisibilityBridge?.overlays?.options
    },
    get labels() {
      return activePreviewVisibilityBridge?.overlays?.labels
    },
    get notifications() {
      return activePreviewVisibilityBridge?.overlays?.notifications
    },
    get lookupFilter() {
      return activePreviewVisibilityBridge?.overlays?.lookupFilter
    },
    get focus() {
      return activePreviewVisibilityBridge?.overlays?.focus
    },
  }
}

function deferredRequiredBag(): {
  state: PortalFormRequiredState
  notify: () => void
  getAllFieldKeys: () => string[]
} {
  return {
    get state() {
      return activePreviewVisibilityBridge?.requiredState as PortalFormRequiredState
    },
    notify: () => {
      activePreviewVisibilityBridge?.notifyRequired?.()
    },
    getAllFieldKeys: () => activePreviewVisibilityBridge?.getAllFieldKeys() ?? [],
  }
}

function requiredBagFromVisibility(
  visibility?: PreviewVisibilityBridge,
): { state: PortalFormRequiredState; notify: () => void; getAllFieldKeys: () => string[] } | undefined {
  if (!visibility) return undefined
  return {
    get state() {
      return visibility.requiredState as PortalFormRequiredState
    },
    notify: () => {
      visibility.notifyRequired?.()
    },
    getAllFieldKeys: visibility.getAllFieldKeys,
  }
}

export interface PreviewFieldChangeOptions {
  formOnChange?: unknown
  programmatic?: boolean
  requestIdConfig?: { fieldNames?: string[]; separator?: string } | null
  requestIdRecompute?: (model: Record<string, unknown>) => string | undefined
  visibility?: PreviewVisibilityBridge
}

/** Update preview model and run component change/blur/value hooks (form-create segments + standalone lookup). */
export function dispatchPreviewFieldValueChange(
  segmentRules: unknown[],
  field: string,
  value: unknown,
  previewData: PreviewDataBox,
  options?: PreviewFieldChangeOptions,
): unknown {
  if (!field) return
  const patch: Record<string, unknown> = { [field]: value }
  const nextModel = { ...previewData.value, ...patch }
  const fieldNames = options?.requestIdConfig?.fieldNames
  if (fieldNames?.includes(field) && options?.requestIdRecompute) {
    patch[REQUEST_ID_FIELD] = options.requestIdRecompute(nextModel)
  }
  const api = createPortalFormApi(
    () => previewData.value,
    (p) => {
      Object.keys(p).forEach(field => options?.visibility?.assertWritable?.(field))
      previewData.value = { ...previewData.value, ...p }
    },
    undefined,
    options?.visibility,
    options?.visibility?.fieldErrors,
    requiredBagFromVisibility(options?.visibility),
    options?.visibility?.overlays,
  )
  const ev = collectFieldComponentEventsFromRules(segmentRules).get(field)
  return runPreviewFieldExecution(previewData, api, { get: () => previewData.value, set: data => { previewData.value = data } }, options?.visibility, field, value, scoped => {
  previewData.value = { ...previewData.value, ...patch }
  return eventSequence([() => runComponentFieldEventsOnValueChange(ev, {
    field,
    value,
    api: scoped,
    onEvent: 'change',
    hookEvent: 'value',
    fieldType: ev?.rule?.type != null ? String(ev.rule.type) : undefined,
  }), () => runFormOnChangeHandler(options?.formOnChange, field, value, scoped, ev?.rule ?? {})])
  }, options?.programmatic)
}

export function materializePreviewComponentEvents(
  rules: unknown[],
  previewData: Ref<Record<string, unknown>>,
  changeOwner: 'native' | 'host' = 'native',
): void {
  if (!Array.isArray(rules) || rules.length === 0) return
  const eventMap = collectFieldComponentEventsFromRules(rules)
  // Visibility bridge registers when FormPreviewItems mounts (after this runs).
  const api = createPortalFormApi(
    () => previewData.value,
    (patch) => {
      Object.keys(patch).forEach(field => activePreviewVisibilityBridge?.assertWritable?.(field))
      previewData.value = { ...previewData.value, ...patch }
    },
    undefined,
    createDeferredPreviewVisibilityBridge(),
    { setFieldError: (field, message) => activePreviewVisibilityBridge?.fieldErrors?.setFieldError(field, message),
      clearFieldError: field => activePreviewVisibilityBridge?.fieldErrors?.clearFieldError(field) },
    deferredRequiredBag(),
    deferredOverlayBags(),
  )
  bindPreviewEventExecution(previewData, api, { get: () => previewData.value, set: data => { previewData.value = data } }, createDeferredPreviewVisibilityBridge(), true)

  function walk(items: unknown[]) {
    walkFormCreateRules(items, (rule) => {
      const field = resolveComponentEventFieldKey(rule)
      const ev = field ? eventMap.get(field) : undefined
      if (ev) {
        rule._on = { ...(rule._on as Record<string, unknown>), ...Object.fromEntries(Object.entries(ev.on).map(([name, raw]) => [name, unwrapHermesHandlerSource(raw)])) }
        for (const name of Object.keys(ev.on)) {
          if (name === 'change' && changeOwner === 'host') {
            const deferred = () => undefined
            Object.assign(deferred, { __hermesFormEventSource: ev.on.change })
            rule.on = { ...(rule.on as Record<string, unknown>), change: deferred }
            continue
          }
          bindDomOnEvent(rule, name, ev.on[name], api)
        }
        rule._hook = { ...(rule._hook as Record<string, unknown>), ...ev.hook }
        const hooks: Record<string, unknown> = {}
        for (const [name, raw] of Object.entries(ev.hook)) {
          const source = unwrapHermesHandlerSource(raw)
          // Preserve native designer/parser hooks with their native injection signature.
          if (typeof source === 'function') { hooks[name] = source; continue }
          const handler = parseFormCreateEventHandler(source)
          if (!handler) continue
          const wrapped = (...args: unknown[]) => name === 'value' && changeOwner === 'host' ? undefined
            : handler({ field, value: api.getValue(field), api, rule, args })
          Object.assign(wrapped, { __hermesFormEventSource: source })
          hooks[name] = wrapped
        }
        rule.hook = hooks
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
      materializePreviewComponentEvents(item.rule, previewData, 'host')
    } else if (item.kind === 'card') {
      materializePreviewItemsEvents(item.items, previewData)
    }
  }
}

