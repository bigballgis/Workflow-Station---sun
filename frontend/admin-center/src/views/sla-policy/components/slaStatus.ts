import type { SlaDispatchStatus, SlaJobStatus } from '@/api/slaPolicy'

type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

export function jobStatusTagType(status: SlaJobStatus | null | undefined): TagType {
  switch (status) {
    case 'SUCCEEDED': return 'success'
    case 'PARTIAL': return 'warning'
    case 'FAILED': return 'danger'
    case 'RUNNING':
    case 'PENDING': return 'primary'
    default: return 'info'
  }
}

export function dispatchTagType(status: SlaDispatchStatus): TagType {
  return status === 'DISPATCHED' ? 'success' : status === 'DISPATCH_FAILED' ? 'danger' : 'info'
}
