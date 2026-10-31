import { batchRuleChildren } from './batchFormRules'
import { supportedBatchFieldTypes } from './fields'

export function isEmptyBatchEvent(value: unknown): boolean {
  if (value == null) return true
  if (typeof value !== 'string') return false
  const source = value.trim()
  return source === '' || source === '$FNX:' || /^\[\[FORM-CREATE-PREFIX-function [^(){}]*\([^(){}]*\)\s*\{\s*}-FORM-CREATE-SUFFIX\]\]$/.test(source)
    || source === '$FNX:\nfor(var s=a.getInjectData(n,r),u=arguments.length,c=new Array(u),d=0;d<u;d++)c[d]=arguments[d];return s.args=[].concat(c),c.unshift(s),o.apply(this,c)'
}

export function batchFormOptionsIssue(options: unknown): string | null {
  if (!options || typeof options !== 'object') return null
  return ['onChange', 'onCreated', 'onMounted', 'onReload', 'beforeSubmit', 'onSubmit', 'onReset', 'beforeFetch']
    .find(key => !isEmptyBatchEvent((options as Record<string, unknown>)[key])) ?? null
}

/** Returns the precise field/event for the design save error. */
export function batchControlIssue(rules: Array<Record<string, unknown>>): string | null {
  for (const rule of rules) {
    const field = typeof rule.field === 'string' ? rule.field : String(rule.type)
    if (rule.field && rule.type !== 'miAssignment' && !supportedBatchFieldTypes.has(String(rule.type))) return `${field} (${rule.type})`
    if (Array.isArray(rule.validate) && rule.validate.some(validator => !validator || typeof validator !== 'object'
      || Object.keys(validator).some(key => !['required', 'type', 'min', 'max', 'len', 'message', 'trigger'].includes(key)))) return `${field} (validate)`
    for (const kind of ['on', '_on', 'hook', '_hook']) {
      const events = rule[kind]
      if (events == null) continue
      if (typeof events !== 'object' || Array.isArray(events)) return `${field} (${kind})`
      for (const [event, script] of Object.entries(events)) {
        if (isEmptyBatchEvent(script)) continue
        if (!['on', '_on'].includes(kind) || event !== 'change' || typeof script !== 'string'
          || !(script.startsWith('$FNX:') || script.startsWith('[[FORM-CREATE-PREFIX-'))) return `${field} (${event})`
      }
    }
    const child = batchControlIssue(batchRuleChildren(rule))
    if (child) return child
  }
  return null
}
