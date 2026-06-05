<template>
  <div class="process-history">
    <div
      v-if="showHeader"
      class="history-header"
    >
      <span class="title">{{ $t('process.history') }}</span>
      <el-button
        v-if="showRefresh"
        :icon="Refresh"
        circle
        size="small"
        @click="handleRefresh"
      />
    </div>

    <el-timeline v-if="records.length > 0">
      <el-timeline-item
        v-for="record in visibleRecords"
        :key="record.id"
        :timestamp="formatTime(record.completedTime || record.createdTime)"
        :type="getTimelineType(record.status)"
        :hollow="record.status === 'pending'"
        placement="top"
      >
        <el-card
          shadow="never"
          class="history-card"
        >
          <div class="card-header">
            <span class="node-name">{{ record.nodeName }}</span>
            <el-tag
              :type="getStatusTagType(record.status)"
              size="small"
            >
              {{ getStatusText(record.status) }}
            </el-tag>
          </div>

          <div class="card-content">
            <div
              v-if="record.assigneeName"
              class="info-row"
            >
              <span class="label">{{ $t('task.assignee') }}:</span>
              <span class="value">
                <el-avatar
                  :size="20"
                  class="avatar"
                >
                  {{ record.assigneeName.charAt(0) }}
                </el-avatar>
                {{ record.assigneeName }}
              </span>
            </div>

            <div
              v-if="record.action"
              class="info-row"
            >
              <span class="label">{{ $t('task.action') }}:</span>
              <span
                class="value action-text"
                :class="record.action"
              >
                {{ getActionText(record.action) }}
              </span>
            </div>

            <div
              v-if="record.comment"
              class="info-row"
            >
              <span class="label">{{ $t('task.comment') }}:</span>
              <span class="value comment">{{ record.comment }}</span>
            </div>

            <div
              v-if="record.duration"
              class="info-row"
            >
              <span class="label">{{ $t('task.duration') }}:</span>
              <span class="value">{{ formatDuration(record.duration) }}</span>
            </div>

            <!-- 附件列表 -->
            <div
              v-if="record.attachments?.length"
              class="attachments"
            >
              <span class="label">{{ $t('common.attachments') }}:</span>
              <div class="attachment-list">
                <el-link
                  v-for="file in record.attachments"
                  :key="file.id"
                  type="primary"
                  :underline="false"
                  @click="handleDownload(file)"
                >
                  <el-icon><Document /></el-icon>
                  {{ file.name }}
                </el-link>
              </div>
            </div>
          </div>

          <!-- 签名图片 -->
          <div
            v-if="record.signatureUrl"
            class="signature"
          >
            <img
              :src="record.signatureUrl"
              alt="Signature"
            >
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>

    <div
      v-if="canToggle"
      class="toggle-history"
    >
      <el-link
        type="primary"
        :underline="false"
        @click="expanded = !expanded"
      >
        <el-icon><ArrowDown v-if="!expanded" /><ArrowUp v-else /></el-icon>
        {{ expanded ? t('process.collapseHistory') : t('process.expandHistory', { count: hiddenCount }) }}
      </el-link>
    </div>

    <el-empty
      v-else-if="records.length === 0"
      :description="$t('common.noData')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Document, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { HistoryRecord } from '@/types/historyRecord'

export type { HistoryRecord } from '@/types/historyRecord'

interface Props {
  records: HistoryRecord[]
  showHeader?: boolean
  showRefresh?: boolean
  collapsible?: boolean
  defaultVisibleCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  records: () => [],
  showHeader: true,
  showRefresh: true,
  collapsible: false,
  defaultVisibleCount: 1
})

const expanded = ref(false)

const canToggle = computed(() =>
  props.collapsible && props.records.length > props.defaultVisibleCount
)

const hiddenCount = computed(() =>
  props.records.length - props.defaultVisibleCount
)

const visibleRecords = computed(() => {
  if (!props.collapsible || expanded.value) return props.records
  return props.records.slice(-props.defaultVisibleCount)
})

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'download', file: { id: string; name: string; url: string }): void
}>()

const { t } = useI18n()

// 获取时间线类型
const getTimelineType = (status: string) => {
  switch (status) {
    case 'completed':
      return 'success'
    case 'current':
      return 'warning'
    case 'rejected':
    case 'cancelled':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取状态标签类型
const getStatusTagType = (status: string) => {
  switch (status) {
    case 'completed':
      return 'success'
    case 'current':
      return 'warning'
    case 'rejected':
    case 'cancelled':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取状态文本
const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    completed: t('status.completed'),
    current: t('status.processing'),
    pending: t('status.pending'),
    rejected: t('status.rejected'),
    cancelled: t('status.cancelled')
  }
  return statusMap[status] || status
}

// 获取操作文本
const getActionText = (action: string) => {
  const actionMap: Record<string, string> = {
    approve: t('action.approve'),
    reject: t('action.reject'),
    transfer: t('action.transfer'),
    delegate: t('action.delegate'),
    withdraw: t('action.withdraw'),
    submit: t('action.submit')
  }
  return actionMap[action] || action
}

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 格式化时长
const formatDuration = (ms: number) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 0) {
    return `${days}d${hours % 24}h`
  } else if (hours > 0) {
    return `${hours}h${minutes % 60}m`
  } else if (minutes > 0) {
    return `${minutes}m`
  } else {
    return `${seconds}s`
  }
}

// 刷新
const handleRefresh = () => {
  emit('refresh')
}

// 下载附件
const handleDownload = (file: { id: string; name: string; url: string }) => {
  emit('download', file)
}
</script>

<style scoped lang="scss">
.process-history {
  .toggle-history {
    text-align: center;
    padding: 8px 0 4px;

    .el-link {
      font-size: 13px;
      gap: 4px;
    }
  }

  .history-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
  }

  .history-card {
    :deep(.el-card__body) {
      padding: 12px 16px;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      .node-name {
        font-weight: 600;
        color: #303133;
      }
    }

    .card-content {
      .info-row {
        display: flex;
        align-items: flex-start;
        margin-bottom: 8px;
        font-size: 13px;

        &:last-child {
          margin-bottom: 0;
        }

        .label {
          color: #909399;
          min-width: 60px;
          flex-shrink: 0;
        }

        .value {
          color: #606266;
          display: flex;
          align-items: center;
          gap: 5px;

          .avatar {
            background: #DB0011;
            color: white;
            font-size: 10px;
          }

          &.action-text {
            font-weight: 500;

            &.approve {
              color: #00A651;
            }

            &.reject {
              color: #DB0011;
            }

            &.transfer,
            &.delegate {
              color: #FF6600;
            }
          }

          &.comment {
            color: #606266;
            background: #f5f7fa;
            padding: 4px 8px;
            border-radius: 4px;
            line-height: 1.5;
          }
        }
      }

      .attachments {
        margin-top: 8px;

        .label {
          color: #909399;
          font-size: 13px;
          display: block;
          margin-bottom: 5px;
        }

        .attachment-list {
          display: flex;
          flex-wrap: wrap;
          gap: 10px;

          .el-link {
            font-size: 12px;
          }
        }
      }
    }

    .signature {
      margin-top: 10px;
      padding-top: 10px;
      border-top: 1px dashed #e4e7ed;

      img {
        max-width: 150px;
        max-height: 60px;
      }
    }
  }
}
</style>
