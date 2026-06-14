import type { DialogColumn, TreeNode } from './types'
import { unwrapUserLikeValueToDisplayString } from './userDisplay'
import { getLookupPrimaryKeyFieldFromProps, resolveLookupCellTagText } from './lookup'

/**
 * Resolves a raw stored value to a human-readable display string for table cells.
 * - radio/select: maps value → option label
 * - checkbox: maps array of values → comma-separated labels
 * - password: returns masked string '••••••'
 * - timerange: formats [start, end] array as "start - end"
 * - others: converts to string
 */
export function resolveDisplayValue(col: DialogColumn, rawValue: unknown): string {
  if (rawValue === null || rawValue === undefined) return '-'

  const options = col.props?.options ?? col.options

  if (col.type === 'password') {
    return '••••••'
  }

  if (col.type === 'radio' || col.type === 'select') {
    if (!options) return unwrapUserLikeValueToDisplayString(rawValue)
    const opt = options.find((o) => o.value === rawValue)
    return opt ? opt.label : unwrapUserLikeValueToDisplayString(rawValue)
  }

  if (col.type === 'checkbox') {
    if (!Array.isArray(rawValue) || !options) return unwrapUserLikeValueToDisplayString(rawValue)
    return rawValue
      .map((v: unknown) => options.find((o) => o.value === v)?.label ?? v)
      .join(', ')
  }

  if (col.type === 'timerange') {
    if (Array.isArray(rawValue) && rawValue.length === 2) {
      return `${rawValue[0]} - ${rawValue[1]}`
    }
    return String(rawValue)
  }

  if (col.type === 'treeselect') {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }

  if (col.type === 'rate') {
    return `${rawValue} ★`
  }

  if (col.type === 'slider') {
    return String(rawValue)
  }

  if (col.type === 'colorPicker') {
    return String(rawValue)
  }

  if (col.type === 'editor') {
    // Strip HTML tags for table display
    return String(rawValue).replace(/<[^>]*>/g, '').substring(0, 100) || '-'
  }

  if (col.type === 'signature') {
    return rawValue ? '[Signature]' : '-'
  }

  if (col.type === 'transfer') {
    if (Array.isArray(rawValue)) return rawValue.join(', ')
    return String(rawValue)
  }

  if (col.type === 'cascader') {
    if (Array.isArray(rawValue)) return rawValue.join(' / ')
    return String(rawValue)
  }

  if (col.type === 'lookup') {
    if (typeof rawValue === 'object' && rawValue != null && !Array.isArray(rawValue)) {
      return resolveLookupCellTagText(col.props ?? null, rawValue as Record<string, unknown>)
    }
    if (typeof rawValue === 'string' || typeof rawValue === 'number') {
      const pk = getLookupPrimaryKeyFieldFromProps(col.props ?? null)
      const synthetic = { [pk]: rawValue } as Record<string, unknown>
      const label = resolveLookupCellTagText(col.props ?? null, synthetic)
      return label !== '-' ? label : String(rawValue)
    }
    return '-'
  }

  if (col.type === 'tree') {
    if (!Array.isArray(rawValue)) return String(rawValue)
    const treeData = col.props?.treeData || []
    const nodeKey = col.props?.nodeKey || 'id'
    const labelKey = col.props?.labelProps?.label || 'label'
    const childrenKey = col.props?.labelProps?.children || 'children'
    // Flatten tree to build id→label map
    const labelMap = new Map<string | number, string>()
    const walk = (nodes: TreeNode[]) => {
      for (const n of nodes) {
        const key = n[nodeKey]
        const label = n[labelKey]
        if (key != null && typeof label === 'string') {
          labelMap.set(key as string | number, label)
        }
        const children = n[childrenKey]
        if (Array.isArray(children)) walk(children as TreeNode[])
      }
    }
    walk(treeData)
    return rawValue.map((v: unknown) => labelMap.get(v as string | number) ?? v).join(', ')
  }

  if (col.type === 'user' || col.type === 'department') {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }

  if (typeof rawValue === 'object' && !Array.isArray(rawValue)) {
    return unwrapUserLikeValueToDisplayString(rawValue)
  }

  return String(rawValue)
}
