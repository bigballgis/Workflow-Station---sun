import {
  EMAIL_VAR_GROUP_LOOKUP,
  EMAIL_VAR_GROUP_SUBTABLES,
  type EmailVariableGroup,
  type EmailVariableOption,
} from '@/composables/email/useEmailTemplateVariables'

/** Sentinel group label for built-in process variables (mapped to i18n in the UI). */
export const SEND_TASK_PROCESS_VAR_GROUP = '__PROCESS__'

/** Matches simple JUEL placeholders resolved by BpmnExtensionUtils.resolveExpression. */
const SIMPLE_JUEL_TOKEN = /^\$\{[A-Za-z_][A-Za-z0-9_]*\}$/

export const SEND_TASK_INITIATOR_LABEL = '__INITIATOR__'

const PROCESS_VARIABLE_OPTIONS: EmailVariableOption[] = [
  { token: '${initiator}', label: SEND_TASK_INITIATOR_LABEL },
]

/**
 * Send Task To/Cc/Bcc/From only support top-level process variables (${fieldName}).
 * Template-only tokens (subTableField, subTableHtml, lookupField) are excluded.
 */
export function filterGroupsForSendTaskRecipient(
  groups: EmailVariableGroup[],
): EmailVariableGroup[] {
  const mainGroups = (groups ?? [])
    .filter(g => g?.label && g.label !== EMAIL_VAR_GROUP_SUBTABLES)
    .filter(g => !g.label.startsWith(`${EMAIL_VAR_GROUP_LOOKUP}:`))
    .map(g => ({
      label: g.label,
      options: (g.options ?? []).filter(o => SIMPLE_JUEL_TOKEN.test(o.token)),
    }))
    .filter(g => g.options.length > 0)

  return [
    {
      label: SEND_TASK_PROCESS_VAR_GROUP,
      options: PROCESS_VARIABLE_OPTIONS.map(o => ({ ...o })),
    },
    ...mainGroups,
  ]
}

/** Append a JUEL token, separated with "; " so the engine split("[;,]") sees two recipients. */
export function appendJuelTokenToExpression(current: string, token: string): string {
  const trimmedToken = token.trim()
  if (!trimmedToken) return current
  const base = current ?? ''
  const trimmed = base.trimEnd()
  if (!trimmed) return trimmedToken
  if (trimmed.endsWith(',') || trimmed.endsWith(';')) {
    return `${trimmed} ${trimmedToken}`
  }
  return `${trimmed}; ${trimmedToken}`
}

export function resolveSendTaskVariableGroupLabel(
  label: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (label === SEND_TASK_PROCESS_VAR_GROUP) {
    return t('properties.sendTaskProcessVariableGroup')
  }
  return label
}

export function resolveSendTaskVariableOptionLabel(
  label: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (label === SEND_TASK_INITIATOR_LABEL) {
    return t('properties.sendTaskInitiator')
  }
  return label
}
