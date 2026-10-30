<template>
  <el-button v-if="failed && !operation" :disabled="submitting" @click="retry++">{{ t('task.assignmentActions.checkUnavailable') }}</el-button>
  <el-button v-if="allowed || operation" :loading="submitting" :disabled="state === 'SYNC_CONFLICT'" data-test="assignment-reopen" @click="run">
    {{ t(operation ? 'task.assignmentActions.reopenRetry' : 'task.assignmentActions.reopen') }}
  </el-button>
  <el-alert v-if="operation && state" :title="t(`task.assignmentActions.${state === 'SYNC_CONFLICT' ? 'reopenConflict' : 'reopenPending'}`)" type="warning" :closable="false" />
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { actionAvailability, reopenProcess } from '@/api/assignmentRuntimeActions'
import { savedReopenOperation, reopenOperation, forgetReopenOperation } from '@/utils/reopenOperation'
const props = defineProps<{ processId: string; status?: string }>()
const { t } = useI18n(), router = useRouter()
const allowed = ref(false), submitting = ref(false), operation = ref<string | null>(null), state = ref(''), failed = ref(false), retry = ref(0)
let epoch = 0
onBeforeUnmount(() => { epoch++ })
watch(() => [props.processId, props.status, retry.value] as const, async ([source, status]) => {
  const requested = ++epoch
  allowed.value = false; state.value = ''; failed.value = false; operation.value = null
  try { operation.value = savedReopenOperation(source) }
  catch { failed.value = true; return }
  if (operation.value) state.value = 'UNKNOWN'
  if (!['COMPLETED', 'REJECTED', 'WITHDRAWN', 'CANCELLED'].includes(status ?? '')) return
  try {
    const value = await actionAvailability(source, 'reopen')
    if (requested === epoch) allowed.value = value.allowed
  } catch {
    if (requested === epoch) failed.value = true
  }
}, { immediate: true })
async function run() {
  if (submitting.value || (!allowed.value && !operation.value)) return
  const source = props.processId, requested = epoch
  if (!operation.value) {
    try { await ElMessageBox.confirm(t('task.assignmentActions.reopenHint'), t('task.assignmentActions.reopen'), {
      type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'),
    }) }
    catch { return }
  }
  if (requested !== epoch || source !== props.processId || submitting.value || (!allowed.value && !operation.value)) return
  const id = operation.value ?? reopenOperation(source)
  operation.value = id; submitting.value = true
  try {
    const value = await reopenProcess(source, id)
    if (requested !== epoch || source !== props.processId) return
    state.value = value.synchronizationState
    if (value.synchronizationState === 'READY') {
      forgetReopenOperation(source); operation.value = null
      allowed.value = false
      await router.push({ name: 'ApplicationDetail', params: { id: value.processInstanceId } })
      router.go(0) // ApplicationDetail captures its source id at setup; reload the newly selected round.
    }
  } catch {
    if (requested !== epoch) return
    state.value = 'UNKNOWN'
    ElMessage.warning(t('task.assignmentActions.reopenPending'))
  } finally { submitting.value = false }
}
</script>
