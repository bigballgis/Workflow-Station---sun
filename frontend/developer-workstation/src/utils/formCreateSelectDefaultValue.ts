/**
 * Form Designer — Select Component "Default Value" props-panel rule.
 * Persists to rule.value (via formCreateValue) and mirrors props.value for Preview/Portal.
 */

export type SelectOptionItem = { label: string; value: string | number | boolean }

export function extractSelectOptionsFromRule(rule: Record<string, unknown> | null | undefined): SelectOptionItem[] {
  if (!rule || typeof rule !== 'object') return []
  const props = (rule.props && typeof rule.props === 'object')
    ? rule.props as Record<string, unknown>
    : {}
  const raw = rule.options ?? props.options
  if (!Array.isArray(raw)) return []
  const out: SelectOptionItem[] = []
  for (const item of raw) {
    if (item == null || typeof item !== 'object') continue
    const opt = item as Record<string, unknown>
    const value = opt.value ?? opt.key
    if (value === undefined || value === null || value === '') continue
    if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'boolean') continue
    const labelRaw = opt.label ?? opt.key ?? opt.text ?? value
    out.push({
      label: String(labelRaw),
      value,
    })
  }
  return out
}

export function syncSelectDefaultOntoRule(
  activeRule: Record<string, unknown> | null | undefined,
  value: unknown,
): void {
  if (!activeRule || typeof activeRule !== 'object') return
  const empty = value === undefined || value === null || value === ''
    || (Array.isArray(value) && value.length === 0)
  if (empty) {
    delete activeRule.value
    const props = (activeRule.props && typeof activeRule.props === 'object')
      ? { ...(activeRule.props as Record<string, unknown>) }
      : {}
    delete props.value
    activeRule.props = props
    return
  }
  activeRule.value = value
  const props = (activeRule.props && typeof activeRule.props === 'object')
    ? { ...(activeRule.props as Record<string, unknown>) }
    : {}
  props.value = value
  activeRule.props = props
}

/**
 * Props-panel rule appended for Select: choose default from configured Options.
 */
export function buildSelectDefaultValuePropRule(
  activeRule: Record<string, unknown> | null | undefined,
  titles: { title: string; placeholder: string },
): Record<string, unknown> {
  const options = extractSelectOptionsFromRule(activeRule)
  const props = (activeRule?.props && typeof activeRule.props === 'object')
    ? activeRule.props as Record<string, unknown>
    : {}
  const multiple = !!props.multiple
  const current = activeRule?.value !== undefined && activeRule?.value !== null
    ? activeRule.value
    : props.value

  return {
    type: 'select',
    field: 'formCreateValue',
    title: titles.title,
    value: current,
    options,
    props: {
      clearable: true,
      filterable: true,
      multiple,
      placeholder: titles.placeholder,
      disabled: options.length === 0,
    },
    inject: true,
    on: {
      change(inject: { api?: { activeRule?: Record<string, unknown> } }, value: unknown) {
        const target = inject?.api?.activeRule
        syncSelectDefaultOntoRule(target, value)
      },
    },
  }
}
