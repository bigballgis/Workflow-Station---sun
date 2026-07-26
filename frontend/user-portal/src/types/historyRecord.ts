/** Shared shape for flow-history timeline rows (portal task / process views). */
export interface HistoryRecord {
  id: string
  nodeId: string
  nodeName: string
  assigneeId?: string
  assigneeName?: string
  status: 'completed' | 'current' | 'pending' | 'rejected' | 'cancelled'
  action?: 'approve' | 'reject' | 'transfer' | 'delegate' | 'withdraw' | 'submit' | 'return' | 'draft' | 'send'
  comment?: string
  createdTime: string
  completedTime?: string
  duration?: number
  attachments?: Array<{ id: string; name: string; url: string }>
  signatureUrl?: string
  activityType?: string
}
