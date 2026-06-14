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
import { useI18n } from 'vue-i18n'
import { ArrowDown, ArrowRight, Document, Warning } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { useChangeHistoryFormatting } from '@/composables/changeHistoryPanel/useChangeHistoryFormatting'
import { useChangeHistoryLoader } from '@/composables/changeHistoryPanel/useChangeHistoryLoader'
import { useChangeHistoryBatches } from '@/composables/changeHistoryPanel/useChangeHistoryBatches'
import { useChangeHistoryExpansion } from '@/composables/changeHistoryPanel/useChangeHistoryExpansion'

const { t } = useI18n()

interface Props {
  processInstanceId: string
  snapshotTime?: string
  taskInstanceId?: string
}

const props = defineProps<Props>()

// 展示格式化、字段标签与变更类型映射（纯函数）
const formatting = useChangeHistoryFormatting(t, dayjs)
const {
  fieldLocationLabel,
  formatDisplayValue,
  formatTimestamp,
  getChangeTypeLabel,
  getChangeTypeTag,
} = formatting

// 拉取历史并按快照时间/任务过滤
const { loading, error, records } = useChangeHistoryLoader(props, t, dayjs)

// 按「同一保存动作」分组为批次
const { groupedBatches } = useChangeHistoryBatches(records, formatting, dayjs)

// 折叠面板与各批次明细表格的展开/收起状态
const {
  sectionExpandedNames,
  toggleBatchTable,
  isBatchTableOpen,
} = useChangeHistoryExpansion(groupedBatches)
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
