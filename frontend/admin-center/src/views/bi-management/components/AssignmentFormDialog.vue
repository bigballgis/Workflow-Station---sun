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
      label-width="120px"
    >
      <el-form-item :label="t('bi.assignment.formDashboard')" prop="dashboardId">
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
      <el-form-item :label="t('bi.assignment.formTargetType')" prop="targetType">
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
      <el-form-item :label="t('bi.assignment.formTarget')" prop="targetId">
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
      <el-form-item :label="t('bi.assignment.formLayoutMode')" prop="layoutMode">
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
      <el-form-item :label="t('bi.assignment.formDisplayOrder')" prop="displayOrder">
        <el-input-number v-model="form.displayOrder" :min="0" :max="9999" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="t('bi.assignment.formDefault')">
        <el-switch v-model="form.isDefault" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onDialogVisible(false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  biManagementApi,
  type DashboardAssignmentResponse,
  type DashboardAssignmentCreateRequest,
  type DashboardRegistryResponse,
  type AssignmentTargetType,
  type LayoutMode
} from '@/api/biManagement'
import { userApi, type User } from '@/api/user'
import { roleApi, type Role } from '@/api/role'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'

const DASHBOARD_ACTIVE_PAGE_SIZE = 1000

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
const submitLoading = ref(false)
const dashboardsLoading = ref(false)
const targetsLoading = ref(false)
const userSearchLoading = ref(false)
const dialogInitializing = ref(false)

const editingId = ref('')
const activeDashboards = ref<DashboardRegistryResponse[]>([])
const dashboardsCache = ref<DashboardRegistryResponse[] | null>(null)
let dashboardTruncationWarned = false

const targetSelectOptions = ref<{ id: string; label: string }[]>([])

const targetsSeq = ref(0)
const userSearchSeq = ref(0)

const isEdit = computed(() => props.mode === 'edit')

const dialogTitle = computed(() =>
  isEdit.value ? t('bi.assignment.editAssignment') : t('bi.assignment.newAssignment')
)

const form = reactive<{
  dashboardId: string
  targetType: AssignmentTargetType | ''
  targetId: string
  layoutMode: LayoutMode
  displayOrder: number
  isDefault: boolean
}>({
  dashboardId: '',
  targetType: '',
  targetId: '',
  layoutMode: 'SINGLE',
  displayOrder: 0,
  isDefault: false
})

const formRules = computed<FormRules>(() => ({
  dashboardId: [{ required: true, message: t('bi.assignment.ruleSelectDashboard'), trigger: 'change' }],
  targetType: [{ required: true, message: t('bi.assignment.ruleSelectTargetType'), trigger: 'change' }],
  targetId: [{ required: true, message: t('bi.assignment.ruleSelectTarget'), trigger: 'change' }],
  layoutMode: [{ required: true, message: t('bi.assignment.ruleSelectLayoutMode'), trigger: 'change' }]
}))

const ASSIGNMENT_TARGET_TYPES: AssignmentTargetType[] = ['USER', 'ROLE', 'BUSINESS_UNIT']

const targetTypeOptions = computed(() =>
  ASSIGNMENT_TARGET_TYPES.map((value) => ({
    value,
    label:
      value === 'USER'
        ? t('bi.assignment.targetTypeUser')
        : value === 'ROLE'
          ? t('bi.assignment.targetTypeRole')
          : t('bi.assignment.targetTypeBusinessUnit')
  }))
)

const LAYOUT_MODES: LayoutMode[] = ['SINGLE', 'MULTI', 'WIDGET']

const layoutModeOptions = computed(() =>
  LAYOUT_MODES.map((value) => ({
    value,
    label:
      value === 'SINGLE'
        ? t('bi.assignment.layoutModeSingle')
        : value === 'MULTI'
          ? t('bi.assignment.layoutModeMulti')
          : t('bi.assignment.layoutModeWidget')
  }))
)

const userOptionLabel = (u: User) => `${u.fullName} (${u.username})`

const onDialogVisible = (v: boolean) => {
  emit('update:modelValue', v)
}

const resetForm = () => {
  form.dashboardId = ''
  form.targetType = ''
  form.targetId = ''
  form.layoutMode = 'SINGLE'
  form.displayOrder = 0
  form.isDefault = false
  editingId.value = ''
  targetSelectOptions.value = []
}

const loadActiveDashboards = async () => {
  if (dashboardsCache.value !== null) {
    activeDashboards.value = dashboardsCache.value
    return
  }
  dashboardsLoading.value = true
  try {
    const result = await biManagementApi.dashboard.list({
      status: 'ACTIVE',
      size: DASHBOARD_ACTIVE_PAGE_SIZE
    })
    dashboardsCache.value = result.content
    activeDashboards.value = result.content
    if (result.totalElements > result.content.length && !dashboardTruncationWarned) {
      dashboardTruncationWarned = true
      ElMessage.warning(t('bi.assignment.dashboardListTruncated'))
    }
  } catch {
    ElMessage.error(t('bi.assignment.loadDashboardsFailed'))
  } finally {
    dashboardsLoading.value = false
  }
}

