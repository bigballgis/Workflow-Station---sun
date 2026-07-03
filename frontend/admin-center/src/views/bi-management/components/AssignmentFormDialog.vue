<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="520px"
    destroy-on-close
    @update:model-value="onDialogVisible"
  >
    <el-form
      ref="formRef"
      v-loading="dialogInitializing"
      :model="form"
      :rules="formRules"
      label-width="auto"
    >
      <el-form-item
        :label="t('bi.assignment.formDashboard')"
        prop="dashboardId"
      >
        <el-select
          v-model="form.dashboardId"
          :placeholder="t('bi.assignment.placeholderSelectDashboard')"
          filterable
          :disabled="isEdit"
          style="width: 100%"
          :loading="dashboardsLoading"
        >
          <el-option
            v-for="d in activeDashboards"
            :key="d.id"
            :label="d.dashboardTitle"
            :value="d.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="t('bi.assignment.formTargetType')"
        prop="targetType"
      >
        <el-select
          v-model="form.targetType"
          :placeholder="t('bi.assignment.placeholderSelectTargetType')"
          :disabled="isEdit"
          style="width: 100%"
          @change="onTargetTypeChange"
        >
          <el-option
            v-for="opt in targetTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="t('bi.assignment.formTarget')"
        prop="targetId"
      >
        <el-select
          v-if="form.targetType === 'USER'"
          v-model="form.targetId"
          :placeholder="t('bi.assignment.placeholderSelectTarget')"
          filterable
          remote
          :remote-method="searchUsers"
          :disabled="isEdit"
          style="width: 100%"
          :loading="userSearchLoading"
        >
          <el-option
            v-for="o in targetSelectOptions"
            :key="o.id"
            :label="o.label"
            :value="o.id"
          />
        </el-select>
        <el-select
          v-else
          v-model="form.targetId"
          :placeholder="t('bi.assignment.placeholderSelectTarget')"
          filterable
          :disabled="isEdit"
          style="width: 100%"
          :loading="targetsLoading"
        >
          <el-option
            v-for="o in targetSelectOptions"
            :key="o.id"
            :label="o.label"
            :value="o.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="t('bi.assignment.formLayoutMode')"
        prop="layoutMode"
      >
        <el-select
          v-model="form.layoutMode"
          :placeholder="t('bi.assignment.placeholderSelectLayoutMode')"
          style="width: 100%"
        >
          <el-option
            v-for="opt in layoutModeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        :label="t('bi.assignment.formDisplayOrder')"
        prop="displayOrder"
      >
        <el-input-number
          v-model="form.displayOrder"
          :min="0"
          :max="9999"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item :label="t('bi.assignment.formDefault')">
        <el-switch v-model="form.isDefault" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onDialogVisible(false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitLoading"
        @click="handleSubmit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useBiAssignmentForm } from '@/composables/modules/useBiAssignmentForm'
import type { DashboardAssignmentResponse } from '@/api/biManagement'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  initialRow: DashboardAssignmentResponse | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  form, formRules,
  submitLoading, dashboardsLoading, targetsLoading, userSearchLoading, dialogInitializing,
  activeDashboards, targetSelectOptions,
  isEdit, dialogTitle, targetTypeOptions, layoutModeOptions,
  onTargetTypeChange, searchUsers,
  handleSubmit: doSubmit, initDialog,
} = useBiAssignmentForm({
  mode: toRef(props, 'mode'),
  initialRow: toRef(props, 'initialRow'),
  onSuccess: () => { emit('update:modelValue', false); emit('success') },
})

const onDialogVisible = (v: boolean) => emit('update:modelValue', v)

// Dialog open → init
watch(() => props.modelValue, async (open) => {
  if (!open) return
  await initDialog()
})

const handleSubmit = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  await doSubmit()
}
</script>
