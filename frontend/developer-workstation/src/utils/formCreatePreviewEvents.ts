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
