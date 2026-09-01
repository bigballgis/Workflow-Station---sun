import type { InjectionKey } from 'vue'
import {
  createPortalFormApi,
  isEmptyFormCreateHandler,
  runFormOnChangeHandler,
  type PortalFormRequiredState,
  type PortalFormVisibilityState,
} from '@/utils/formCreateEventRuntime'
import type { PreviewVisibilityBridge } from '@/utils/formCreatePreviewEvents'
import { getRuleChildren } from '@/utils/formDesigner'
import { isFormCreateRuleRequired } from '@/utils/formCreateValidateRules'
import { isFormCreateRuleReadonly } from '@/utils/formCreateRuleUtils'
import { isTableAuditField } from '@/utils/tableAuditFields'
import type { PortalFormApiOverlays, PortalFormDisabledState, FormEventChoiceOption, FormEventLookupFilter, FormEventNotification } from '@/utils/formCreateEventOverlays'

export {
  registerPreviewVisibilityBridge,
  unregisterPreviewVisibilityBridge,
} from '@/utils/formCreatePreviewEvents'

/**
 * Replace form-level onChange so api.hidden/display hit Preview visibility
 * (fc native api cannot hide extracted kind:'lookup' items).
 */
export function withPreviewVisibilityFormOnChange(
  option: Record<string, unknown>,
  deps: {
    getFormData: () => Record<string, unknown>
    applyPatch: (patch: Record<string, unknown>) => void
    getVisibility: () => PreviewVisibilityBridge
  },
): Record<string, unknown> {
  const raw = option.onChange
  if (raw == null || isEmptyFormCreateHandler(raw)) return option
  return {
    ...option,
    onChange: (field: string, value: unknown) => {
      const vis = deps.getVisibility()
      const api = createPortalFormApi(
        deps.getFormData,
        deps.applyPatch,
        undefined,
        vis,
        undefined,
        vis.requiredState
          ? {
              state: vis.requiredState,
              notify: vis.notifyRequired ?? vis.notify,
              getAllFieldKeys: vis.getAllFieldKeys,
            }
          : undefined,
        vis.overlays,
      )
      const tagged = typeof raw === 'function'
        ? (raw as { __hermesFormEventSource?: unknown }).__hermesFormEventSource
        : undefined
      const source = tagged != null ? tagged : raw
      runFormOnChangeHandler(source, field, value, api, {})
    },
  }
}

/** Shared script visibility (`api.hidden` / `api.display`) for Form Preview tree. */
export interface PreviewEventVisibilityCtx extends PreviewVisibilityBridge {
  state: PortalFormVisibilityState
  tick: { value: number }
  isFieldVisible: (fieldKey: string) => boolean
  requiredState: PortalFormRequiredState
  requiredTick: { value: number }
  notifyRequired: () => void
  overlays: PortalFormApiOverlays
  overlayTick: { value: number }
  formNotifications: { value: FormEventNotification[] }
  eventDisabledState: PortalFormDisabledState
  scriptOptionsFor: (fieldKey: string) => FormEventChoiceOption[] | undefined
  scriptLabelOverlay: (fieldKey: string) => string | undefined
  scriptLookupFiltersFor: (fieldKey: string) => FormEventLookupFilter[]
  hasScriptLookupFilter: (fieldKey: string) => boolean
}

export const PREVIEW_EVENT_VISIBILITY_KEY: InjectionKey<PreviewEventVisibilityCtx> =
  Symbol('previewEventVisibility')

/**
 * Write mapped children back into the same nesting slot `getRuleChildren` would read.
 * Also walks `rule` arrays used by some Preview snapshots (not in getRuleChildren).
 */
function withMappedChildren(
  rule: Record<string, unknown>,
  mapChildren: (children: unknown[]) => unknown[],
): void {
  const sources: Array<{ get: () => unknown; set: (v: unknown[]) => void }> = [
    {
      get: () => rule.children,
      set: (v) => {
        rule.children = v
      },
    },
    {
      get: () => (rule.props as Record<string, unknown> | undefined)?.children,
      set: (v) => {
        const props = { ...((rule.props as Record<string, unknown>) || {}) }
        props.children = v
        rule.props = props
      },
    },
    {
      get: () => (rule.props as Record<string, unknown> | undefined)?.list,
      set: (v) => {
        const props = { ...((rule.props as Record<string, unknown>) || {}) }
        props.list = v
        rule.props = props
      },
    },
    {
      get: () => (rule.props as Record<string, unknown> | undefined)?.items,
      set: (v) => {
        const props = { ...((rule.props as Record<string, unknown>) || {}) }
        props.items = v
        rule.props = props
      },
    },
    {
      get: () => (rule.props as Record<string, unknown> | undefined)?.fields,
      set: (v) => {
        const props = { ...((rule.props as Record<string, unknown>) || {}) }
        props.fields = v
        rule.props = props
      },
    },
  ]
  for (const source of sources) {
    if (Array.isArray(source.get())) {
      source.set(mapChildren(source.get() as unknown[]))
      break
    }
  }
  if (Array.isArray(rule.rule)) {
    rule.rule = mapChildren(rule.rule as unknown[])
  }
}

