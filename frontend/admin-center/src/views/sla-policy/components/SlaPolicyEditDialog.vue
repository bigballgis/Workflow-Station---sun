<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('sla.editTitle')"
    width="520px"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <el-form
      ref="formRef"
      class="sla-edit-form"
      :model="form"
      :rules="rules"
      label-position="left"
      label-width="150px"
    >
      <el-form-item :label="t('sla.colFunctionUnitName')">
        <span>{{ form.functionUnitName || form.functionUnitCode }}</span>
      </el-form-item>
      <el-form-item :label="t('sla.currentLeadTime')">
        <span>{{ form.currentDays == null ? t('sla.notSet') : t('sla.days', { n: form.currentDays }) }}</span>
      </el-form-item>
      <el-form-item
        :label="t('sla.colLeadTimeDays')"
        prop="leadTimeDays"
      >
        <el-input-number
          v-model="form.leadTimeDays"
          :min="SLA_MIN_DAYS"
          :max="SLA_MAX_DAYS"
          :step="1"
          :precision="0"
          step-strictly
          controls-position="right"
        />
      </el-form-item>
      <el-form-item
        :label="t('sla.changeReason')"
        prop="changeReason"
      >
        <el-input
          v-model="form.changeReason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('sla.recalcNotice')"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="saving"
        @click="handleSubmit"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { SLA_MAX_DAYS, SLA_MIN_DAYS } from '@/composables/modules/useSlaPolicies'

const props = defineProps<{
  modelValue: boolean
  saving: boolean
  form: {
    functionUnitCode: string
    functionUnitName: string
    currentDays: number | null
    leadTimeDays: number | undefined
    changeReason: string
  }
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()

const rules: FormRules = {
  leadTimeDays: [{
    validator: (_rule, value, callback) => {
      if (value == null || value === '') {
        callback(new Error(t('sla.leadTimeRequired')))
      } else if (!Number.isInteger(value) || value < SLA_MIN_DAYS || value > SLA_MAX_DAYS) {
        callback(new Error(t('sla.leadTimeRange', { min: SLA_MIN_DAYS, max: SLA_MAX_DAYS })))
      } else {
        callback()
      }
    },
    trigger: ['blur', 'change'],
  }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid && props.form.leadTimeDays != null) {
    emit('submit')
  }
}
</script>

<style scoped>
.sla-edit-form :deep(.el-form-item__label) {
  white-space: nowrap;
}
</style>
