/** Designer flags and defaults shared by ordinary forms and Batch workspaces. */
export function isFormFieldRequired(rule: Record<string, unknown>): boolean {
  const flag = (value: unknown) => value === true || (typeof value === 'string' && value.trim().length > 0)
  return flag(rule.$required) || flag(rule.required) || (Array.isArray(rule.validate) && rule.validate.some(entry =>
    entry && typeof entry === 'object' && (entry.required === true || entry.mode === 'required')))
}

export function formFieldMaxLength(props: Record<string, unknown> | undefined): number | undefined {
  const raw = props?.maxlength ?? props?.maxLength
  const length = typeof raw === 'number' || typeof raw === 'string' ? Number(raw) : NaN
  return Number.isFinite(length) && length > 0 ? length : undefined
}

export function configuredSwitchValue(raw: unknown, props: Record<string, unknown> | undefined): unknown {
  if (props && ('activeValue' in props || 'inactiveValue' in props)) {
    const values = [props.activeValue ?? true, props.inactiveValue ?? false]
    const matched = values.find(value => String(value) === String(raw))
    return matched === undefined ? raw : matched
  }
  if (raw === 'true' || raw === 1 || raw === '1') return true
  if (raw === 'false' || raw === 0 || raw === '0') return false
  return raw
}

export function formDateValueFormat(kind: string): string {
  return kind === 'time' || kind === 'timerange' ? 'HH:mm:ss'
    : kind === 'datetime' || kind === 'datetimerange' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'
}
