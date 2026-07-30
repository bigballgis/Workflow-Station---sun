import { isFormCreateRuleReadonly } from '../formRendererHelpers'
import {
  isFormCreateRuleRequired,
  mapFormCreateValidateToElementPlusRules,
} from '@/utils/formCreateValidateRules'
import { resolveRuleDefaultValue } from '@/utils/formCreateRuleDefaults'
import {
  buildLookupColumnProps,
  enrichLookupColumnPropsFromSubFormRule,
  parseLookupConfig,
} from './lookup'
import type { DialogColumn } from './types'

export type SubFormColumnLookupContext = {
  lookupDbConfigs: Record<string, { tableId: number; searchFields: string[]; displayField: string; viewFields: unknown[] }>
  relationViewConfigs: Record<string, { viewFields: unknown[]; allFields: unknown[] }>
}

/** Resolve designer sub-form rule for a binding (supports subFormConfig + configJson.subForms). */
export function resolveSubFormRuleForBinding(
  binding: { bindingId?: number | string; subFormConfig?: { rule?: unknown[] } },
  subForms?: Record<string, { rule?: unknown[] }>,
): unknown[] | undefined {
  const fromBinding = binding.subFormConfig?.rule
  if (Array.isArray(fromBinding) && fromBinding.length > 0) return fromBinding
  if (binding.bindingId == null || !subForms) return undefined
  const bid = binding.bindingId
  const entry = subForms[bid] ?? subForms[String(bid)]
  return Array.isArray(entry?.rule) && entry.rule.length > 0 ? entry.rule : undefined
}

/** Layout containers whose children are real field rules (same set the DW preview inlines). */
const LAYOUT_CONTAINER_TYPES = new Set([
  'el-card', 'elCard', 'card',
  'el-row', 'elRow', 'row',
  'el-col', 'elCol', 'col',
  'group', 'subForm', 'tableForm', 'tableFormColumn',
])

function getLayoutRuleChildren(item: Record<string, unknown>): unknown[] {
  const props = (item.props ?? {}) as Record<string, unknown>
  const sources = [item.children, props.children, props.list, props.items, props.fields]
  return (sources.find(Array.isArray) as unknown[] | undefined) ?? []
}

/**
 * Expand layout containers (Card/Row/Col/…) into their children so field rules nested
 * inside them still participate in column derivation. Field-bearing rules and
 * placeholders (`subTable`, `linkForm`) pass through untouched, in document order.
 */
export function flattenSubFormRuleLayoutContainers(rules: unknown[] | undefined | null): unknown[] {
  if (!Array.isArray(rules)) return []
  const out: unknown[] = []
  const walk = (items: unknown[]) => {
    for (const item of items) {
      const r = item as Record<string, unknown> | null
      if (r && typeof r === 'object' && !r.field && LAYOUT_CONTAINER_TYPES.has(String(r.type))) {
        walk(getLayoutRuleChildren(r))
      } else {
        out.push(item)
      }
    }
  }
  walk(rules)
  return out
}

/**
 * Only field-bearing input rules become dialog columns. Placeholders (`subTable` nested
 * tables, `linkForm` widgets) and layout containers have no editable `field` and cannot
 * live in the column-driven Add/Edit dialog — mapping them produced a bogus text input
 * labeled "Sub-Table". Nested sub-tables render via form-below-table instead.
 */
export function isDialogMappableSubFormRule(rawRule: unknown): boolean {
  const r = rawRule as Record<string, unknown> | null
  if (!r || typeof r !== 'object') return false
  if (r.type === 'subTable' || r.type === 'linkForm') return false
  return typeof r.field === 'string' && r.field.length > 0
}

