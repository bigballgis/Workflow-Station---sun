/**
 * FormField tree utilities — readonly/hidden flags, leaf flattening, and the
 * designer-hide visibility seeding consumed by FormRenderer init/rules.
 */

import type { FormField, FormTab } from './formRendererTypes'

/** Coerce designer native binding id lists (number[]) into Set for `.has()` lookups. */
export function asNumberSet(
  src: Set<number> | ReadonlySet<number> | Iterable<number> | null | undefined,
): Set<number> {
  if (src == null) return new Set()
  if (src instanceof Set) return new Set(src)
  return new Set([...src].map(Number).filter(Number.isFinite))
}

/** True when a form-create rule marks the field read-only (designer Props → readonly or disabled). */
export function isFormCreateRuleReadonly(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return (
    r.disabled === true ||
    r.readonly === true ||
    props.disabled === true ||
    props.readonly === true
  )
}

/** Designer "Hide" toggle — form-create `rule.hidden` / `_hidden` / `display: false`. */
export function isFormCreateRuleHidden(rule: unknown): boolean {
  if (!rule || typeof rule !== 'object') return false
  const r = rule as Record<string, unknown>
  const props = (r.props as Record<string, unknown> | undefined) || {}
  return (
    r.hidden === true ||
    r._hidden === true ||
    r.display === false ||
    r._display === false ||
    props.hidden === true ||
    props.hide === true
  )
}

/** Copy designer Hide flag onto parsed {@link FormField} (runtime visibility via event script). */
export function applyDesignerHideFlagToFormField(field: FormField, rule: unknown): void {
  if (isFormCreateRuleHidden(rule)) {
    field.hidden = true
  }
}

/** Seed script visibility map from designer-hidden fields (overridable via `options.hidden(false, …)`). */
export function seedDesignerHiddenFieldVisibility(
  fields: FormField[] | undefined,
  tabs: FormTab[] | undefined,
  fieldsAfterTabs: FormField[] | undefined,
  state: { hidden: Map<string, boolean>; display: Map<string, boolean> },
): void {
  for (const field of flattenAllFormFieldSegments(fields, tabs, fieldsAfterTabs)) {
    if (field.hidden === true && field.key) {
      state.hidden.set(String(field.key), true)
    }
  }
}

export function isFormFieldReadonly(field: FormField, formReadonly = false): boolean {
  return formReadonly || field.readonly === true
}

const LAYOUT_ONLY_FIELD_KEY_PREFIXES = ['__subTable_', '__layout_']

function isDataBoundFormFieldKey(key: string): boolean {
  if (!key || key.startsWith('__')) return false
  return !LAYOUT_ONLY_FIELD_KEY_PREFIXES.some(p => key.startsWith(p))
}

const DISPLAY_ONLY_LAYOUT_TYPES = new Set<string>([
  'title',
  'staticText',
  'html',
  'divider',
  'alert',
  'tag',
  'button',
  'space',
  'image',
])

export function isDisplayOnlyLayoutField(field: FormField): boolean {
  return DISPLAY_ONLY_LAYOUT_TYPES.has(field.type)
}

/**
 * Collect data-bound field keys from parsed {@link FormField} trees, recursing into
 * {@code elCard} children (and tab panes). Used by MI isolation and My Request hydration
 * so scalars inside Case Info cards (e.g. {@code case_number}, {@code legal_hold}) are not dropped.
 */
export function collectLeafFormFieldKeys(
  fields: FormField[],
  tabs?: FormTab[],
): string[] {
  const keys = new Set<string>()
  const walk = (arr?: FormField[]) => {
    if (!Array.isArray(arr)) return
    for (const f of arr) {
      if (f.type === 'tabs' && Array.isArray(f.tabs)) {
        for (const tab of f.tabs) walk(tab.fields)
        continue
      }
      if (f.type === 'collapse' && Array.isArray(f.collapsePanels)) {
        for (const panel of f.collapsePanels) walk(panel.fields)
        continue
      }
      if ((f.type === 'card' || f.type === 'row' || f.type === 'col') && Array.isArray(f.children)) {
        walk(f.children)
        continue
      }
      if (isDisplayOnlyLayoutField(f)) continue
      if (f.type === 'subTable') continue
      const key = f.key != null ? String(f.key) : ''
      if (isDataBoundFormFieldKey(key)) keys.add(key)
    }
  }
  walk(fields)
  for (const tab of tabs || []) {
    walk(tab.fields)
  }
  return Array.from(keys)
}

/** Flatten parsed layout trees into leaf {@link FormField} entries for init/rules. */
export function flattenLeafFormFields(items?: FormField[]): FormField[] {
  if (!Array.isArray(items) || items.length === 0) return []
  const flatten = (arr: FormField[]): FormField[] =>
    arr.flatMap(field => {
      if (field.type === 'tabs' && Array.isArray(field.tabs)) {
        return field.tabs.flatMap(tab => flatten(tab.fields))
      }
      if (field.type === 'collapse' && Array.isArray(field.collapsePanels)) {
        return field.collapsePanels.flatMap(panel => flatten(panel.fields))
      }
      if (
        (field.type === 'card' || field.type === 'row' || field.type === 'col')
        && Array.isArray(field.children)
      ) {
        return flatten(field.children)
      }
      if (field.children?.length) return flatten(field.children)
      return [field]
    })
  return flatten(items)
}

/** All data-bound leaf fields across before-tabs, tab panes, and after-tabs segments. */
export function flattenAllFormFieldSegments(
  fields?: FormField[],
  tabs?: FormTab[],
  fieldsAfterTabs?: FormField[],
): FormField[] {
  const merged: FormField[] = []
  merged.push(...flattenLeafFormFields(fields))
  for (const tab of tabs || []) {
    merged.push(...flattenLeafFormFields(tab.fields))
  }
  merged.push(...flattenLeafFormFields(fieldsAfterTabs))
  return merged
}
