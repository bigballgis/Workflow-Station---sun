<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'edit' ? t('bi.dataViewAssignment.editAssignment') : t('bi.dataViewAssignment.newAssignment')"
    width="680px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert :title="t('bi.dataViewAssignment.guidance')" type="info" :closable="false" show-icon class="guidance" />
    <div v-if="initializationFailed" class="guidance" role="alert">
      <el-alert :title="t('bi.dataViewAssignment.initializeFailed')" type="error" :closable="false" show-icon />
      <el-button @click="initialize">{{ t('bi.assignment.retry') }}</el-button>
    </div>
    <el-form
      ref="formRef"
      v-loading="initializing"
      :model="form"
      :rules="rules"
      label-position="left"
      label-width="auto"
    >
      <el-form-item
        :label="t('bi.dataViewAssignment.formTarget')"
        prop="functionUnitId"
      >
        <el-select
          v-model="form.functionUnitId"
          filterable
          :loading="functionUnitsLoading"
          :placeholder="t('bi.dataViewAssignment.placeholderTarget')"
          style="width: 100%"
          @change="handleFunctionUnitChange"
        >
          <el-option
            v-for="unit in functionUnits"
            :key="unit.id"
            :label="`${unit.name} (${unit.code})`"
            :value="unit.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        :label="t('bi.dataViewAssignment.formTable')"
        prop="tableId"
      >
        <el-select
          v-model="form.tableId"
          filterable
          @change="handleTableChange"
          :disabled="!form.functionUnitId"
          :loading="tablesLoading"
          :placeholder="t('bi.dataViewAssignment.placeholderTable')"
          style="width: 100%"
        >
          <el-option
            v-for="table in tables"
            :key="table.id"
            :label="tableOptionLabel(table)"
            :value="table.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('bi.dataViewAssignment.formView')" prop="viewId">
        <el-select v-model="form.viewId" filterable :disabled="!form.tableId" :loading="viewsLoading" style="width: 100%">
          <el-option v-for="view in views" :key="view.id" :label="view.viewName" :value="view.id" />
        </el-select>
        <div class="field-help">{{ t('bi.dataViewAssignment.viewHint') }}</div>
      </el-form-item>
      <el-form-item
        v-if="mode === 'create'"
        :label="t('bi.dataViewAssignment.formDashboard')"
        prop="dashboardIds"
      >
        <el-select
          v-model="form.dashboardIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          :max-collapse-tags="2"
          filterable
          :loading="dashboardsLoading"
          :placeholder="t('bi.dataViewAssignment.placeholderDashboard')"
          style="width: 100%"
        >
          <el-option
            v-for="dashboard in dashboards"
            :key="dashboard.id"
            :label="dashboard.dashboardTitle"
            :value="dashboard.id"
          />
        </el-select>
        <div class="field-help">
          {{ t('bi.dataViewAssignment.multiDashboardHint') }}
        </div>
      </el-form-item>

      <el-form-item
        v-else
        :label="t('bi.dataViewAssignment.formDashboard')"
        prop="dashboardId"
      >
        <el-select
          v-model="form.dashboardId"
          filterable
          :loading="dashboardsLoading"
          :placeholder="t('bi.dataViewAssignment.placeholderDashboard')"
          style="width: 100%"
        >
          <el-option
            v-for="dashboard in dashboards"
            :key="dashboard.id"
            :label="dashboard.dashboardTitle"
            :value="dashboard.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('bi.assignment.enabled')">
        <el-switch v-model="form.enabled" />
        <div class="field-help">{{ t('bi.assignment.enabledHint') }}</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!initialized || initializing"
        @click="submit"
      >
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { biManagementApi, type DashboardRegistryResponse, type DataViewAssignmentResponse, type DataViewFunctionUnitOption, type DataViewTableOption, type DataViewOption } from '@/api/biManagement'
import { notifyError, notifySuccess } from '@/utils/notify'

