import type { PortalFormApi } from './api'

function isFormApi(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<PortalFormApi>
  return typeof candidate.getValue === 'function' && typeof candidate.setValue === 'function'
}

/** Native hook arguments must never provide a second, unguarded API. */
export function bindFormEventArgs(args: unknown, api: PortalFormApi, rule: Record<string, unknown>): unknown {
  if (!Array.isArray(args)) return args
  return args.map(arg => {
    if (isFormApi(arg)) return api
    if (!arg || typeof arg !== 'object' || !isFormApi(arg.api)) return arg
    return { ...arg, api, ...('rule' in arg ? { rule } : {}), ...('self' in arg ? { self: rule } : {}) }
  })
}
