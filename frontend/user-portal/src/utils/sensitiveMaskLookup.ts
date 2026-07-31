/**
 * Build / apply field-name → sensitiveMask lookups for display surfaces
 * outside FieldRenderer (Change History, Snapshot Diff, lists, dialogs).
 */
import type { FormField } from '@/components/formRendererHelpers'
import { flattenLeafFormFields } from '@/components/formRendererHelpers'
import {
  applySensitiveMask,
  isSensitiveMaskActive,
  normalizeSensitiveMaskConfig,
  type SensitiveMaskConfig,
} from '@/utils/sensitiveMask'

export type SensitiveMaskLookup = Map<string, SensitiveMaskConfig>

export function emptySensitiveMaskLookup(): SensitiveMaskLookup {
  return new Map()
}

/** Register mask under field key (first enabled wins; later skips if already set). */
export function putSensitiveMask(
  lookup: SensitiveMaskLookup,
  fieldKey: string | null | undefined,
  rawConfig: unknown,
  inputType?: string | null,
): void {
  if (!fieldKey) return
  if (lookup.has(fieldKey)) return
  const cfg = normalizeSensitiveMaskConfig(rawConfig)
  if (!isSensitiveMaskActive(cfg, inputType)) return
  lookup.set(fieldKey, cfg!)
}

/** Collect from FormField trees (main form / tabs / after-tabs / previous node forms). */
export function collectMasksFromFormFields(
  lookup: SensitiveMaskLookup,
  fields: FormField[] | null | undefined,
): void {
  if (!fields?.length) return
  for (const field of flattenLeafFormFields(fields)) {
    if (field.type === 'password' || field.type === 'textarea') continue
    putSensitiveMask(lookup, field.key, field.sensitiveMask)
  }
}

/** Collect from sub-table / dialog columns that carry props.sensitiveMask. */
export function collectMasksFromColumns(
  lookup: SensitiveMaskLookup,
  columns: Array<{ field?: string; type?: string; props?: Record<string, unknown> }> | null | undefined,
): void {
  if (!columns?.length) return
  for (const col of columns) {
    if (col.type === 'password' || col.type === 'textarea') continue
    const inputType = typeof col.props?.type === 'string' ? col.props.type : undefined
    putSensitiveMask(lookup, col.field, col.props?.sensitiveMask, inputType)
  }
}

/**
 * Walk form-create rules (configJson.rule / subForms.*.rule) and collect
 * Input sensitiveMask — covers fields not currently in the active FormField list.
 */
export function collectMasksFromFormCreateRules(
  lookup: SensitiveMaskLookup,
  rules: unknown,
): void {
  if (!Array.isArray(rules)) return
  const stack = [...rules] as Array<Record<string, unknown>>
  while (stack.length) {
    const rule = stack.pop()
    if (!rule || typeof rule !== 'object') continue
    const children = rule.children
    if (Array.isArray(children)) {
      for (const c of children) {
        if (c && typeof c === 'object') stack.push(c as Record<string, unknown>)
      }
    }
    const props = (rule.props && typeof rule.props === 'object')
      ? rule.props as Record<string, unknown>
      : undefined
    if (rule.type === 'input' && typeof rule.field === 'string') {
      const inputType = typeof props?.type === 'string' ? props.type : undefined
      putSensitiveMask(lookup, rule.field, props?.sensitiveMask, inputType)
    }
  }
}

/** Merge masks from a parsed form configJson (rule + subForms). */
export function collectMasksFromFormConfigJson(
  lookup: SensitiveMaskLookup,
  configJson: unknown,
): void {
  if (!configJson || typeof configJson !== 'object') return
  const cfg = configJson as Record<string, unknown>
  collectMasksFromFormCreateRules(lookup, cfg.rule)
  const subForms = cfg.subForms
  if (subForms && typeof subForms === 'object') {
    for (const sub of Object.values(subForms as Record<string, unknown>)) {
      if (sub && typeof sub === 'object') {
        collectMasksFromFormCreateRules(
          lookup,
          (sub as Record<string, unknown>).rule,
        )
      }
    }
  }
}

export function getSensitiveMask(
  lookup: SensitiveMaskLookup | null | undefined,
  fieldName: string | null | undefined,
): SensitiveMaskConfig | null {
  if (!lookup || !fieldName) return null
  return lookup.get(fieldName) ?? null
}

/** Mask a scalar display string when the field has an active config. */
export function maskScalarIfConfigured(
  raw: string,
  fieldName: string | null | undefined,
  lookup: SensitiveMaskLookup | null | undefined,
): string {
  const cfg = getSensitiveMask(lookup, fieldName)
  if (!isSensitiveMaskActive(cfg)) return raw
  return applySensitiveMask(raw, cfg!)
}

/**
 * Build a lookup from the common portal detail sources.
 */
export function buildSensitiveMaskLookup(sources: {
  formFields?: FormField[] | null
  formTabs?: Array<{ fields?: FormField[] }> | null
  formFieldsAfterTabs?: FormField[] | null
  extraFieldLists?: Array<FormField[] | null | undefined>
  subTableBindings?: Array<{ columns?: Array<{ field?: string; type?: string; props?: Record<string, unknown> }> | null }> | null
  formConfigJsons?: unknown[]
}): SensitiveMaskLookup {
  const lookup = emptySensitiveMaskLookup()
  collectMasksFromFormFields(lookup, sources.formFields)
  collectMasksFromFormFields(lookup, sources.formFieldsAfterTabs)
  if (sources.formTabs) {
    for (const tab of sources.formTabs) {
      collectMasksFromFormFields(lookup, tab.fields)
    }
  }
  if (sources.extraFieldLists) {
    for (const list of sources.extraFieldLists) {
      collectMasksFromFormFields(lookup, list)
    }
  }
  if (sources.subTableBindings) {
    for (const b of sources.subTableBindings) {
      collectMasksFromColumns(lookup, b.columns ?? undefined)
    }
  }
  if (sources.formConfigJsons) {
    for (const cfg of sources.formConfigJsons) {
      collectMasksFromFormConfigJson(lookup, cfg)
    }
  }
  return lookup
}
