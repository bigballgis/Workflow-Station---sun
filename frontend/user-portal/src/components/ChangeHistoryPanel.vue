<template>
  <div class="change-history-panel">
    <div v-if="loading" class="loading-state">
      <el-skeleton animated :count="3">
        <template #template>
          <el-skeleton-item variant="text" style="width: 80%; margin-bottom: 12px;" />
        </template>
      </el-skeleton>
    </div>
    <div v-else-if="error" class="error-state">
      <el-alert :title="error" type="warning" show-icon :closable="false" />
    </div>
    <div v-else-if="records.length === 0" class="empty-state">
      <el-empty :description="t('changeHistory.noRecords')" :image-size="80" />
    </div>
    <el-timeline v-else>
      <el-timeline-item
        v-for="record in records"
        :key="record.id"
        :timestamp="formatTimestamp(record.timestamp)"
        placement="top"
        :type="record.concurrent ? 'warning' : undefined"
      >
        <div class="history-item">
          <div class="history-header">
            <span class="user-name">{{ record.userName || record.userId }}</span>
            <el-tag :type="getChangeTypeTag(record.changeType)" size="small">
              {{ getChangeTypeLabel(record.changeType) }}
            </el-tag>
            <el-icon v-if="record.concurrent" class="concurrent-icon" color="#e6a23c">
              <Warning />
            </el-icon>
            <el-tooltip v-if="record.concurrent" :content="t('changeHistory.concurrentWarning')">
              <span class="concurrent-text">{{ t('changeHistory.concurrentWarning') }}</span>
            </el-tooltip>
          </div>
          <div class="history-body">
            <template v-if="record.subTableName">
              <span class="field-label">{{ t('changeHistory.subTable') }}: {{ record.subTableName }}</span>
              <span class="field-label"> / {{ t('changeHistory.row') }}: {{ record.rowIdentifier }}</span>
              <span class="field-label"> / {{ record.fieldLabel || record.fieldName }}</span>
            </template>
            <span v-else class="field-label">{{ record.fieldLabel || record.fieldName }}</span>
            <template v-if="record.changeType !== 'PROCESS_INITIATION'">
              <span class="old-value" v-if="record.oldValue !== null">
                {{ t('changeHistory.changedFrom') }}
                <code>{{ record.oldValue ?? '-' }}</code>
              </span>
              <span class="new-value">
                {{ t('changeHistory.changedTo') }}
                <code>{{ record.newValue ?? '-' }}</code>
              </span>
            </template>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Warning } from '@element-plus/icons-vue'
import { getChangeHistory, type ChangeHistoryRecord } from '@/api/processForm'
import dayjs from 'dayjs'

const { t } = useI18n()

interface Props {
  processInstanceId: string
}

const props = defineProps<Props>()

const loading = ref(false)
const error = ref<string | null>(null)
const records = ref<ChangeHistoryRecord[]>([])

async function loadHistory() {
  if (!props.processInstanceId) return
  loading.value = true
  error.value = null
  try {
    const res = await getChangeHistory(props.processInstanceId) as Record<string, unknown>
    const raw = res?.data ?? res
    records.value = Array.isArray(raw) ? raw : []
  } catch (e: any) {
    console.error('Failed to load change history:', e)
    error.value = t('changeHistory.loadFailed')
    records.value = []
  } finally {
    loading.value = false
  }
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

watch(() => props.processInstanceId, () => {
  loadHistory()
})

onMounted(() => {
  loadHistory()
})
</script>

<style scoped lang="scss">
.change-history-panel {
  width: 100%;

  .history-item {
    .history-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;

      .user-name {
        font-weight: 500;
      }

      .concurrent-icon {
        font-size: 16px;
      }

      .concurrent-text {
        color: #e6a23c;
        font-size: 12px;
      }
    }

    .history-body {
      font-size: 13px;
      color: #606266;

      .field-label {
        font-weight: 500;
        margin-right: 4px;
      }

      .old-value code,
      .new-value code {
        background: #f5f7fa;
        padding: 1px 4px;
        border-radius: 3px;
        font-size: 12px;
        margin: 0 2px;
      }

      .old-value code {
        text-decoration: line-through;
        color: #f56c6c;
      }

      .new-value code {
        color: #67c23a;
      }
    }
  }
}
</style>
