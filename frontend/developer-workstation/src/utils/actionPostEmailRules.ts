import type { FilterConfig } from '@/utils/mainTableViewFilter'
import { nextFilterNodeId } from '@/utils/mainTableViewFilter'
import type { ActionPostEmailModel } from '@/components/designer/action-designer/ActionPostEmailConfig.vue'

export const MAX_POST_EMAIL_RULES = 20

export interface ActionPostEmailRule extends ActionPostEmailModel {
  id: string
  name?: string
  enabled?: boolean
  condition?: FilterConfig | null
}

export function createEmptyPostEmailRule(): ActionPostEmailRule {
  return {
    id: nextFilterNodeId('per'),
    name: '',
    enabled: true,
    connectionId: '',
    emailTo: '',
    emailCc: '',
    emailBcc: '',
    emailFrom: '',
    emailTemplateId: '',
    emailAttachments: '',
    condition: null,
  }
}

function legacyPostEmailToRule(legacy: ActionPostEmailModel): ActionPostEmailRule {
  const condition =
    legacy && typeof legacy === 'object' && 'condition' in legacy
      ? (legacy as ActionPostEmailRule).condition ?? null
      : null
  return {
    ...createEmptyPostEmailRule(),
    ...legacy,
    id: 'legacy',
    condition,
  }
}

function legacyPostEmailHasContent(legacy: ActionPostEmailModel): boolean {
  return Boolean(
    legacy.enabled
      || legacy.connectionId?.trim()
      || legacy.emailTo?.trim()
      || legacy.emailTemplateId?.trim(),
  )
}

/** Normalize persisted config: legacy postEmail → postEmailRules, strip postEmail on save elsewhere. */
export function normalizePostEmailRules(config: Record<string, unknown>): ActionPostEmailRule[] {
  const raw = config.postEmailRules
  if (Array.isArray(raw) && raw.length > 0) {
    return raw.map(item => ({ ...createEmptyPostEmailRule(), ...(item as object) })) as ActionPostEmailRule[]
  }
  const legacy = config.postEmail as ActionPostEmailModel | undefined
  if (legacy && typeof legacy === 'object' && legacyPostEmailHasContent(legacy)) {
    return [legacyPostEmailToRule(legacy)]
  }
  return []
}

export function stripLegacyPostEmail(config: Record<string, unknown>): void {
  if ('postEmail' in config) {
    delete config.postEmail
  }
}

export function filterConfigHasConditions(condition: FilterConfig | null | undefined): boolean {
  if (!condition) return false
  const rows = condition.conditions?.length ?? 0
  const groups = condition.groups?.length ?? 0
  return rows + groups > 0
}
