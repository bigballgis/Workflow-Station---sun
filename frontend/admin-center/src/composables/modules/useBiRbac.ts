/**
 * BI RBAC Mapping 业务逻辑 composable
 */
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { notifyConfirm, notifyError, notifySuccess } from '@/utils/notify'
import type { FormRules } from 'element-plus'
import {
  biManagementApi,
  type RbacMappingResponse,
  type SupersetRoleResponse,
  type RbacMappingListParams,
  type RoleOptionResponse
} from '@/api/biManagement'

export function useBiRbac() {
  const { t } = useI18n()

  const loading = ref(false)
  const syncing = ref(false)
  const editLoading = ref(false)
  const supersetRolesLoading = ref(false)
  const mappings = ref<RbacMappingResponse[]>([])
  const allSupersetRoles = ref<SupersetRoleResponse[]>([])

  const query = reactive<RbacMappingListParams>({ roleName: '', roleType: undefined })

  // Edit dialog
  const editDialogVisible = ref(false)
  const editForm = reactive({ sysRoleId: '', sysRoleName: '', selectedRoleIds: [] as number[] })

  const activeSupersetRoles = computed(() =>
    allSupersetRoles.value.filter(r => r.status === 'ACTIVE')
  )

  // Create dialog
  const createDialogVisible = ref(false)
  const createLoading = ref(false)
  const unmappedRolesLoading = ref(false)
  const createSupersetRolesLoading = ref(false)
  const unmappedRoles = ref<RoleOptionResponse[]>([])
  const createAllSupersetRoles = ref<SupersetRoleResponse[]>([])
  const createDialogRef = ref<{ createFormRef: { validate: () => Promise<void> } }>()

  const createForm = reactive({ sysRoleId: '', supersetRoleIds: [] as number[] })
  const createFormRules: FormRules = {
    sysRoleId: [{ required: true, message: t('bi.rbac.selectSystemRole'), trigger: 'change' }],
    supersetRoleIds: [{ required: true, type: 'array', min: 1, message: t('bi.rbac.selectSupersetRoles'), trigger: 'change' }]
  }

  const createActiveSupersetRoles = computed(() =>
    createAllSupersetRoles.value.filter(r => r.status === 'ACTIVE')
  )

  const handleSearch = async () => {
    loading.value = true
    try {
      const params: RbacMappingListParams = {
        roleName: query.roleName || undefined,
        roleType: query.roleType || undefined
      }
      mappings.value = await biManagementApi.rbac.listMappings(params)
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_QUERY_FAILED)))
    } finally { loading.value = false }
  }

  const handleReset = () => { query.roleName = ''; query.roleType = undefined; handleSearch() }

  const handleSync = async () => {
    syncing.value = true
    try {
      const result = await biManagementApi.rbac.syncSupersetRoles()
      notifySuccess(t('bi.rbac.syncSuccess', { created: result.created, updated: result.updated, autoInactivated: result.autoInactivated }))
      handleSearch()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_SYNC_FAILED)))
    } finally { syncing.value = false }
  }

  const loadSupersetRoles = async () => {
    supersetRolesLoading.value = true
    try { allSupersetRoles.value = await biManagementApi.rbac.listSupersetRoles() }
    catch { notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_LOAD_SUPERSET_FAILED))) }
    finally { supersetRolesLoading.value = false }
  }

  const showEditDialog = (row: RbacMappingResponse) => {
    editForm.sysRoleId = row.sysRoleId
    editForm.sysRoleName = row.sysRoleName
    editForm.selectedRoleIds = row.supersetRoles ? row.supersetRoles.map(sr => sr.supersetRoleId) : []
    loadSupersetRoles()
    editDialogVisible.value = true
  }

  const handleEditSubmit = async () => {
    editLoading.value = true
    try {
      await biManagementApi.rbac.updateMapping(editForm.sysRoleId, { supersetRoleIds: editForm.selectedRoleIds })
      notifySuccess(t('bi.rbac.updateSuccess'))
      editDialogVisible.value = false
      handleSearch()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_UPDATE_FAILED)))
    } finally { editLoading.value = false }
  }

  const showCreateDialog = () => {
    createForm.sysRoleId = ''; createForm.supersetRoleIds = []
    loadUnmappedRoles(); loadCreateSupersetRoles()
    createDialogVisible.value = true
  }

  const loadUnmappedRoles = async () => {
    unmappedRolesLoading.value = true
    try { unmappedRoles.value = await biManagementApi.rbac.listUnmappedRoles() }
    catch { notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_LOAD_UNMAPPED_FAILED))) }
    finally { unmappedRolesLoading.value = false }
  }

  const loadCreateSupersetRoles = async () => {
    createSupersetRolesLoading.value = true
    try { createAllSupersetRoles.value = await biManagementApi.rbac.listSupersetRoles() }
    catch { notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_LOAD_SUPERSET_FAILED))) }
    finally { createSupersetRolesLoading.value = false }
  }

  const handleCreateSubmit = async () => {
    const formRef = createDialogRef.value?.createFormRef
    if (!formRef) return
    await formRef.validate()
    createLoading.value = true
    try {
      await biManagementApi.rbac.createMapping({ sysRoleId: createForm.sysRoleId, supersetRoleIds: createForm.supersetRoleIds })
      notifySuccess(t('bi.rbac.createSuccess'))
      createDialogVisible.value = false
      handleSearch()
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_CREATE_FAILED)))
    } finally { createLoading.value = false }
  }

  const handleDelete = async (row: RbacMappingResponse) => {
    try {
      await notifyConfirm(
        t('bi.rbac.confirmDeleteMsg', { name: row.sysRoleName }),
        t('bi.rbac.confirmDelete'), { confirmButtonText: t('bi.rbac.delete'), cancelButtonText: t('bi.rbac.cancel'), type: 'warning' }
      )
      await biManagementApi.rbac.deleteMapping(row.sysRoleId)
      notifySuccess(t('bi.rbac.deleteSuccess'))
      handleSearch()
    } catch {
      if (error === 'cancel' || error?.toString?.() === 'cancel') return
      notifyError(t(errorTranslator(AppErrorCode.BI_RBAC_DELETE_FAILED)))
    }
  }

  return {
    loading, syncing, editLoading, supersetRolesLoading, mappings, allSupersetRoles,
    query, editDialogVisible, editForm, activeSupersetRoles,
    createDialogVisible, createLoading, unmappedRolesLoading, createSupersetRolesLoading,
    unmappedRoles, createAllSupersetRoles, createDialogRef, createForm, createFormRules, createActiveSupersetRoles,
    handleSearch, handleReset, handleSync,
    loadSupersetRoles, showEditDialog, handleEditSubmit,
    showCreateDialog, handleCreateSubmit, handleDelete,
  }
}
