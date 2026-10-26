<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="640px"
    destroy-on-close
    @update:model-value="onDialogVisible"
  >
    <el-alert :title="t('bi.assignment.guidance')" type="info" :closable="false" show-icon class="assignment-guidance" />
    <el-form
      ref="formRef"
      v-loading="dialogInitializing"
      :model="form"
      :rules="formRules"
      label-width="auto"
      label-position="left"
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
          :remote-method="scheduleUserSearch"
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
          <template #footer>
            <el-button v-if="userSearchFailed" text :loading="userSearchLoading" @click="retryUsers">{{ t('bi.assignment.retry') }}</el-button>
            <el-button v-else-if="moreUsers" text :loading="userSearchLoading" @click="loadMoreUsers">{{ t('bi.assignment.loadMore') }}</el-button>
            <span v-else>{{ t('bi.assignment.allLoaded') }}</span>
          </template>
          <template #empty>
            <div class="el-select-dropdown__empty">{{ t(userSearchFailed ? 'bi.assignment.loadTargetsFailed' : 'common.noData') }}</div>
          </template>
        </el-select>
        <div v-if="form.targetType === 'USER'" class="field-help">{{ t('bi.assignment.userHint') }}</div>
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
        <div v-if="form.targetType === 'USER' && userSearchFailed && targetSelectOptions.length === 0" class="field-help" role="alert">
          {{ t('bi.assignment.loadTargetsFailed') }}
          <el-button text :loading="userSearchLoading" @click="retryUsers">{{ t('bi.assignment.retry') }}</el-button>
        </div>
      </el-form-item>
      <div class="field-help">{{ t('bi.assignment.targetHint') }}</div>
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
      <div class="field-help">{{ t('bi.assignment.layoutHint') }}</div>
      <el-form-item :label="t('bi.assignment.enabled')">
        <el-switch v-model="form.enabled" />
        <div class="field-help">{{ t('bi.assignment.enabledHint') }}</div>
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
  onTargetTypeChange, cancelUserSearch, scheduleUserSearch, loadMoreUsers, retryUsers, moreUsers, userSearchFailed,
  handleSubmit: doSubmit, initDialog,
} = useBiAssignmentForm({
  mode: toRef(props, 'mode'),
  initialRow: toRef(props, 'initialRow'),
  onSuccess: () => { emit('update:modelValue', false); emit('success') },
})

const onDialogVisible = (v: boolean) => emit('update:modelValue', v)

// Dialog open → init
watch(() => props.modelValue, async (open) => {
  if (!open) { cancelUserSearch(); return }
  await initDialog()
})

const handleSubmit = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  await doSubmit()
}
</script>

<style scoped>
.assignment-guidance { margin-bottom: 20px; }
.field-help { width: 100%; color: var(--el-text-color-secondary); font-size: 12px; margin: 4px 0 12px; line-height: 1.5; }
:deep(.el-form-item__label) { white-space: nowrap; }
</style>
