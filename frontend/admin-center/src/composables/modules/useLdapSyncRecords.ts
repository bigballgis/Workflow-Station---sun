/**
 * LDAP sync records page (D-51): shared list, true paging, read-only for everyone who sees it.
 * Membership changes a sync makes are in the Admin operation audit (operator "LDAP sync", D-32).
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyError } from '@/utils/notify'
import { ldapSyncApi, type LdapSyncRecord } from '@/api/ldapSync'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'
import { ApiError } from '@/types/errors'

type StatusTone = 'success' | 'warning' | 'danger' | 'info'

const STATUS_TONE: Record<LdapSyncRecord['status'], StatusTone> = {
  SUCCESS: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger',
  RUNNING: 'info',
}

/**
 * The full Message of a record for its hover tooltip: one finding per line. A group run joins its findings
 * (Not read…, Read incompletely…, No members found…, Kept the AD members of…) with "; ".
 */
export function messageTooltip(message: string | null): string {
  return (message ?? '').split('; ').join('\n')
}

export function useLdapSyncRecords() {
  const { t } = useI18n()

  const loading = ref(false)
  const keyword = ref('')

  const grid = useAdminListGrid<LdapSyncRecord>({
    storageKey: 'admin-list-layout:ldap-sync-records',
  })

  const statusTone = (status: LdapSyncRecord['status']): StatusTone => STATUS_TONE[status] ?? 'info'

  /** Enum labels come from the backend column declaration (single source). */
  const optionLabel = (field: string, value: string): string => {
    const column = grid.displayColumns.value.find((col) => col.field === field)
    return column?.options?.find((option) => option.value === value)?.label ?? value
  }

  const formatDuration = (durationMs: number | null): string => {
    if (durationMs === null || durationMs === undefined || durationMs < 0) return '—'
    if (durationMs < 60_000) return `${(durationMs / 1000).toFixed(1)}s`
    const minutes = Math.floor(durationMs / 60_000)
    const seconds = Math.round((durationMs % 60_000) / 1000)
    return `${minutes}m ${String(seconds).padStart(2, '0')}s`
  }

  const loadRecords = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const envelope = await ldapSyncApi.queryRecords({
        ...grid.buildQuery(),
        keyword: keyword.value || undefined,
      })
      if (!grid.isCurrentQuery(seq)) return
      if (!envelope.data) {
        throw new Error('ldap-sync/audit-logs/query response is missing data')
      }
      grid.applyPage(envelope.data, 'ldap-sync/audit-logs/query response is missing its column declaration')
    } catch (error: unknown) {
      if (!grid.isCurrentQuery(seq)) return
      // Request errors are already reported by the request layer.
      if (!(error instanceof ApiError)) {
        notifyError(t('ldapSync.loadFailed'))
      }
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  return {
    loading,
    keyword,
    statusTone,
    optionLabel,
    formatDuration,
    messageTooltip,
    loadRecords,
    ...grid,
  }
}
