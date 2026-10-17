<template>
  <section class="batch-assignment-history">
    <h3>{{ t('task.batchHistory.title') }}</h3>
    <p>{{ t('task.batchHistory.hint') }}</p>
    <article v-for="record in records" :key="record.id">
      <time>{{ record.operationTime }}</time>
      <el-alert
        :title="t(`task.batchHistory.${record.batchAssignmentVerification.status}`)"
        :type="record.batchAssignmentVerification.status === 'VERIFIED' ? 'success' : 'warning'"
        :closable="false" show-icon
      />
      <ul v-if="record.batchAssignmentVerification.issues.length">
        <li v-for="issue in record.batchAssignmentVerification.issues" :key="`${record.id}-${issue.taskId}-${issue.reasonCode}`">
          {{ t('task.batchHistory.task') }}: <code>{{ issue.taskId ?? t('task.batchHistory.unknownTask') }}</code>
          · {{ t('task.batchHistory.reason') }}: <code>{{ issue.reasonCode }}</code>
        </li>
      </ul>
    </article>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { BatchAssignmentHistoryRecord } from '@/api/process'
defineProps<{ records: BatchAssignmentHistoryRecord[] }>()
const { t } = useI18n()
</script>

<style scoped>
.batch-assignment-history { margin-bottom: 24px; overflow-wrap: anywhere; }
h3 { margin-bottom: 8px; }
p, time { color: var(--el-text-color-secondary); font-size: 13px; }
article { margin-top: 16px; }
time { display: block; margin-bottom: 6px; }
ul { padding-left: 20px; font-size: 13px; line-height: 1.6; }
</style>
