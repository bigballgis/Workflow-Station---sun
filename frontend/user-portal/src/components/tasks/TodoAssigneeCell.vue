<template>
  <!-- Holder first, the lost-entry flag below it: the column keeps its list width (shared-list §6.6) and the flag
       wraps instead of being cut off by the cell's ellipsis. -->
  <div class="todo-assignee-cell">
    <el-tag
      v-if="isTodoClaimHold(task)"
      :type="task.claimedByCurrentUser ? 'success' : 'info'"
      size="small"
      data-test="todo-current-assignee"
    >
      {{ holder }}
    </el-tag>
    <span
      v-else
      data-test="todo-current-assignee"
    >
      {{ holder }}
    </span>
    <el-tag
      v-if="task.assigneeLostPortalEntry"
      type="warning"
      size="small"
      class="todo-assignee-lost-entry"
      data-test="todo-assignee-lost-entry"
    >
      {{ t('task.assigneeLostPortalEntry') }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TaskInfo } from '@/api/task'
import { formatTodoCurrentAssignee, isTodoClaimHold } from '@/utils/todoCurrentAssignee'

/** To Do「Current Assignee」cell: the holder, flagged when they have lost Portal entry. */
const props = defineProps<{
  task: Pick<TaskInfo, 'claimPoolTask' | 'assignee' | 'assigneeName' | 'claimedByCurrentUser' | 'assigneeLostPortalEntry'>
}>()

const { t } = useI18n()
const holder = computed(() => formatTodoCurrentAssignee(props.task, t('task.claimedByMe')))
</script>

<style scoped>
.todo-assignee-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
  white-space: normal;
}

.todo-assignee-lost-entry {
  max-width: 100%;
  height: auto;
  min-height: 20px;
  padding-top: 2px;
  padding-bottom: 2px;
  line-height: 1.4;
  white-space: normal;
  text-align: left;
}

.todo-assignee-lost-entry :deep(.el-tag__content) {
  white-space: normal;
  overflow-wrap: anywhere;
}
</style>
