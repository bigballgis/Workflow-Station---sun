<template>
  <section v-if="rows.length" class="batch-completion-status">
    <article v-for="row in rows" :key="row.operationId">
      <router-link :to="`/tasks/${encodeURIComponent(row.taskId)}`">{{ t('task.assignmentCompletion.openTask') }} · {{ row.taskId }}</router-link>
      <CompletionStatus :value="row" context="batch" @refresh="$emit('refresh', row.taskId)" />
    </article>
  </section>
</template>
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import CompletionStatus from './CompletionStatus.vue'
import type { BatchRecoveryRow } from '@/composables/tasks/useBatchCompletionRecovery'
defineProps<{ rows: BatchRecoveryRow[] }>()
defineEmits<{ (event: 'refresh', taskId: string): void }>()
const { t } = useI18n()
</script>
<style scoped>
.batch-completion-status { margin-bottom: 16px; }
article { padding: 12px; background: var(--el-fill-color-blank); border: 1px solid var(--el-border-color); border-radius: 4px; }
article + article { margin-top: 8px; }
a { display: inline-block; margin-bottom: 8px; overflow-wrap: anywhere; }
</style>
