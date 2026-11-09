import { post } from './request'
import type { AdminListPage } from '@/types/common'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'

interface ApiEnvelope<T> {
  success: boolean
  data: T
}

/** One LDAP sync run (backend `LdapSyncAuditView`); types and statuses follow `LdapSyncAuditColumnSpec`. */
export interface LdapSyncRecord {
  id: string
  syncType: 'FULL' | 'INCREMENTAL' | 'HERMES_AD_GROUP' | 'HERMES_AD_INCR'
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
  totalFetched: number | null
  upserted: number | null
  failed: number | null
  message: string | null
  startedAt: string
  finishedAt: string | null
  highWaterMark: string | null
  groups: string | null
  successCount: number | null
  skippedMissingKey: number | null
  insertCount: number | null
  updateCount: number | null
  durationMs: number | null
}

export interface LdapSyncRecordQuery {
  page: number
  size: number
  keyword?: string
  filters?: Array<ListColumnFilter & { field: string }>
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

export const ldapSyncApi = {
  /** Sync records, read-only for SYS_ADMIN, Technical Leads and Auditors (D-51); syncs are started elsewhere. */
  queryRecords: (body: LdapSyncRecordQuery) =>
    post<ApiEnvelope<AdminListPage<LdapSyncRecord>>>('/ldap-sync/audit-logs/query', body),
}
