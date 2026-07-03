import type { HistoryRecord } from '@/components/ProcessHistory.vue'
import { processApi } from '@/api/process'
import { getHistoryStatus, getHistoryAction } from './subTableRowHelpers'
import type { ApplicationDetailCtx } from './context'

export interface ApplicationDetailHistoryFns {
  loadProcessHistory: () => Promise<void>
  initHistoryRecords: () => void
}

export function createApplicationDetailHistory(ctx: ApplicationDetailCtx): ApplicationDetailHistoryFns {
  const {
    t,
    processId,
    snapshotTime,
    snapshotTaskName,
    snapshotTaskId,
    processInfo,
    historyRecords,
    snapshotActivityId,
  } = ctx

  // Load flow history
  const loadProcessHistory = async () => {
    try {
      const response = await processApi.getProcessHistory(processId)
      const historyData = response.data || response
      if (historyData && Array.isArray(historyData)) {

        // Running + snapshot: only keep records up to this task; completed processes show full history
        let filteredData = historyData
        if (snapshotTaskId) {
          const snapshotRecord = historyData.find((item: any) => String(item.taskId || '') === snapshotTaskId)
          if (snapshotRecord?.activityId) {
            snapshotActivityId.value = String(snapshotRecord.activityId)
          }
        }
        if (snapshotTaskName && processInfo.value.status === 'RUNNING') {
          // Find the last occurrence of snapshotTaskName in the history list (sorted by time) and truncate there
          const snapshotIdx = historyData.map((item: any) => item.activityName || item.taskName).lastIndexOf(snapshotTaskName)
          if (snapshotIdx >= 0) {
            filteredData = historyData.slice(0, snapshotIdx + 1)
          } else if (snapshotTime) {
            // activityName match failed (might be a BPMN element ID), fall back to time-based truncation
            const cutoff = new Date(snapshotTime).getTime()
            const timeIdx = historyData.map((item: any) => new Date(item.operationTime || 0).getTime()).lastIndexOf(cutoff)
            if (timeIdx >= 0) {
              filteredData = historyData.slice(0, timeIdx + 1)
            } else {
              // Keep all records with operationTime <= snapshotTime
              filteredData = historyData.filter((item: any) => {
                const t = new Date(item.operationTime || 0).getTime()
                return t <= cutoff
              })
            }
          }
        }

        // Convert to HistoryRecord format (keep gateway records for diagram status determination)
        historyRecords.value = filteredData.map((item: any, index: number) => ({
          id: `history_${index}`,
          nodeId: item.activityId || `node_${index}`,
          nodeName: item.activityName || item.taskName || t('applicationDetail.unknownNode'),
          status: getHistoryStatus(item.operationType),
          action: getHistoryAction(item.operationType),
          assigneeName: item.operatorName || '-',
          comment: item.comment,
          createdTime: item.operationTime || '',
          completedTime: item.operationTime,
          activityType: item.activityType || ''
        }))
      } else {
        initHistoryRecords()
      }
    } catch (error: any) {
      console.error('Failed to load process history:', error)
      initHistoryRecords()
    }
  }

  // Initialize flow history records
  const initHistoryRecords = () => {
    const records: HistoryRecord[] = [{ id: 'submit', nodeId: 'start', nodeName: t('applicationDetail.submitApplication'), status: 'completed', assigneeName: processInfo.value.startUserName || processInfo.value.startUserId, createdTime: processInfo.value.startTime || '' }]
    if (processInfo.value.status === 'RUNNING') records.push({ id: 'current', nodeId: 'task', nodeName: processInfo.value.currentNode || t('applicationDetail.pendingApproval'), status: 'current', assigneeName: processInfo.value.currentAssignee || t('applicationDetail.unassigned'), createdTime: '' })
    else if (processInfo.value.status === 'COMPLETED') records.push({ id: 'end', nodeId: 'end', nodeName: t('applicationDetail.processEnded'), status: 'completed', createdTime: processInfo.value.endTime || '' })
    else if (processInfo.value.status === 'WITHDRAWN') records.push({ id: 'withdrawn', nodeId: 'withdrawn', nodeName: t('applicationDetail.processWithdrawn'), status: 'rejected', assigneeName: processInfo.value.startUserName || processInfo.value.startUserId, createdTime: processInfo.value.endTime || '' })
    historyRecords.value = records
  }

  return {
    loadProcessHistory,
    initHistoryRecords,
  }
}
