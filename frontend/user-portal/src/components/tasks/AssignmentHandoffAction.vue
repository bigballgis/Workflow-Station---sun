<template>
  <el-button v-if="failed" size="small" :disabled="disabled" @click="retry++">{{ t('task.assignmentActions.checkUnavailable') }}</el-button>
  <el-button v-if="targets.length" size="small" data-test="assignment-handoff" :disabled="disabled" @click="visible = true">
    {{ t('task.assignmentActions.handoff') }}
  </el-button>
  <el-dialog v-model="visible" :title="t('task.assignmentActions.handoff')" width="540px" :close-on-click-modal="!submitting" :show-close="!submitting">
    <el-alert :title="t('task.assignmentActions.handoffHint')" type="info" :closable="false" />
    <el-form label-width="150px" label-position="left" class="handoff-form">
      <el-form-item :label="t('task.assignmentActions.recipient')">
        <el-select v-model="target" filterable :placeholder="t('task.selectUser')" :disabled="submitting" style="width: 100%" data-test="handoff-target">
          <el-option v-for="user in targets" :key="user.id" :value="user.id" :label="user.label" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!target || disabled" data-test="handoff-confirm" @click="confirm">{{ t('task.assignmentActions.handoff') }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { actionAvailability, handoffTask, type ActionAvailability } from '@/api/assignmentRuntimeActions'
const props = defineProps<{ taskId?: string; enabled: boolean; disabled?: boolean }>()
const { t } = useI18n()
const router = useRouter()
const targets = ref<ActionAvailability['targets']>([])
const visible = ref(false), submitting = ref(false), target = ref(''), failed = ref(false), retry = ref(0)
let epoch = 0
onBeforeUnmount(() => { epoch++ })
watch(() => [props.taskId, props.enabled, retry.value] as const, async ([task, enabled]) => {
  const requested = ++epoch
  targets.value = []; target.value = ''; visible.value = false; failed.value = false
  if (!task || !enabled) return
  try {
    const value = await actionAvailability(task, 'handoff')
    if (requested === epoch && value.allowed) targets.value = value.targets
  } catch {
    if (requested === epoch) failed.value = true
  }
}, { immediate: true })
async function confirm() {
  if (!props.taskId || !target.value || props.disabled || submitting.value) return
  submitting.value = true
  const task = props.taskId
  const requested = epoch
  try {
    await handoffTask(task, target.value)
    if (requested !== epoch || task !== props.taskId) return
    ElMessage.success(t('task.assignmentActions.handoffDone'))
    visible.value = false
    await router.push('/tasks')
  } catch {
    if (requested !== epoch) return
    targets.value = []; visible.value = false; failed.value = true
  } finally { submitting.value = false }
}
</script>
<style scoped>
.handoff-form { margin-top: 20px; }
:deep(.el-form-item__label) { white-space: nowrap; }
</style>
