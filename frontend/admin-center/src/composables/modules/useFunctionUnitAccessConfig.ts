/**
 * Function Unit Access Config 业务逻辑 composable
 */
import { ref, computed, type Ref } from 'vue'
import type { CascaderValue } from 'element-plus'
import { notifySuccess, notifyError } from '@/utils/notify'
import { functionUnitApi, type FunctionUnitAccess } from '@/api/functionUnit'
import { roleApi, type Role } from '@/api/role'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'
import { formatDate as fmtDate, roleTypeDisplayLabel, roleTagType } from '@/utils/format'

export function useFunctionUnitAccessConfig(functionUnitId: Ref<string | undefined>) {
  const loading = ref(false)
  const accessList = ref<FunctionUnitAccess[]>([])
  const allRoles = ref<Role[]>([])
  const rolesLoading = ref(false)
  const allRolesMap = computed(() => { const m = new Map<string, Role>(); allRoles.value.forEach(r => m.set(r.id, r)); return m })

  const showAddRole = ref(false)
  const addLoading = ref(false)
  const addRoleTab = ref<'system' | 'bu'>('bu')
  const selectedSystemRoleIds = ref<string[]>([])
  const selectedBuId = ref<string | null>(null)
  const selectedBuRoleId = ref('')
  const buCascaderOptions = ref<BusinessUnit[]>([])
  const buRoles = ref<Role[]>([])
  const buRolesLoading = ref(false)

  const buCascaderProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }

  const assignedIds = computed(() => new Set(accessList.value.map(a => a.targetId || a.roleId)))
  const availableSystemRoles = computed(() => allRoles.value.filter(r => r.status === 'ACTIVE' && r.type !== 'BU_BOUNDED' && !assignedIds.value.has(r.id)))
  const availableBuRoles = computed(() => buRoles.value.filter(r => !assignedIds.value.has(r.id)))

  const resolveRoleName = (roleId: string, fallbackName?: string | null) => {
    const fromApi = fallbackName?.trim()
    if (fromApi) return fromApi
    return allRolesMap.value.get(roleId)?.name ?? roleId
  }
  const resolveRoleTypeLabel = (roleId: string) => { const t = allRolesMap.value.get(roleId)?.type; return t ? (roleTypeDisplayLabel(t)) : '—' }
  const resolveRoleTagType = (roleId: string) => { const rt = allRolesMap.value.get(roleId)?.type; return rt ? roleTagType(rt) : 'info' }
  const formatDate = (d: string | undefined) => d ? fmtDate(d) : ''

  const fetchAccessConfig = async () => {
    if (!functionUnitId.value) return
    loading.value = true
    try { accessList.value = await functionUnitApi.getAccessConfigs(functionUnitId.value) }
    catch { /* silent */ } finally { loading.value = false }
  }

  const fetchAllRoles = async () => {
    if (allRoles.value.length > 0) return
    rolesLoading.value = true
    try {
      const r = await roleApi.list({ size: 9999 })
      allRoles.value = r.content ?? []
    } catch { /* silent */ } finally { rolesLoading.value = false }
  }

  const fetchBuTree = async () => {
    if (buCascaderOptions.value.length > 0) return
    try { buCascaderOptions.value = await businessUnitApi.getTree() } catch { /* silent */ }
  }

  const resetAddForm = () => { addRoleTab.value = 'bu'; selectedSystemRoleIds.value = []; selectedBuId.value = null; selectedBuRoleId.value = ''; buRoles.value = [] }

  const openAddDialog = async () => {
    resetAddForm(); showAddRole.value = true
    await fetchAllRoles(); selectedSystemRoleIds.value = availableSystemRoles.value.map(r => r.id)
    fetchBuTree()
  }

  const resolveCascaderBuId = (value: CascaderValue | null | undefined): string | null => {
    if (value == null || value === '') return null
    if (Array.isArray(value)) {
      const last = value[value.length - 1]
      return last == null || last === '' ? null : String(last)
    }
    return String(value)
  }

  const handleBuChange = async (value: CascaderValue | null | undefined) => {
    const buId = resolveCascaderBuId(value)
    selectedBuId.value = buId
    selectedBuRoleId.value = ''; buRoles.value = []
    if (!buId) return
    buRolesLoading.value = true
    try { buRoles.value = await businessUnitApi.getBoundRoles(buId) } catch { /* silent */ }
    finally { buRolesLoading.value = false }
  }

  const handleAddRole = async () => {
    if (!functionUnitId.value) return
    if (addRoleTab.value === 'system') {
      const ids = selectedSystemRoleIds.value.filter(id => !assignedIds.value.has(id))
      if (!ids.length) return
      addLoading.value = true
      try { await Promise.all(ids.map(id => functionUnitApi.addAccessConfig(functionUnitId.value!, { roleId: id }))); notifySuccess(`Added ${ids.length} role(s)`); showAddRole.value = false; await fetchAccessConfig() }
      catch (e) { notifyError((e as any)?.response?.data?.error?.message || (e as any)?.response?.data?.message || 'Failed') }
      finally { addLoading.value = false }
    } else {
      const roleId = selectedBuRoleId.value
      if (!roleId) return
      addLoading.value = true
      try { await functionUnitApi.addAccessConfig(functionUnitId.value, { roleId }); notifySuccess('Access added'); showAddRole.value = false; await fetchAccessConfig() }
      catch (e) { notifyError((e as any)?.response?.data?.error?.message || (e as any)?.response?.data?.message || 'Failed') }
      finally { addLoading.value = false }
    }
  }

  const handleRemove = async (access: FunctionUnitAccess) => {
    if (!functionUnitId.value) return
    try { await functionUnitApi.removeAccessConfig(functionUnitId.value, access.id); notifySuccess('Access removed'); await fetchAccessConfig() }
    catch { /* silent */ }
  }

  return { loading, accessList, allRoles, rolesLoading, allRolesMap,
    showAddRole, addLoading, addRoleTab, selectedSystemRoleIds, selectedBuId, selectedBuRoleId,
    buCascaderOptions, buRoles, buRolesLoading, buCascaderProps,
    assignedIds, availableSystemRoles, availableBuRoles,
    resolveRoleName, resolveRoleTypeLabel, resolveRoleTagType, formatDate,
    fetchAccessConfig, fetchAllRoles, resetAddForm, openAddDialog, handleBuChange, handleAddRole, handleRemove,
  }
}
