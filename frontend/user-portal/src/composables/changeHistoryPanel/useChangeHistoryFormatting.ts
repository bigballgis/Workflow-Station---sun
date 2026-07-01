import type { useI18n } from 'vue-i18n'
import type { ChangeHistoryRecord } from '@/api/processForm'

type TranslateFn = ReturnType<typeof useI18n>['t']

/** 批次表头解析后的展示字段集合 */
export interface BatchHeaderFields {
  displayOperator: string
  displayStage: string
  /** 与展示名不同或需补充技术 ID 时的 tooltip */
  stageTooltip: string | null
  /** 关联任务展示：优先 BPMN/环节解析名，否则短 ID；完整 ID 见 tooltip */
  taskDisplayLabel: string
}

export interface ChangeHistoryFormatting {
  shortId: (id: string) => string
  resolveOperator: (row: ChangeHistoryRecord) => string
  humanizeStageKey: (stageId: string | null | undefined) => string
  resolveStageDisplay: (row: ChangeHistoryRecord) => string
  resolveStageTooltip: (row: ChangeHistoryRecord, displayStage: string) => string | null
  resolveTaskDisplayLabel: (row: ChangeHistoryRecord) => string
  batchHeaderFields: (row: ChangeHistoryRecord) => BatchHeaderFields
  fieldLocationLabel: (row: ChangeHistoryRecord) => string
  formatDisplayValue: (raw: string | null | undefined, maxLen?: number) => string
  formatTimestamp: (ts: string) => string
  getChangeTypeLabel: (changeType: string) => string
  getChangeTypeTag: (changeType: string) => 'success' | 'warning' | 'danger' | 'info'
}

/** 历史记录的展示格式化、字段标签与变更类型映射；纯函数，依赖 i18n。 */
export function useChangeHistoryFormatting(
  t: TranslateFn,
  dayjs: typeof import('dayjs'),
): ChangeHistoryFormatting {
  function shortId(id: string): string {
    if (!id || id.length <= 14) return id
    return `${id.slice(0, 8)}…${id.slice(-4)}`
  }

  function resolveOperator(row: ChangeHistoryRecord): string {
    const n = row.userName?.trim()
    if (n) return n
    return row.userId
  }

  function humanizeStageKey(stageId: string | null | undefined): string {
    if (!stageId) return ''
    let s = stageId
    if (/^Task_/i.test(s) && s.length > 5) {
      s = s.slice(5)
    }
    return s.replace(/_/g, ' ')
  }

  function resolveStageDisplay(row: ChangeHistoryRecord): string {
    const n = row.stageName?.trim()
    if (n) return n
    if (row.stageId === 'RETURN_TO_REQUESTER') {
      return t('changeHistory.stageReturnToRequester')
    }
    return humanizeStageKey(row.stageId)
  }

  function resolveStageTooltip(row: ChangeHistoryRecord, displayStage: string): string | null {
    if (!row.stageId) return null
    if (displayStage !== row.stageId) {
      return row.stageId
    }
    return null
  }

  function resolveTaskDisplayLabel(row: ChangeHistoryRecord): string {
    if (!row.taskInstanceId) return ''
    const stageText = resolveStageDisplay(row).trim()
    if (stageText) return stageText
    return shortId(row.taskInstanceId)
  }

  function batchHeaderFields(row: ChangeHistoryRecord): BatchHeaderFields {
    const displayStage = resolveStageDisplay(row)
    return {
      displayOperator: resolveOperator(row),
      displayStage,
      stageTooltip: resolveStageTooltip(row, displayStage),
      taskDisplayLabel: resolveTaskDisplayLabel(row),
    }
  }

  function fieldLocationLabel(row: ChangeHistoryRecord): string {
    if (row.changeType === 'PROCESS_INITIATION') {
      return t('changeHistory.processInitiation')
    }
    if (row.subTableName) {
      const parts = [
        `${t('changeHistory.subTable')}: ${row.subTableName}`,
        `${t('changeHistory.row')}: ${row.rowIdentifier ?? '—'}`,
      ]
      const field = row.fieldLabel?.trim() || row.fieldName
      if (field) parts.push(field)
      return parts.join(' · ')
    }
    return row.fieldLabel?.trim() || row.fieldName || '—'
  }

  function formatDisplayValue(raw: string | null | undefined, maxLen = 240): string {
    if (raw === null || raw === undefined || raw === '') return '—'
    const s = String(raw).trim()
    if (!s) return '—'

    // JSON object/array first — sub-table row data is serialised as JSON
    // and may contain file URLs; we must parse JSON before the file-upload
    // regex, otherwise the regex greedily captures JSON tail content.
    if ((s.startsWith('{') && s.endsWith('}')) || (s.startsWith('[') && s.endsWith(']'))) {
      try {
        const parsed = JSON.parse(s) as unknown
        const compact = JSON.stringify(parsed)
        if (compact.length <= maxLen) return compact
        return `${compact.slice(0, maxLen)}…`
      } catch {
        /* fall through */
      }
    }

    // File upload paths: show original filename instead of internal API path
    const fileUploadMatch = s.match(/\/api\/v1\/upload\/files\/[^?]+\?originalName=([^&]+)/)
    if (fileUploadMatch) {
      const decoded = decodeURIComponent(fileUploadMatch[1]!)
      return decoded.length <= maxLen ? decoded : `${decoded.slice(0, maxLen)}…`
    }

    if (s.length <= maxLen) return s
    return `${s.slice(0, maxLen)}…`
  }

  function formatTimestamp(ts: string): string {
    if (!ts) return '-'
    const d = dayjs(ts)
    return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : ts
  }

  function getChangeTypeLabel(changeType: string): string {
    const map: Record<string, string> = {
      FIELD_UPDATE: t('changeHistory.fieldUpdate'),
      SUB_TABLE_ROW_ADD: t('changeHistory.subTableRowAdd'),
      SUB_TABLE_ROW_UPDATE: t('changeHistory.subTableRowUpdate'),
      SUB_TABLE_ROW_DELETE: t('changeHistory.subTableRowDelete'),
      PROCESS_INITIATION: t('changeHistory.processInitiation'),
    }
    return map[changeType] || changeType
  }

  function getChangeTypeTag(changeType: string): 'success' | 'warning' | 'danger' | 'info' {
    const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
      FIELD_UPDATE: 'info',
      SUB_TABLE_ROW_ADD: 'success',
      SUB_TABLE_ROW_UPDATE: 'warning',
      SUB_TABLE_ROW_DELETE: 'danger',
      PROCESS_INITIATION: 'success',
    }
    return map[changeType] || 'info'
  }

  return {
    shortId,
    resolveOperator,
    humanizeStageKey,
    resolveStageDisplay,
    resolveStageTooltip,
    resolveTaskDisplayLabel,
    batchHeaderFields,
    fieldLocationLabel,
    formatDisplayValue,
    formatTimestamp,
    getChangeTypeLabel,
    getChangeTypeTag,
  }
}