/** Map form-design canvas rule items to Add/Edit dialog columns (excludes list-view-only fields). */
export function mapSubFormRuleToDialogColumns(
  subFormRule: unknown[],
  ctx: SubFormColumnLookupContext,
): DialogColumn[] {
  return flattenSubFormRuleLayoutContainers(subFormRule).filter(isDialogMappableSubFormRule).map((rawRule): DialogColumn => {
    const r = rawRule as Record<string, unknown>
    const rProps = (r.props ?? {}) as Record<string, unknown>
    let type: string | undefined

    if (r.type === 'input') {
      if (rProps.type === 'textarea') type = 'textarea'
      else if (rProps.type === 'password') type = 'password'
      else type = 'text'
    } else if (r.type === 'inputNumber') {
      type = 'number'
    } else if (r.type === 'select') {
      type = 'select'
    } else if (r.type === 'radio') {
      type = 'radio'
    } else if (r.type === 'switch') {
      type = 'switch'
    } else if (r.type === 'datePicker') {
      type = rProps.type === 'datetime' ? 'datetime' : 'date'
    } else if (r.type === 'timePicker') {
      type = rProps.isRange === true ? 'timerange' : 'time'
    } else if (r.type === 'treeSelect' || r.type === 'elTreeSelect') {
      type = 'treeselect'
    } else if (r.type === 'tree') {
      type = 'tree'
    } else if (r.type === 'upload') {
      type = 'upload'
    } else if (r.type === 'userSelect' || r.type === 'user') {
      type = 'user'
    } else if (r.type === 'departmentSelect' || r.type === 'department') {
      type = 'department'
    } else if (r.type === 'colorPicker') {
      type = 'colorPicker'
    } else if (r.type === 'rate') {
      type = 'rate'
    } else if (r.type === 'slider') {
      type = 'slider'
    } else if (r.type === 'editor') {
      type = 'editor'
    } else if (r.type === 'signature') {
      type = 'signature'
    } else if (r.type === 'transfer') {
      type = 'transfer'
    } else if (r.type === 'cascader') {
      type = 'cascader'
    } else if (r.type === 'lookup') {
      type = 'lookup'
    } else {
      type = r.type as string
    }

    const rawOptions = r.options ?? rProps.options
    const options = rawOptions
      ? (type === 'cascader'
        ? rawOptions
        : (rawOptions as Array<{ label?: unknown; value?: unknown }>).map(o => ({
          label: o.label ?? o.value,
          value: o.value,
        })))
      : undefined

    const passProps: Record<string, unknown> = {}
    const propKeys = [
      'action', 'accept', 'multiple', 'precision', 'min', 'max', 'rows', 'maxlength', 'fileNameTargetField',
      'isRange', 'valueFormat', 'startPlaceholder', 'endPlaceholder', 'treeData', 'checkStrictly',
      'showAlpha', 'allowHalf', 'step', 'cascaderProps', 'leftTitle', 'rightTitle',
      'boundSubTableBindingId',
    ]
    for (const key of propKeys) {
      if (rProps[key] !== undefined) passProps[key] = rProps[key]
    }
    if (rProps.data !== undefined) passProps.treeData = rProps.data
    if (rProps.nodeKey !== undefined) passProps.nodeKey = rProps.nodeKey
    if (rProps.showCheckbox !== undefined) passProps.showCheckbox = rProps.showCheckbox
    if (rProps.props !== undefined) passProps.labelProps = rProps.props
    if (type === 'cascader' && rProps.props && !passProps.cascaderProps) passProps.cascaderProps = rProps.props

    const field = String(r.field ?? '')
    if (type === 'lookup') {
      const dbCfg = ctx.lookupDbConfigs[field]
      const lookupCfg = parseLookupConfig(String(rProps.lookupConfig ?? '{}'))
      const relationView = lookupCfg.bindingId ? ctx.relationViewConfigs[lookupCfg.bindingId] : undefined
      Object.assign(
        passProps,
        buildLookupColumnProps(String(rProps.lookupConfig ?? '{}'), {
          dbCfg,
          relationViewFields: relationView?.viewFields as Array<Record<string, unknown>> | undefined,
        }),
      )
      if (typeof rProps.selectedDisplayField === 'string' && rProps.selectedDisplayField.trim() !== '') {
        passProps.selectedDisplayField = rProps.selectedDisplayField.trim()
        passProps._lookupSelectedDisplayField = rProps.selectedDisplayField.trim()
      }
    }

    if (options) passProps.options = options

    const required = isFormCreateRuleRequired(r)
    const readonly = isFormCreateRuleReadonly(r)
    const elRules = mapFormCreateValidateToElementPlusRules(r, type)
    const defaultValue = resolveRuleDefaultValue(r)

    return {
      field,
      label: String(r.title ?? r.field ?? field),
      type: type as DialogColumn['type'],
      required,
      ...(readonly ? { readonly } : {}),
      ...(elRules.length > 0 ? { rules: elRules } : {}),
      ...(options ? { options: options as DialogColumn['options'] } : {}),
      ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
      ...(defaultValue !== undefined ? { defaultValue } : {}),
      sourceRule: r,
    }
  })
}

/**
 * Designer form-design canvas columns for Add/Edit dialog.
 * List view may include audit / linkForm columns that must not appear in the dialog.
 */
export function resolveSubFormDialogColumnsForBinding(
  binding: { bindingId?: number | string; subFormConfig?: { rule?: unknown[] } },
  subForms: Record<string, { rule?: unknown[] }> | undefined,
  ctx: SubFormColumnLookupContext,
): DialogColumn[] {
  const subFormRule = flattenSubFormRuleLayoutContainers(resolveSubFormRuleForBinding(binding, subForms))
  if (!subFormRule.length) return []
  return enrichLookupColumnPropsFromSubFormRule(
    mapSubFormRuleToDialogColumns(subFormRule, ctx),
    subFormRule,
  )
}
