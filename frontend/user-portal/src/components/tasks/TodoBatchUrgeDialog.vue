<template>
  <el-dialog
    v-model="visible"
    :title="t('task.batchUrge')"
    width="500px"
    class="task-action-form"
    @open="reason = ''"
  >
    <el-form label-width="100px">
      <el-form-item :label="t('common.reason')">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        data-test="todo-batch-urge-confirm"
        @click="submit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { batchUrgeTasks } from '@/api/task'

/** To Do「Batch Urge」: one shared message for the selected tasks. */
const props = defineProps<{
  modelValue: boolean
  taskIds: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  urged: []
}>()

const { t } = useI18n()
const reason = ref('')
const submitting = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

async function submit(): Promise<void> {
  submitting.value = true
  try {
    await batchUrgeTasks(props.taskIds, reason.value)
    ElMessage.success(t('common.success'))
    visible.value = false
    emit('urged')
  } catch (error) {
    const msg =
      (error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
      ?? (error as { message?: string })?.message
      ?? t('common.error')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss">
.task-action-form .el-form-item__label {
  white-space: nowrap;
}
</style>
