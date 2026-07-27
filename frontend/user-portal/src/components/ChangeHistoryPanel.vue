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
        <div
          v-else

        >
          <el-table
            :data="records"
            border
            stripe
            size="small"
            class="change-history-table"
            :empty-text="t('changeHistory.noRecords')"
          >
            <el-table-column
              :label="t('changeHistory.colStage')"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ resolveStageDisplay(row) || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('changeHistory.colChangeDate')"
              min-width="175"
            >
              <template #default="{ row }">
                {{ formatTimestamp(row.timestamp) }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('changeHistory.colChangedBy')"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.userId && resolveOperator(row) !== row.userId"
                  :content="row.userId"
                  placement="top"
                >
                  <span>{{ resolveOperator(row) }}</span>
                </el-tooltip>
                <span v-else>{{ resolveOperator(row) }}</span>
              </template>
            </el-table-column>
<el-table-column
              :label="t('changeHistory.colEvent')"
              min-width="150"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <el-tag
                  :type="getChangeTypeTag(row.changeType)"
                  size="small"
                >
                  {{ getChangeTypeLabel(row.changeType) }}
                </el-tag>
                <el-tooltip
                  v-if="row.concurrent"
                  :content="t('changeHistory.concurrentWarning')"
                >
                  <el-icon class="concurrent-icon"><Warning /></el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('changeHistory.colChangedField')"
              min-width="210"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ fieldLocationLabel(row) }}
              </template>
            </el-table-column>
            <el-table-column
              :label="t('changeHistory.colOldValue')"
              min-width="180"
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
              min-width="180"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <span
                  v-if="row.changeType === 'PROCESS_INITIATION'"
                  class="cell-muted"
                >{{ t('changeHistory.processStarted') }}</span>
                <span
                  v-else
                  class="cell-new"
                >{{ formatDisplayValue(row.newValue) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Document, Warning } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { useChangeHistoryFormatting } from '@/composables/changeHistoryPanel/useChangeHistoryFormatting'
import { useChangeHistoryLoader } from '@/composables/changeHistoryPanel/useChangeHistoryLoader'


const { t } = useI18n()

interface Props {
  rowIdentifier?: string
  taskId?: string
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
  resolveOperator,
  resolveStageDisplay,
} = formatting

// 拉取历史并按快照时间/任务过滤
const { loading, error, records } = useChangeHistoryLoader(props, t, dayjs)

const sectionExpandedNames = ref(['history'])
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



.concurrent-icon {
  font-size: 16px;




  
  color: var(--el-color-warning);


  margin-left: 6px;
  vertical-align: middle;
}

.change-history-table {
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
