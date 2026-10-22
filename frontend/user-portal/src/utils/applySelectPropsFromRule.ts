import type { FormField } from '@/components/formRendererHelpers'

/** Carry the designer's Select cardinality into every Portal schema entry point. */
export function applySelectPropsFromRule(field: FormField, rule: Record<string, unknown>): void {
  if (rule.type !== 'select') return
  const props = rule.props as Record<string, unknown> | undefined
  field.multiple = props?.multiple === true
}
