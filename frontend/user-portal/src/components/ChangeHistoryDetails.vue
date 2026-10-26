<template>
  <el-table
    :data="rows"
    border
    stripe
    size="small"
    class="change-history-table"
    :empty-text="t('changeHistory.noRecords')"
  >
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
          <el-icon class="concurrent-icon">
            <Warning />
          </el-icon>
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
        >{{ formatDisplayValue(row.oldValue, HISTORY_VALUE_MAX_LENGTH, row.fieldName) }}</span>
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
        >{{ formatDisplayValue(row.newValue, HISTORY_VALUE_MAX_LENGTH, row.fieldName) }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Warning } from '@element-plus/icons-vue'
import type { ChangeHistoryRecord } from '@/api/processForm'
import { HISTORY_VALUE_MAX_LENGTH, type ChangeHistoryFormatting } from '@/composables/changeHistoryPanel/useChangeHistoryFormatting'

const props = defineProps<{ rows: ChangeHistoryRecord[]; formatting: ChangeHistoryFormatting }>()
const { t } = useI18n()
const { fieldLocationLabel, formatDisplayValue, getChangeTypeLabel, getChangeTypeTag } = props.formatting
</script>

<style scoped lang="scss">
.change-history-table { width: 100%; :deep(.el-table__cell) { font-size: 13px; } }
.concurrent-icon { color: var(--el-color-warning); margin-left: 6px; vertical-align: middle; }
.cell-old { color: var(--el-color-danger); word-break: break-word; }
.cell-new { color: var(--el-color-success); word-break: break-word; }
.cell-muted { color: var(--el-text-color-placeholder); }
</style>
