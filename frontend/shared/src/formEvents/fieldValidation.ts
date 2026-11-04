import Schema, { type RuleItem, type InternalRuleItem } from 'async-validator'

/** Declarative rules use the same built-in validators as Element Plus / ordinary forms. */
export const fieldValidatorKeys = ['required', 'type', 'min', 'max', 'len', 'message', 'trigger', 'pattern', 'enum', 'whitespace', 'mode', 'maxLen', 'minLen']
export const fieldValidatorTypes = ['string', 'number', 'integer', 'float', 'boolean', 'date', 'array', 'object', 'email', 'url', 'hex', 'enum']
export const fieldValidatorModes = ['required', 'email', 'url', 'integer', 'number', 'len', 'maxLen', 'minLen', 'min', 'max', 'pattern', 'enum']

export function normalizeFieldValidator(entry: Record<string, unknown>): RuleItem {
  const { mode, maxLen, minLen, ...rest } = entry
  if (mode === 'required') return { ...rest, required: true } as RuleItem
  if (mode === 'min' || mode === 'max') return { ...rest, type: 'number' } as RuleItem
  if (mode === 'maxLen' || mode === 'minLen') return { ...rest, type: 'string', [mode === 'maxLen' ? 'max' : 'min']: mode === 'maxLen' ? maxLen : minLen } as RuleItem
  if (['email', 'url', 'integer', 'number', 'enum'].includes(String(mode))) return { ...rest, type: mode } as RuleItem
  return rest as RuleItem
}

/** Only synchronous built-ins are accepted here; executable validators retain their event contract. */
export function fieldValidatorError(value: unknown, entry: Record<string, unknown>, label: string): string | null {
  const normalized = normalizeFieldValidator(entry)
  if (['min', 'max', 'integer', 'number'].includes(String(entry.mode))) value = Number(value)
  if (normalized.required && !normalized.type && !normalized.pattern && !normalized.enum) {
    normalized.type = Array.isArray(value) ? 'array' : typeof value === 'boolean' ? 'boolean' : typeof value === 'number' ? 'number' : 'string'
  }
  const schema = new Schema({ value: normalized })
  const rule: InternalRuleItem = { ...normalized, field: 'value', fullField: label, type: schema.getType(normalized) }
  const validator = schema.getValidationMethod(rule)
  let error: string | null = null
  validator(rule, value, (messages?: string | Error | string[]) => {
    if (messages && (!Array.isArray(messages) || messages.length)) {
      error = typeof entry.message === 'string' ? entry.message : String(Array.isArray(messages) ? messages[0] : messages)
    }
  }, { value }, { messages: Schema.messages })
  return error
}