const props = defineProps<{
  modelValue: boolean
  mode: 'create' | 'edit'
  initialRow: DataViewAssignmentResponse | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const initializing = ref(false)
const initialized = ref(false)
const initializationFailed = ref(false)
const submitting = ref(false)
const dashboardsLoading = ref(false)
const functionUnitsLoading = ref(false)
const tablesLoading = ref(false)
const dashboards = ref<DashboardRegistryResponse[]>([])
const functionUnits = ref<DataViewFunctionUnitOption[]>([])
const tables = ref<DataViewTableOption[]>([])
const views = ref<DataViewOption[]>([])
const viewsLoading = ref(false)
let contextSequence = 0
let initializationSequence = 0

const form = reactive({
  dashboardId: '',
  dashboardIds: [] as string[],
  functionUnitId: null as number | null,
  tableId: null as number | null,
  viewId: null as number | null,
  enabled: true,
})

const rules = computed(() => ({
  dashboardId: [{ required: true, message: t('bi.dataViewAssignment.ruleDashboard'), trigger: 'change' }],
  dashboardIds: [{
    type: 'array' as const,
    required: true,
    min: 1,
    message: t('bi.dataViewAssignment.ruleDashboard'),
    trigger: 'change',
  }],
  functionUnitId: [{ required: true, message: t('bi.dataViewAssignment.ruleTarget'), trigger: 'change' }],
  viewId: [{ required: true, message: t('bi.dataViewAssignment.ruleView'), trigger: 'change' }],
  tableId: [{ required: true, message: t('bi.dataViewAssignment.ruleTable'), trigger: 'change' }],
}))

function tableOptionLabel(table: DataViewTableOption) {
  const name = table.tableDisplayName || table.tableName
  return `${name} · ${t(`bi.dataViewAssignment.tableType${table.tableType}`)}`
}

async function loadTables(functionUnitId: number) {
  const seq = ++contextSequence
  tablesLoading.value = true
  try {
    const result = await biManagementApi.dataViewAssignment.listTables(functionUnitId)
    if (seq === contextSequence) tables.value = result
    return seq === contextSequence
  } catch {
    if (seq !== contextSequence) return
    tables.value = []
    notifyError(t('bi.dataViewAssignment.loadTablesFailed'))
    return false
  } finally {
    if (seq === contextSequence) tablesLoading.value = false
  }
}

async function handleFunctionUnitChange(value: number) {
  contextSequence++
  form.tableId = null
  form.viewId = null
  views.value = []
  viewsLoading.value = false
  tables.value = []
  if (value) await loadTables(value)
}

async function handleTableChange(): Promise<void> {
  form.viewId = null
  views.value = []
  await loadViews()
}

async function loadViews(): Promise<boolean> {
  const seq = ++contextSequence
  viewsLoading.value = true
  try {
    if (!form.functionUnitId || !form.tableId) return true
    const result = await biManagementApi.dataViewAssignment.listViews(form.functionUnitId, form.tableId)
    if (seq === contextSequence) views.value = result
    return seq === contextSequence
  } catch {
    if (seq === contextSequence) notifyError(t('bi.dataViewAssignment.loadViewsFailed'))
    return false
  } finally {
    if (seq === contextSequence) viewsLoading.value = false
  }
}

async function initialize() {
  const seq = ++initializationSequence
  contextSequence++
  const row = props.initialRow
  initialized.value = false
  initializationFailed.value = false
  // Clear the previous session before any request can fail or complete late.
  Object.assign(form, {
    dashboardId: row?.dashboardId || '', dashboardIds: [],
    functionUnitId: row?.functionUnitId || null, tableId: row?.tableId || null,
    viewId: row?.viewAvailable ? row.viewId : null, enabled: row?.enabled ?? true,
  })
  dashboards.value = []
  functionUnits.value = []
  tables.value = []
  views.value = []
  initializing.value = true
  dashboardsLoading.value = true
  functionUnitsLoading.value = true
  try {
    const [dashboardPage, units] = await Promise.all([
      biManagementApi.dashboard.list({ status: 'ACTIVE', size: 1000 }),
      biManagementApi.dataViewAssignment.listFunctionUnits(),
    ])
    if (seq !== initializationSequence) return
    dashboards.value = dashboardPage.content || []
    functionUnits.value = units || []

    if (form.functionUnitId) {
      const loadedTables = await loadTables(form.functionUnitId)
      if (seq !== initializationSequence) return
      if (!loadedTables || !await loadViews()) {
        if (seq === initializationSequence) initializationFailed.value = true
        return
      }
    }
    if (seq === initializationSequence) initialized.value = true
  } catch {
    if (seq !== initializationSequence) return
    initializationFailed.value = true
    notifyError(t('bi.dataViewAssignment.initializeFailed'))
  } finally {
    if (seq === initializationSequence) {
      dashboardsLoading.value = false
      functionUnitsLoading.value = false
      initializing.value = false
    }
  }
}

async function submit() {
  if (!initialized.value || initializing.value || submitting.value || !formRef.value) return
  const seq = initializationSequence
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (seq !== initializationSequence || !form.functionUnitId || !form.tableId || !form.viewId) return

  submitting.value = true
  try {
    const payload = {
      dashboardId: form.dashboardId,
      functionUnitId: form.functionUnitId,
      tableId: form.tableId,
      viewId: form.viewId,
      // Omitted on unchanged edits so another administrator's status change survives.
      ...(form.enabled !== props.initialRow?.enabled ? { enabled: form.enabled } : {}),
    }
    if (props.mode === 'edit' && props.initialRow) {
      await biManagementApi.dataViewAssignment.update(props.initialRow.id, payload)
      notifySuccess(t('bi.dataViewAssignment.updateSuccess'))
    } else {
      const created = await biManagementApi.dataViewAssignment.createBatch({
        dashboardIds: form.dashboardIds,
        functionUnitId: form.functionUnitId,
        tableId: form.tableId,
        viewId: form.viewId,
        enabled: form.enabled,
      })
      notifySuccess(t('bi.dataViewAssignment.createSuccess', { count: created.length }))
    }
    if (seq === initializationSequence) {
      emit('update:modelValue', false)
      emit('success')
    }
  } catch {
    notifyError(t('bi.dataViewAssignment.submitFailed'))
  } finally {
    submitting.value = false
  }
}

watch(() => [props.modelValue, props.initialRow] as const, ([open]) => {
  if (open) void initialize()
  else {
    initializationSequence++
    contextSequence++
    initialized.value = false
    initializing.value = false
    tablesLoading.value = false
    viewsLoading.value = false
  }
})
</script>

<style scoped>
.guidance { margin-bottom: 20px; }
:deep(.el-form-item__label) { white-space: nowrap; }
.field-help {
  width: 100%;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}
</style>
