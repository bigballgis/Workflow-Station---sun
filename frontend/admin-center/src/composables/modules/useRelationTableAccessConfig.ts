/**
 * Relation Table Access Config 业务逻辑 composable
 */
import { ref, computed, type Ref } from 'vue'
import { notifySuccess, notifyError } from '@/utils/notify'
import { relationTableStructureApi, type RelationTableAccess } from '@/api/relationTable'
import { roleApi, type Role } from '@/api/role'
import { businessUnitApi, type BusinessUnit } from '@/api/businessUnit'
import { formatDate as fmtDate, roleTypeDisplayLabel, roleTagType } from '@/utils/format'

export function useRelationTableAccessConfig(entityId: Ref<number | undefined>) {
  const loading = ref(false)
  const accessList = ref<RelationTableAccess[]>([])
  const allRoles = ref<Role[]>([])
  const rolesLoading = ref(false)
  const allRolesMap = computed(() => {
    const m = new Map<string, Role>()
    allRoles.value.forEach(r => m.set(r.id, r))
    return m
  })

  const showAddRole = ref(false)
  const adding = ref(false)
  const addRoleTab = ref<'bu' | 'system'>('bu')
  const selectedSystemRoleIds = ref<string[]>([])
  const selectedBuId = ref<string | null>(null)
  const selectedBuRoleId = ref('')
  const buCascaderOptions = ref<BusinessUnit[]>([])
  const buRoles = ref<Role[]>([])
  const buRolesLoading = ref(false)

  const buCascaderProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }

  const assignedIds = computed(() => new Set(accessList.value.map(a => a.targetId)))
  const availableSystemRoles = computed(() =>
    allRoles.value.filter(r => r.status === 'ACTIVE' && r.type !== 'BU_BOUNDED' && !assignedIds.value.has(r.id))
  )
  const availableBuRoles = computed(() => buRoles.value.filter(r => !assignedIds.value.has(r.id)))

  const resolveRoleName = (roleId: string, fallbackName?: string | null) =>
    allRolesMap.value.get(roleId)?.name ?? fallbackName ?? roleId
  const resolveRoleTagType = (roleId: string) => {
    const t = allRolesMap.value.get(roleId)?.type
    return t ? roleTagType(t) : 'info'
  }
  const resolveRoleTypeLabel = (roleId: string) => {
    const t = allRolesMap.value.get(roleId)?.type
    return t ? roleTypeDisplayLabel(t) : '—'
  }
  const formatDate = (d: string | undefined) => (d ? fmtDate(d) : '')

  const loadAccessList = async () => {
    if (!entityId.value) return
    loading.value = true
    try {
      accessList.value = await relationTableStructureApi.getAccessConfig(entityId.value)
    } catch {
      /* silent */
    } finally {
      loading.value = false
    }
  }

  const loadAllRoles = async () => {
    if (allRoles.value.length > 0) return
    rolesLoading.value = true
    try {
      const r = await roleApi.list({ size: 9999 })
      allRoles.value = r.content ?? []
    } catch {
      /* silent */
    } finally {
      rolesLoading.value = false
    }
  }

  const fetchBuTree = async () => {
    if (buCascaderOptions.value.length > 0) return
    try {
      buCascaderOptions.value = await businessUnitApi.getTree()
    } catch {
      /* silent */
    }
  }

  const resetAddForm = () => {
    addRoleTab.value = 'bu'
    selectedSystemRoleIds.value = []
    selectedBuId.value = null
    selectedBuRoleId.value = ''
    buRoles.value = []
  }

  const openAddDialog = async () => {
    resetAddForm()
    showAddRole.value = true
    await loadAllRoles()
    selectedSystemRoleIds.value = availableSystemRoles.value.map(r => r.id)
    fetchBuTree()
  }

  const resolveCascaderBuId = (value: import('element-plus').CascaderValue | null | undefined): string | null => {
    if (value == null || value === '') return null
    if (Array.isArray(value)) {
      const last = value[value.length - 1]
      return last == null || last === '' ? null : String(last)
    }
    return String(value)
  }

  const handleBuChange = async (value: import('element-plus').CascaderValue | null | undefined) => {
    const buId = resolveCascaderBuId(value)
    selectedBuId.value = buId
    selectedBuRoleId.value = ''
    buRoles.value = []
    if (!buId) return
    buRolesLoading.value = true
    try {
      buRoles.value = await businessUnitApi.getBoundRoles(buId)
    } catch {
      /* silent */
    } finally {
      buRolesLoading.value = false
    }
  }

  const handleAddRole = async () => {
    if (!entityId.value) return
    if (addRoleTab.value === 'system') {
      const ids = selectedSystemRoleIds.value.filter(id => !assignedIds.value.has(id))
      if (!ids.length) return
      adding.value = true
      try {
        await Promise.all(ids.map(id => relationTableStructureApi.addAccess(entityId.value!, id)))
        notifySuccess(`Added ${ids.length} role(s)`)
        showAddRole.value = false
        await loadAccessList()
      } catch (e) {
        notifyError((e as { response?: { data?: { error?: { message?: string }; message?: string } } })?.response?.data?.error?.message
          || (e as { response?: { data?: { message?: string } } })?.response?.data?.message
          || 'Failed')
      } finally {
        adding.value = false
      }
    } else {
      const roleId = selectedBuRoleId.value
      if (!roleId) return
      adding.value = true
      try {
        await relationTableStructureApi.addAccess(entityId.value, roleId)
        notifySuccess('Access added')
        showAddRole.value = false
        await loadAccessList()
      } catch (e) {
        notifyError((e as { response?: { data?: { error?: { message?: string }; message?: string } } })?.response?.data?.error?.message
          || (e as { response?: { data?: { message?: string } } })?.response?.data?.message
          || 'Failed')
      } finally {
        adding.value = false
      }
    }
  }

  const handleRemove = async (access: RelationTableAccess) => {
    if (!entityId.value) return
    try {
      await relationTableStructureApi.removeAccess(entityId.value, access.id)
      notifySuccess('Removed')
      await loadAccessList()
    } catch {
      /* silent */
    }
  }

  return {
    loading,
    accessList,
    allRoles,
    rolesLoading,
    allRolesMap,
    showAddRole,
    adding,
    addRoleTab,
    selectedSystemRoleIds,
    selectedBuId,
    selectedBuRoleId,
    buCascaderOptions,
    buRoles,
    buRolesLoading,
    buCascaderProps,
    assignedIds,
    availableSystemRoles,
    availableBuRoles,
    roleTypeDisplayLabel,
    resolveRoleName,
    resolveRoleTagType,
    resolveRoleTypeLabel,
    formatDate,
    loadAccessList,
    loadAllRoles,
    resetAddForm,
    openAddDialog,
    handleBuChange,
    handleAddRole,
    handleRemove,
  }
}
