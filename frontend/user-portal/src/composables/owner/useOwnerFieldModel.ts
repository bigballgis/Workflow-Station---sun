import { type Ref } from 'vue'

export const OWNER_USER_PREFIX = 'user:'
export const OWNER_GROUP_PREFIX = 'group:'

export type OwnerSource = 'CREATOR' | 'CURRENT_ASSIGNEE'
export type OwnerChipKind = 'user' | 'group'

export type OwnerChipModel = {
  kind: OwnerChipKind
  label: string
}

/**
 * Parse `ownerConfig` (§4.1). Missing source (including leftover allowGroup-only
 * configs) is CREATOR. Invalid JSON sets configError.
 */
export function parseOwnerSource(
  ownerConfig: string | undefined,
  configError: Ref<boolean>,
): OwnerSource {
  try {
    const parsed = JSON.parse(ownerConfig || '{}') as { source?: unknown }
    configError.value = false
    return parsed?.source === 'CURRENT_ASSIGNEE' ? 'CURRENT_ASSIGNEE' : 'CREATOR'
  } catch {
    configError.value = true
    return 'CREATOR'
  }
}

/** Parses `user:<id>` or `user:<id1>,user:<id2>` into user ids. */
export function parseStoredUserIds(value: string): string[] {
  return value
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.startsWith(OWNER_USER_PREFIX) && part.length > OWNER_USER_PREFIX.length)
    .map((part) => part.slice(OWNER_USER_PREFIX.length).trim())
    .filter((id) => id.length > 0)
}

export function ownerChips(modelValue: string | null | undefined, display: string | undefined): OwnerChipModel[] {
  const value = (modelValue || '').trim()
  const label = (display || '').trim()
  if (value.startsWith(OWNER_GROUP_PREFIX)) {
    return [{ kind: 'group', label: label || value }]
  }
  const ids = parseStoredUserIds(value)
  if (ids.length > 0) {
    const labels = label
      ? label.split(',').map((part) => part.trim()).filter((part) => part.length > 0)
      : []
    return ids.map((id, index) => ({
      kind: 'user' as const,
      label: labels[index] || id,
    }))
  }
  if (!label) {
    return []
  }
  return label.split(',').map((part) => ({
    kind: 'user' as const,
    label: part.trim(),
  })).filter((chip) => chip.label.length > 0)
}