const loadTargetsRoleBu = async (targetType: AssignmentTargetType) => {
  if (targetType !== 'ROLE' && targetType !== 'BUSINESS_UNIT') {
    return
  }
  const seq = ++targetsSeq.value
  targetsLoading.value = true
  try {
    if (targetType === 'ROLE') {
      const roles = await roleApi.list()
      if (seq !== targetsSeq.value) return
      targetSelectOptions.value = roles.map((r: Role) => ({
        id: r.id,
        label: r.name
      }))
    } else {
      const units = await businessUnitApi.list()
      if (seq !== targetsSeq.value) return
      targetSelectOptions.value = units.map((bu: BusinessUnit) => ({
        id: bu.id,
        label: bu.name
      }))
    }
  } catch {
    if (seq === targetsSeq.value) {
      targetSelectOptions.value = []
      ElMessage.error(t('bi.assignment.loadTargetsFailed'))
    }
  } finally {
    if (seq === targetsSeq.value) {
      targetsLoading.value = false
    }
  }
}

const loadDefaultUsersForTarget = async () => {
  const seq = ++userSearchSeq.value
  userSearchLoading.value = true
  try {
    const res = await userApi.list({ page: 0, size: 3 })
    if (seq !== userSearchSeq.value) return
    targetSelectOptions.value = (res.content || []).map((u: User) => ({
      id: u.id,
      label: userOptionLabel(u)
    }))
  } catch {
    if (seq === userSearchSeq.value) {
      targetSelectOptions.value = []
      ElMessage.error(t('bi.assignment.loadTargetsFailed'))
    }
  } finally {
    if (seq === userSearchSeq.value) {
      userSearchLoading.value = false
    }
  }
}

const searchUsers = async (query: string) => {
  const seq = ++userSearchSeq.value
  userSearchLoading.value = true
  try {
    if (!query.trim()) {
      const res = await userApi.list({ page: 0, size: 3 })
      if (seq !== userSearchSeq.value) return
      targetSelectOptions.value = (res.content || []).map((u: User) => ({
        id: u.id,
        label: userOptionLabel(u)
      }))
      return
    }
    const res = await userApi.list({ keyword: query.trim(), page: 0, size: 20 })
    if (seq !== userSearchSeq.value) return
    targetSelectOptions.value = (res.content || []).map((u: User) => ({
      id: u.id,
      label: userOptionLabel(u)
    }))
  } catch {
    if (seq === userSearchSeq.value) {
      targetSelectOptions.value = []
      ElMessage.error(t('bi.assignment.loadTargetsFailed'))
    }
  } finally {
    if (seq === userSearchSeq.value) {
      userSearchLoading.value = false
    }
  }
}

const onTargetTypeChange = (val: string | AssignmentTargetType | '') => {
  userSearchSeq.value++
  targetsSeq.value++
  form.targetId = ''
  targetSelectOptions.value = []
  if (!val) return
  if (val === 'USER') {
    void loadDefaultUsersForTarget()
  } else {
    void loadTargetsRoleBu(val as AssignmentTargetType)
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    dialogInitializing.value = true
    try {
      if (props.mode === 'edit' && props.initialRow) {
        const row = props.initialRow
        editingId.value = row.id
        Object.assign(form, {
          dashboardId: row.dashboardId,
          targetType: row.targetType,
          targetId: row.targetId,
          layoutMode: row.layoutMode,
          displayOrder: row.displayOrder,
          isDefault: row.isDefault
        })
        if (row.targetType === 'USER') {
          targetSelectOptions.value = [{ id: row.targetId, label: row.targetName }]
        } else {
          targetSelectOptions.value = []
          await loadTargetsRoleBu(row.targetType)
        }
        await loadActiveDashboards()
      } else {
        resetForm()
        targetSelectOptions.value = []
        await loadActiveDashboards()
      }
    } finally {
      dialogInitializing.value = false
    }
  }
)

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    const data: DashboardAssignmentCreateRequest = {
      dashboardId: form.dashboardId,
      targetType: form.targetType as AssignmentTargetType,
      targetId: form.targetId,
      layoutMode: form.layoutMode,
      displayOrder: form.displayOrder,
      isDefault: form.isDefault
    }

    if (isEdit.value) {
      await biManagementApi.assignment.update(editingId.value, data)
      ElMessage.success(t('bi.assignment.updateSuccess'))
    } else {
      await biManagementApi.assignment.create(data)
      ElMessage.success(t('bi.assignment.createSuccess'))
    }
    dashboardsCache.value = null
    emit('update:modelValue', false)
    emit('success')
  } catch {
    ElMessage.error(isEdit.value ? t('bi.assignment.submitFailed') : t('bi.assignment.createFailed'))
  } finally {
    submitLoading.value = false
  }
}
</script>
