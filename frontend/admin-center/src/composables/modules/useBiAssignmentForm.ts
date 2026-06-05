/**
 * BI Dashboard Assignment 表单业务逻辑 composable
 *
 * 封装 AssignmentFormDialog 的所有 API 调用、状态管理和提交逻辑。
 * Dialog 组件仅保留 template + formRef + 调用此 composable。
 */

import { ref, reactive, computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifySuccess, notifyError, notifyWarning } from '@/utils/notify'
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
const ASSIGNMENT_TARGET_TYPES: AssignmentTargetType[] = ['USER', 'ROLE', 'BUSINESS_UNIT']
const LAYOUT_MODES: LayoutMode[] = ['SINGLE', 'MULTI', 'WIDGET']

export interface BiAssignmentFormOptions {
  mode: Ref<'create' | 'edit'>
  initialRow: Ref<DashboardAssignmentResponse | null>
  onSuccess: () => void
}

export function useBiAssignmentForm(options: BiAssignmentFormOptions) {
  const { mode, initialRow, onSuccess } = options
  const { t } = useI18n()

  // ==================== State ====================

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

  const form = reactive({
    dashboardId: '',
    targetType: '' as AssignmentTargetType | '',
    targetId: '',
    layoutMode: 'SINGLE' as LayoutMode,
    displayOrder: 0,
    isDefault: false,
  })

  // ==================== Computed ====================

  const isEdit = computed(() => mode.value === 'edit')

  const dialogTitle = computed(() =>
    isEdit.value ? t('bi.assignment.editAssignment') : t('bi.assignment.newAssignment')
  )

  const targetTypeOptions = computed(() =>
    ASSIGNMENT_TARGET_TYPES.map(value => ({
      value,
      label:
        value === 'USER' ? t('bi.assignment.targetTypeUser')
        : value === 'ROLE' ? t('bi.assignment.targetTypeRole')
        : t('bi.assignment.targetTypeBusinessUnit'),
    }))
  )

  const layoutModeOptions = computed(() =>
    LAYOUT_MODES.map(value => ({
      value,
      label:
        value === 'SINGLE' ? t('bi.assignment.layoutModeSingle')
        : value === 'MULTI' ? t('bi.assignment.layoutModeMulti')
        : t('bi.assignment.layoutModeWidget'),
    }))
  )

  const formRules = computed(() => ({
    dashboardId: [{ required: true, message: t('bi.assignment.ruleSelectDashboard'), trigger: 'change' }],
    targetType: [{ required: true, message: t('bi.assignment.ruleSelectTargetType'), trigger: 'change' }],
    targetId: [{ required: true, message: t('bi.assignment.ruleSelectTarget'), trigger: 'change' }],
    layoutMode: [{ required: true, message: t('bi.assignment.ruleSelectLayoutMode'), trigger: 'change' }],
  }))

  const userOptionLabel = (u: User) => `${u.fullName} (${u.username})`

  // ==================== Form Reset ====================

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

  // ==================== Dashboard Loading ====================

  const loadActiveDashboards = async () => {
    if (dashboardsCache.value !== null) {
      activeDashboards.value = dashboardsCache.value
      return
    }
    dashboardsLoading.value = true
    try {
      const result = await biManagementApi.dashboard.list({
        status: 'ACTIVE', size: DASHBOARD_ACTIVE_PAGE_SIZE,
      })
      dashboardsCache.value = result.content
      activeDashboards.value = result.content
      if (result.totalElements > result.content.length && !dashboardTruncationWarned) {
        dashboardTruncationWarned = true
        notifyWarning(t('bi.assignment.dashboardListTruncated'))
      }
    } catch {
      notifyError(t('bi.assignment.loadDashboardsFailed'))
    } finally {
      dashboardsLoading.value = false
    }
  }

  // ==================== Target Loading ====================

  const loadTargetsRoleBu = async (targetType: AssignmentTargetType) => {
    if (targetType !== 'ROLE' && targetType !== 'BUSINESS_UNIT') return
    const seq = ++targetsSeq.value
    targetsLoading.value = true
    try {
      if (targetType === 'ROLE') {
        const res = await roleApi.list({ size: 9999 }); const roles = res.content
        if (seq !== targetsSeq.value) return
        targetSelectOptions.value = roles.map((r: Role) => ({ id: r.id, label: r.name }))
      } else {
        const units = await businessUnitApi.list()
        if (seq !== targetsSeq.value) return
        targetSelectOptions.value = units.map((bu: BusinessUnit) => ({ id: bu.id, label: bu.name }))
      }
    } catch {
      if (seq === targetsSeq.value) {
        targetSelectOptions.value = []
        notifyError(t('bi.assignment.loadTargetsFailed'))
      }
    } finally {
      if (seq === targetsSeq.value) targetsLoading.value = false
    }
  }

  const loadDefaultUsersForTarget = async () => {
    const seq = ++userSearchSeq.value
    userSearchLoading.value = true
    try {
      const res = await userApi.list({ page: 0, size: 3 })
      if (seq !== userSearchSeq.value) return
      targetSelectOptions.value = (res.content || []).map((u: User) => ({
        id: u.id, label: userOptionLabel(u),
      }))
    } catch {
      if (seq === userSearchSeq.value) {
        targetSelectOptions.value = []
        notifyError(t('bi.assignment.loadTargetsFailed'))
      }
    } finally {
      if (seq === userSearchSeq.value) userSearchLoading.value = false
    }
  }

  const searchUsers = async (query: string) => {
    const seq = ++userSearchSeq.value
    userSearchLoading.value = true
    try {
      const params = query.trim()
        ? { keyword: query.trim(), page: 0, size: 20 }
        : { page: 0, size: 3 }
      const res = await userApi.list(params)
      if (seq !== userSearchSeq.value) return
      targetSelectOptions.value = (res.content || []).map((u: User) => ({
        id: u.id, label: userOptionLabel(u),
      }))
    } catch {
      if (seq === userSearchSeq.value) {
        targetSelectOptions.value = []
        notifyError(t('bi.assignment.loadTargetsFailed'))
      }
    } finally {
      if (seq === userSearchSeq.value) userSearchLoading.value = false
    }
  }

  const onTargetTypeChange = (val: string | AssignmentTargetType | '') => {
    userSearchSeq.value++
    targetsSeq.value++
    form.targetId = ''
    targetSelectOptions.value = []
    if (!val) return
    if (val === 'USER') void loadDefaultUsersForTarget()
    else void loadTargetsRoleBu(val as AssignmentTargetType)
  }

  // ==================== Dialog Init ====================

  const initDialog = async () => {
    dialogInitializing.value = true
    try {
      if (isEdit.value && initialRow.value) {
        const row = initialRow.value
        editingId.value = row.id
        Object.assign(form, {
          dashboardId: row.dashboardId,
          targetType: row.targetType,
          targetId: row.targetId,
          layoutMode: row.layoutMode,
          displayOrder: row.displayOrder,
          isDefault: row.isDefault,
        })
        if (row.targetType === 'USER') {
          targetSelectOptions.value = [{ id: row.targetId, label: row.targetName }]
        } else {
          targetSelectOptions.value = []
          await loadTargetsRoleBu(row.targetType)
        }
      } else {
        resetForm()
        targetSelectOptions.value = []
      }
      await loadActiveDashboards()
    } finally {
      dialogInitializing.value = false
    }
  }

  // ==================== Submit ====================

  const handleSubmit = async () => {
    submitLoading.value = true
    try {
      const data: DashboardAssignmentCreateRequest = {
        dashboardId: form.dashboardId,
        targetType: form.targetType as AssignmentTargetType,
        targetId: form.targetId,
        layoutMode: form.layoutMode,
        displayOrder: form.displayOrder,
        isDefault: form.isDefault,
      }
      if (isEdit.value) {
        await biManagementApi.assignment.update(editingId.value, data)
        notifySuccess(t('bi.assignment.updateSuccess'))
      } else {
        await biManagementApi.assignment.create(data)
        notifySuccess(t('bi.assignment.createSuccess'))
      }
      dashboardsCache.value = null
      onSuccess()
    } catch {
      notifyError(isEdit.value ? t('bi.assignment.submitFailed') : t('bi.assignment.createFailed'))
    } finally {
      submitLoading.value = false
    }
  }

  return {
    form, formRules,
    submitLoading, dashboardsLoading, targetsLoading, userSearchLoading, dialogInitializing,
    activeDashboards, targetSelectOptions,
    isEdit, dialogTitle, targetTypeOptions, layoutModeOptions,
    onTargetTypeChange, searchUsers,
    handleSubmit, initDialog,
  }
}
