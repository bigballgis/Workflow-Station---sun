<template>
  <el-dialog
    :model-value="visible"
    :title="t('delegation.create')"
    width="500px"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-form
      :model="form"
      label-width="auto"
      label-position="left"
    >
      <el-form-item :label="t('delegation.delegateTo')">
        <el-select
          v-model="form.delegateId"
          filterable
          :placeholder="t('delegation.selectDelegate')"
          style="width: 100%;"
        >
          <el-option
            label="Li Si"
            value="user_2"
          />
          <el-option
            label="Wang Wu"
            value="user_3"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('delegation.delegationType')">
        <el-select
          v-model="form.delegationType"
          style="width: 100%;"
        >
          <el-option
            value="ALL"
            :label="t('delegation.all')"
          />
          <el-option
            value="PARTIAL"
            :label="t('delegation.partial')"
          />
          <el-option
            value="TEMPORARY"
            :label="t('delegation.temporary')"
          />
          <el-option
            value="URGENT"
            :label="t('delegation.urgent')"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('delegation.startTime')">
        <el-date-picker
          v-model="form.startTime"
          type="datetime"
          style="width: 100%;"
        />
      </el-form-item>
      <el-form-item :label="t('delegation.endTime')">
        <el-date-picker
          v-model="form.endTime"
          type="datetime"
          style="width: 100%;"
        />
      </el-form-item>
      <el-form-item :label="t('delegation.reason')">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="submit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { createDelegationRule } from '@/api/delegation'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  created: []
}>()

const { t } = useI18n()
const submitting = ref(false)

const form = reactive({
  delegateId: '',
  delegationType: 'ALL',
  startTime: null as Date | null,
  endTime: null as Date | null,
  reason: '',
})

function resetForm() {
  form.delegateId = ''
  form.delegationType = 'ALL'
  form.startTime = null
  form.endTime = null
  form.reason = ''
}

watch(
  () => props.visible,
  (open) => {
    if (open) resetForm()
  },
)

async function submit() {
  if (!form.delegateId) {
    ElMessage.warning(t('delegation.selectDelegate'))
    return
  }
  submitting.value = true
  try {
    await createDelegationRule({
      delegateId: form.delegateId,
      delegationType: form.delegationType,
      startTime: form.startTime ? form.startTime.toISOString() : undefined,
      endTime: form.endTime ? form.endTime.toISOString() : undefined,
      reason: form.reason || undefined,
    })
    ElMessage.success(t('delegation.createSuccess'))
    emit('update:visible', false)
    emit('created')
  } catch {
    // request interceptor already surfaces API errors
  } finally {
    submitting.value = false
  }
}
</script>
