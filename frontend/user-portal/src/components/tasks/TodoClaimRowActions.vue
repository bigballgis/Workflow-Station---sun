<template>
  <template v-if="task.claimable">
    <el-button
      type="primary"
      size="small"
      :loading="loading"
      data-test="todo-claim-btn"
      @click="$emit('claim', task)"
    >
      {{ t('task.claim') }}
    </el-button>
  </template>
  <el-button
    v-else-if="task.claimedByCurrentUser"
    size="small"
    :loading="loading"
    data-test="todo-unclaim-btn"
    @click="$emit('unclaim', task)"
  >
    {{ t('task.unclaim') }}
  </el-button>
  <el-button
    v-else-if="task.canForceUnclaim"
    type="warning"
    size="small"
    :loading="loading"
    data-test="todo-force-unclaim-btn"
    @click="$emit('force-unclaim', task)"
  >
    {{ t('task.forceUnclaim') }}
  </el-button>
  <span
    v-else-if="task.claimPoolTask && task.assignee"
    class="todo-held"
    data-test="todo-held"
  >{{ t('task.heldByOther') }}</span>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { TaskInfo } from '@/api/task'

defineProps<{
  task: TaskInfo
  loading: boolean
}>()

defineEmits<{
  claim: [task: TaskInfo]
  unclaim: [task: TaskInfo]
  'force-unclaim': [task: TaskInfo]
}>()

const { t } = useI18n()
</script>

<style scoped>
.todo-held {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