/**
 * Apply script visibility onto form-create rules without wiping Designer Hide.
 * `hidden = designerHidden || !scriptVisible`
 */
export function applyPreviewVisibilityToRules(
  rules: unknown[],
  isFieldVisible: (fieldKey: string) => boolean,
): unknown[] {
  if (!Array.isArray(rules) || rules.length === 0) return []
  const apply = (nodes: unknown[]): unknown[] =>
    nodes.map((node) => {
      if (!node || typeof node !== 'object') return node
      const rule = { ...(node as Record<string, unknown>) }
      withMappedChildren(rule, apply)
      if (rule.field != null) {
        const designerHidden = rule.hidden === true
        const scriptVisible = isFieldVisible(String(rule.field))
        rule.hidden = designerHidden || !scriptVisible
      }
      return rule
    })
  return apply(rules)
}

function applyRequiredFlagToPreviewRule(
  rule: Record<string, unknown>,
  need: boolean,
): void {
  rule.$required = need
  const validate = Array.isArray(rule.validate) ? [...rule.validate] : []
  const withoutRequired = validate.filter((entry) => {
    if (!entry || typeof entry !== 'object') return true
    const item = entry as Record<string, unknown>
    return item.required !== true && item.mode !== 'required'
  })
  if (need) {
    const hasRequired = validate.some((entry) => {
      if (!entry || typeof entry !== 'object') return false
      const item = entry as Record<string, unknown>
      return item.required === true || item.mode === 'required'
    })
    rule.validate = hasRequired ? validate : [{ required: true }, ...withoutRequired]
  } else {
    rule.validate = withoutRequired.length > 0 ? withoutRequired : undefined
  }
}

/** Apply script `api.required` onto form-create rules (`$required` + validate). */
export function applyPreviewRequiredToRules(
  rules: unknown[],
  isFieldRequired: (fieldKey: string, designerRequired: boolean) => boolean,
): unknown[] {
  if (!Array.isArray(rules) || rules.length === 0) return []
  const apply = (nodes: unknown[]): unknown[] =>
    nodes.map((node) => {
      if (!node || typeof node !== 'object') return node
      const rule = { ...(node as Record<string, unknown>) }
      withMappedChildren(rule, apply)
      if (rule.field != null) {
        const designerRequired = isFormCreateRuleRequired(rule)
        applyRequiredFlagToPreviewRule(
          rule,
          isFieldRequired(String(rule.field), designerRequired),
        )
      }
      return rule
    })
  return apply(rules)
}

/** Collect field keys from a form-create rule tree (getRuleChildren + `rule`). */
export function collectFieldKeysFromRules(rules: unknown[]): string[] {
  const keys: string[] = []
  const visited = new WeakSet<object>()
  const walk = (items: unknown[]): void => {
    if (!Array.isArray(items)) return
    for (const raw of items) {
      if (!raw || typeof raw !== 'object') continue
      if (visited.has(raw)) continue
      visited.add(raw)
      const rule = raw as Record<string, unknown>
      if (rule.field != null) keys.push(String(rule.field))
      walk(getRuleChildren(rule))
      if (Array.isArray(rule.rule)) walk(rule.rule)
    }
  }
  walk(rules)
  return keys
}

export interface PreviewOverlayApply {
  isDisabled: (fieldKey: string, designerDisabled: boolean) => boolean
  optionsFor: (fieldKey: string) => FormEventChoiceOption[] | undefined
  labelFor: (fieldKey: string) => string | undefined
}

/** Apply script disabled / options / label overlays onto form-create preview rules. */
export function applyPreviewOverlayToRules(
  rules: unknown[],
  overlay: PreviewOverlayApply,
): unknown[] {
  if (!Array.isArray(rules) || rules.length === 0) return []
  const apply = (nodes: unknown[]): unknown[] =>
    nodes.map((node) => {
      if (!node || typeof node !== 'object') return node
      const rule = { ...(node as Record<string, unknown>) }
      withMappedChildren(rule, apply)
      if (rule.field == null) return rule
      const fieldKey = String(rule.field)
      const props = {
        ...((rule.props && typeof rule.props === 'object'
          ? rule.props
          : {}) as Record<string, unknown>),
      }
      const designerDisabled = isFormCreateRuleReadonly(rule)
      if (!isTableAuditField(fieldKey)) {
        props.disabled = overlay.isDisabled(fieldKey, designerDisabled)
        rule.props = props
      }
      const options = overlay.optionsFor(fieldKey)
      if (options) rule.options = options
      const label = overlay.labelFor(fieldKey)
      if (label != null) rule.title = label
      return rule
    })
  return apply(rules)
}

