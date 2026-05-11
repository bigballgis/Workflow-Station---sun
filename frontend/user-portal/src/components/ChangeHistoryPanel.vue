<template>
  <el-collapse
    v-model="sectionExpandedNames"
    class="ch-root-collapse"
  >
    <el-collapse-item name="history">
      <template #title>
        <div class="ch-collapse-title">
          <el-icon class="ch-title-icon">
            <Document />
          </el-icon>
          <span class="ch-collapse-title-text">{{ t('changeHistory.title') }}</span>
        </div>
      </template>

      <div class="change-history-panel-inner">
        <div
          v-if="loading"
          class="loading-state"
        >
          <el-skeleton
            animated
            :count="3"
          >
            <template #template>
              <el-skeleton-item
                variant="text"
                style="width: 80%; margin-bottom: 12px;"
              />
            </template>
          </el-skeleton>
        </div>
        <div
          v-else-if="error"
          class="error-state"
        >
          <el-alert
            :title="error"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>
        <div
          v-else-if="records.length === 0"
          class="empty-state"
        >
          <el-empty
            :description="t('changeHistory.noRecords')"
            :image-size="80"
          />
        </div>
        <el-timeline
          v-else
          class="change-timeline"
        >
          <el-timeline-item
            v-for="(batch, batchIndex) in groupedBatches"
            :key="batchIndex"
            :timestamp="formatTimestamp(batch.timestamp)"
            placement="top"
            :type="batch.concurrent ? 'warning' : undefined"
          >
            <div class="batch-card">
              <div
                class="batch-header"
                role="button"
                tabindex="0"
                @click="toggleBatchTable(batchIndex)"
                @keydown.enter.prevent="toggleBatchTable(batchIndex)"
              >
                <el-icon class="batch-chevron">
                  <ArrowDown v-if="isBatchTableOpen(batchIndex)" />
                  <ArrowRight v-else />
                </el-icon>
                <div class="batch-header-body">
                  <div class="batch-meta">
                    <span class="user-line">
                      <span class="label">{{ t('changeHistory.operator') }}</span>
                      <el-tooltip
                        v-if="batch.userId && batch.displayOperator !== batch.userId"
                        :content="batch.userId"
                        placement="top"
                      >
                        <span class="value value--primary">{{ batch.displayOperator }}</span>
                      </el-tooltip>
                      <span
                        v-else
                        class="value value--primary"
                      >{{ batch.displayOperator }}</span>
                    </span>
                    <span
                      v-if="batch.taskInstanceId"
                      class="task-line"
                    >
                      <span class="label">{{ t('changeHistory.relatedTask') }}</span>
                      <el-tooltip
                        :content="batch.taskInstanceId"
                        placement="top"
                      >
                        <el-tag
                          size="small"
                          type="info"
                          class="task-tag"
                        >{{ batch.taskDisplayLabel }}</el-tag>
                      </el-tooltip>
                    </span>
                    <span
                      v-if="batch.displayStage || batch.stageId"
                      class="stage-line"
                    >
                      <span class="label">{{ t('changeHistory.stage') }}</span>
                      <el-tooltip
                        v-if="batch.stageTooltip"
                        :content="batch.stageTooltip"
                        placement="top"
                      >
                        <span class="stage-name stage-name--hint">{{ batch.displayStage || batch.stageId }}</span>
                      </el-tooltip>
                      <span
                        v-else
                        class="stage-name"
                      >{{ batch.displayStage || batch.stageId || '—' }}</span>
                    </span>
                  </div>
                  <div class="batch-summary">
                    <el-tag
                      size="small"
                      effect="plain"
                    >
                      {{ t('changeHistory.batchChanges', { count: batch.rows.length }) }}
                    </el-tag>
                    <el-icon
                      v-if="batch.concurrent"
                      class="concurrent-icon"
                      color="#e6a23c"
                    >
                      <Warning />
                    </el-icon>
                    <el-tooltip
                      v-if="batch.concurrent"
                      :content="t('changeHistory.concurrentWarning')"
                    >
                      <span class="concurrent-text">{{ t('changeHistory.concurrentWarning') }}</span>
                    </el-tooltip>
                  </div>
                </div>
              </div>

              <div
                v-show="isBatchTableOpen(batchIndex)"
                class="batch-table-wrap"
              >
                <el-table
                  :data="batch.rows"
                  border
                  stripe
                  size="small"
                  class="batch-table"
                  :empty-text="t('changeHistory.noRecords')"
                >
                  <el-table-column
                    :label="t('changeHistory.colChangeType')"
                    min-width="120"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">
                      <el-tag
                        :type="getChangeTypeTag(row.changeType)"
                        size="small"
                      >
                        {{ getChangeTypeLabel(row.changeType) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column
                    :label="t('changeHistory.colField')"
                    min-width="200"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">
                      {{ fieldLocationLabel(row) }}
                    </template>
                  </el-table-column>
                  <el-table-column
                    :label="t('changeHistory.colOldValue')"
                    min-width="160"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">
                      <span
                        v-if="row.changeType === 'PROCESS_INITIATION'"
                        class="cell-muted"
                      >—</span>
                      <span
                        v-else
                        class="cell-old"
                      >{{ formatDisplayValue(row.oldValue) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column
                    :label="t('changeHistory.colNewValue')"
                    min-width="160"
                    show-overflow-tooltip
                  >
                    <template #default="{ row }">
                      <span
                        v-if="row.changeType === 'PROCESS_INITIATION'"
                        class="cell-muted"
                      >
                        {{ t('changeHistory.processStarted') }}
                      </span>
                      <span
                        v-else
                        class="cell-new"
                      >{{ formatDisplayValue(row.newValue) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowRight, Document, Warning } from '@element-plus/icons-vue'
import { getChangeHistory, type ChangeHistoryRecord } from '@/api/processForm'
import dayjs from 'dayjs'

const { t } = useI18n()

interface Props {
  processInstanceId: string
  snapshotTime?: string
  taskInstanceId?: string
}

const props = defineProps<Props>()

interface ChangeBatch {
  timestamp: string
  userId: string
  userName: string
  displayOperator: string
  taskInstanceId: string | null
  stageId: string | null
  displayStage: string
  /** 与展示名不同或需补充技术 ID 时的 tooltip */
  stageTooltip: string | null
  /** 关联任务展示：优先 BPMN/环节解析名，否则短 ID；完整 ID 见 tooltip */
  taskDisplayLabel: string
  concurrent: boolean
  rows: ChangeHistoryRecord[]
}

const loading = ref(false)
const error = ref<string | null>(null)
const records = ref<ChangeHistoryRecord[]>([])

/** 整块「变更历史」折叠；默认展开 */
const sectionExpandedNames = ref<string[]>(['history'])

/** 每批次的明细表格是否展开；默认展开 */
const batchTableOpen = ref<Record<number, boolean>>({})

/** 同一操作：同一用户、同一任务/环节，且时间戳间隔在阈值内（一次保存内多条字段记录） */
const SAME_ACTION_MS = 80

function sameSaveAction(a: ChangeHistoryRecord, b: ChangeHistoryRecord): boolean {
  if (a.userId !== b.userId) return false
  if ((a.taskInstanceId ?? '') !== (b.taskInstanceId ?? '')) return false
  if ((a.stageId ?? '') !== (b.stageId ?? '')) return false
  const da = dayjs(a.timestamp)
  const db = dayjs(b.timestamp)
  if (!da.isValid() || !db.isValid()) return false
  return Math.abs(da.diff(db)) <= SAME_ACTION_MS
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

function batchHeaderFields(row: ChangeHistoryRecord) {
  const displayStage = resolveStageDisplay(row)
  return {
    displayOperator: resolveOperator(row),
    displayStage,
    stageTooltip: resolveStageTooltip(row, displayStage),
    taskDisplayLabel: resolveTaskDisplayLabel(row),
  }
}

const groupedBatches = computed<ChangeBatch[]>(() => {
  const sorted = [...records.value].sort((a, b) => {
    const ta = dayjs(a.timestamp).valueOf()
    const tb = dayjs(b.timestamp).valueOf()
    if (ta !== tb) return ta - tb
    return (Number(a.id) || 0) - (Number(b.id) || 0)
  })

  const batches: ChangeBatch[] = []
  for (const r of sorted) {
    const last = batches[batches.length - 1]
    const header = batchHeaderFields(r)
    if (last && last.rows.length > 0 && sameSaveAction(last.rows[last.rows.length - 1]!, r)) {
      last.rows.push(r)
      if (r.concurrent) last.concurrent = true
    } else {
      batches.push({
        timestamp: r.timestamp,
        userId: r.userId,
        userName: r.userName,
        displayOperator: header.displayOperator,
        taskInstanceId: r.taskInstanceId,
        stageId: r.stageId,
        displayStage: header.displayStage,
        stageTooltip: header.stageTooltip,
        taskDisplayLabel: header.taskDisplayLabel,
        concurrent: r.concurrent,
        rows: [r],
      })
    }
  }
  return batches
})

watch(
  groupedBatches,
  (batches) => {
    const next: Record<number, boolean> = {}
    batches.forEach((_, i) => {
      next[i] = batchTableOpen.value[i] ?? true
    })
    batchTableOpen.value = next
  },
  { immediate: true },
)

function toggleBatchTable(index: number) {
  const cur = batchTableOpen.value[index] ?? true
  batchTableOpen.value = { ...batchTableOpen.value, [index]: !cur }
}

function isBatchTableOpen(index: number): boolean {
  return batchTableOpen.value[index] !== false
}

function shortId(id: string): string {
  if (!id || id.length <= 14) return id
  return `${id.slice(0, 8)}…${id.slice(-4)}`
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
  if (s.length <= maxLen) return s
  return `${s.slice(0, maxLen)}…`
}

async function loadHistory() {
  if (!props.processInstanceId) return
  loading.value = true
  error.value = null
  try {
    const res = await getChangeHistory(props.processInstanceId) as Record<string, unknown>
    const raw = res?.data ?? res
    records.value = Array.isArray(raw) ? raw.filter(shouldKeepRecordInSnapshot) : []
  } catch (e: unknown) {
    console.error('Failed to load change history:', e)
    error.value = t('changeHistory.loadFailed')
    records.value = []
  } finally {
    loading.value = false
  }
}

function shouldKeepRecordInSnapshot(row: ChangeHistoryRecord): boolean {
  if (!props.snapshotTime && !props.taskInstanceId) return true
  if (props.taskInstanceId && row.taskInstanceId === props.taskInstanceId) return true
  if (!props.snapshotTime) return true

  const item = dayjs(row.timestamp)
  const cutoff = dayjs(props.snapshotTime)
  if (!item.isValid() || !cutoff.isValid()) return true
  return item.valueOf() <= cutoff.valueOf()
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

watch(() => [props.processInstanceId, props.snapshotTime, props.taskInstanceId], () => {
  loadHistory()
})

onMounted(() => {
  loadHistory()
})
</script>

<style scoped lang="scss">
/* 与任务/申请详情页 .section .section-header（流转记录等）视觉一致 */
.ch-root-collapse {
  width: 100%;
  border: none;

  :deep(.el-collapse-item__header) {
    display: flex;
    align-items: center;
    font-size: 16px;
    font-weight: 500;
    line-height: 1.4;
    height: auto;
    min-height: unset;
    padding: 16px 20px;
    background: #fafafa;
    border: none;
    border-bottom: 1px solid var(--border-color, #e4e7ed);
    color: var(--text-primary, #333333);
  }

  :deep(.el-collapse-item__wrap) {
    border: none;
  }

  :deep(.el-collapse-item__content) {
    padding: 20px;
    padding-bottom: 20px;
  }

  :deep(.el-collapse-item__arrow) {
    margin: 0 8px 0 0;
    color: var(--text-secondary, #666666);
  }
}

.ch-collapse-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ch-collapse-title-text {
  color: var(--text-primary, #333333);
  font-size: 16px;
  font-weight: 500;
}

.ch-title-icon {
  font-size: 18px;
  color: var(--hsbc-red, #db0011);
}

.change-history-panel-inner {
  width: 100%;
}

.change-timeline {
  padding-left: 4px;
}

.batch-card {
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 4px;
}

.batch-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
    border-radius: 4px;
  }
}

.batch-chevron {
  font-size: 16px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  flex-shrink: 0;
}

.batch-header-body {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.batch-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);

  .label {
    color: var(--el-text-color-secondary);
    margin-right: 4px;
  }

  .value--primary {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .stage-name {
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  .stage-name--hint {
    border-bottom: 1px dotted var(--el-border-color);
    cursor: help;
  }
}

.batch-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.concurrent-icon {
  font-size: 16px;
}

.concurrent-text {
  color: var(--el-color-warning);
  font-size: 12px;
}

.batch-table-wrap {
  margin-top: 4px;
}

.batch-table {
  width: 100%;

  :deep(.el-table__cell) {
    font-size: 13px;
  }
}

.cell-old {
  color: var(--el-color-danger);
  word-break: break-word;
}

.cell-new {
  color: var(--el-color-success);
  word-break: break-word;
}

.cell-muted {
  color: var(--el-text-color-placeholder);
}
</style>
