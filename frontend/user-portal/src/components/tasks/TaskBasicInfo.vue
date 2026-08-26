<template>
  <div class="section info-section">
    <div class="section-header">
      <el-icon><InfoFilled /></el-icon>
      <span>{{ $t('task.basicInfo') }}</span>
    </div>
    <div class="section-content">
      <el-descriptions
        :column="3"
        border
      >
        <el-descriptions-item
          v-if="taskInfo.requestId"
          :label="$t('task.requestId')"
        >
          {{ taskInfo.requestId }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.taskName')">
          {{ taskInfo.taskName || $t('common.empty') }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.currentStep')">
          {{ currentStepDisplay }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.processName')">
          {{ taskInfo.processDefinitionName || $t('common.empty') }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.initiator')">
          {{ taskInfo.initiatorName || $t('common.empty') }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.createTime')">
          {{ formatDate(taskInfo.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.dueDate')">
          <span :class="{ 'overdue': taskInfo.isOverdue }">
            {{ taskInfo.dueDate ? formatDate(taskInfo.dueDate) : $t('common.empty') }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('task.currentAssignee')">
          {{ getCurrentAssigneeDisplay() }}
        </el-descriptions-item>
        <el-descriptions-item
          v-if="getDelegationStatusDisplay && getDelegationStatusDisplay()"
          :label="$t('task.delegationStatus')"
        >
          {{ getDelegationStatusDisplay() }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  taskInfo: Record<string, any>
  formatDate: (date?: any) => string
  getCurrentAssigneeDisplay: () => string
  getDelegationStatusDisplay?: () => string
  /** 已完成任务：Current Step 无「当前」语义，显示 '-'（与申请详情终态一致）。 */
  isCompleted?: boolean
}>()

const { t } = useI18n()

/**
 * Current Step（MI 感知）：优先用后端 currentStepName（多实例子任务内=外层多实例节点名如 "multi"），
 * 回退 taskName（内层/普通节点名）；已完成任务显示 '-'。
 */
const currentStepDisplay = computed(() => {
  if (props.isCompleted) return '-'
  return props.taskInfo.currentStepName || props.taskInfo.taskName || t('common.empty')
})
</script>

<style lang="scss" scoped>
.section {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--border-color, #e4e7ed);

  .section-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 16px 20px;
    border-bottom: 1px solid var(--border-color, #e4e7ed);
    font-weight: 500;
    font-size: 16px;
    color: var(--text-primary);
  }

  .section-content {
    padding: 20px;
  }
}

.overdue {
  color: var(--error-red, #f56c6c);
}
</style>
