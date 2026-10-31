<template>
  <section v-if="value" class="completion-status" data-testid="completion-operation-status" aria-live="polite">
    <el-alert :title="t(`task.assignmentCompletion.${messageKey}`)" :type="value.state === 'READY' ? 'success' : 'warning'" :closable="false" show-icon />
    <p class="operation-identity">{{ t('task.assignmentCompletion.operationId') }}: <code>{{ value.operationId }}</code></p>
    <el-button :loading="value.checking" @click="$emit('refresh')">{{ t('task.assignmentCompletion.check') }}</el-button>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CompletionRecoveryState } from '@/composables/tasks/useCompletionRecovery'
const props = defineProps<{ value?: CompletionRecoveryState | null; context?: 'batch' }>()
defineEmits<{ (event: 'refresh'): void }>()
const { t } = useI18n()
const messageKey = computed(() => {
  const key = ({ READY: 'ready', PENDING_SYNC: 'pending', SYNC_CONFLICT: 'conflict',
    NOT_ACCEPTED: 'notAccepted', DELIVERY_UNKNOWN: 'unknown' })[props.value?.state ?? 'DELIVERY_UNKNOWN']
  return props.context === 'batch' ? `batchState_${key}` : key
})
</script>

<style scoped>
.completion-status { margin-bottom: 16px; }
.operation-identity { margin: 10px 0; overflow-wrap: anywhere; font-size: 12px; }
</style>
