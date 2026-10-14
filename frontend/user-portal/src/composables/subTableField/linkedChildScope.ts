import { resolveMiBindingKindFromConfig } from '@/composables/tasks/miBindingKindFromConfig'
import { miChildFkConfigOfBinding, resolveMiChildStructuralFkColumns } from '@/composables/tasks/miLinkChildIdentity'
import { normalizeMiLinkMatchId } from '@/composables/tasks/internal'
import type { SubTableBinding, SubTableFieldProps } from './subTableFieldTypes'

type Host = Pick<SubTableFieldProps, 'tableId' | 'primaryTableId'>

export function isConfiguredParticipantChild(binding: SubTableBinding | undefined, host: Host): boolean {
  return !!binding && resolveMiBindingKindFromConfig(binding, {
    miCollectionTableId: host.tableId ?? null,
    primaryTableId: host.primaryTableId ?? null,
  }) === 'participant-child'
}

/** null means this is not a participant-child binding; false is a conclusive ownership rejection. */
export function matchConfiguredParticipantChildRow(row: Record<string, unknown>, binding: SubTableBinding | undefined,
                                                  host: Host, participantKey: string | number | null): boolean | null {
  if (!isConfiguredParticipantChild(binding, host) || !binding) return null
  const parentId = normalizeMiLinkMatchId(participantKey)
  if (!parentId) return false
  const columns = resolveMiChildStructuralFkColumns(miChildFkConfigOfBinding(binding, host.tableId))
  const declaredFilter = binding.filterFkFieldName
  if (declaredFilter) return columns.includes(declaredFilter) && normalizeMiLinkMatchId(row[declaredFilter]) === parentId
  // Multiple collection FKs without a declared filter relationship are ambiguous, not a union scope.
  return columns.length === 1 && normalizeMiLinkMatchId(row[columns[0]!]) === parentId
}
