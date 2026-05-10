/**
 * Relation Table Access Config 业务逻辑 composable
 */
import { ref, computed, type Ref } from 'vue'
import { notifySuccess, notifyError } from '@/utils/notify'
import { relationTableStructureApi, type RelationTableAccess } from '@/api/relationTable'
import { roleApi, type Role } from '@/api/role'
import { formatDate as fmtDate, roleTypeDisplayLabel, roleTagType } from '@/utils/format'

export function useRelationTableAccessConfig(entityId: Ref<number | undefined>) {
  const loading = ref(false)
  const accessList = ref<RelationTableAccess[]>([])
  const allRoles = ref<Role[]>([])
  const allRolesMap = computed(() => { const m = new Map<string, Role>(); allRoles.value.forEach(r => m.set(r.id, r)); return m })

  const showAddRole = ref(false)
  const adding = ref(false)
  const addRoleTab = ref<'bu' | 'system'>('bu')
  const selectedBuRoleIds = ref<string[]>([])
  const selectedSystemRoleIds = ref<string[]>([])

  const assignedIds = computed(() => new Set(accessList.value.map(a => a.targetId)))
  const systemRoleOptions = computed(() => allRoles.value.filter(r => !['BU_BOUNDED'].includes(r.type as string) && !assignedIds.value.has(r.id)))

  const resolveRoleName = (roleId: string) => allRolesMap.value.get(roleId)?.name ?? roleId
  const resolveRoleTagType = (roleId: string) => { const t = allRolesMap.value.get(roleId)?.type; return t ? roleTagType(t) : '' }
  const resolveRoleTypeLabel = (roleId: string) => { const t = allRolesMap.value.get(roleId)?.type; return t ? (roleTypeDisplayLabel(t)) : '—' }
  const formatDate = (d: string | undefined) => d ? fmtDate(d) : ''

  const loadAccessList = async () => {
    if (!entityId.value) return
    loading.value = true
    try { accessList.value = await relationTableStructureApi.getAccessConfig(entityId.value) }
    catch { /* silent */ } finally { loading.value = false }
  }

  const loadAllRoles = async () => {
    if (allRoles.value.length) return
    try { allRoles.value = await roleApi.list() } catch { /* silent */ }
  }

  const openAddDialog = async () => {
    await loadAllRoles()
    addRoleTab.value = 'bu'; selectedBuRoleIds.value = []; selectedSystemRoleIds.value = []
    showAddRole.value = true
  }

  const handleAddRole = async () => {
    const roleIds = addRoleTab.value === 'bu' ? selectedBuRoleIds.value : selectedSystemRoleIds.value
    if (!roleIds.length) return
    adding.value = true
    try { await Promise.all(roleIds.map(id => relationTableStructureApi.addAccess(entityId.value!, id))); notifySuccess('Added'); showAddRole.value = false; await loadAccessList() }
    catch { notifyError('Failed') } finally { adding.value = false }
  }

  const handleRemove = async (access: RelationTableAccess) => {
    if (!entityId.value) return
    try { await relationTableStructureApi.removeAccess(entityId.value, access.id); notifySuccess('Removed'); await loadAccessList() }
    catch { /* silent */ }
  }

  return { loading, accessList, allRoles, allRolesMap,
    showAddRole, adding, addRoleTab, selectedBuRoleIds, selectedSystemRoleIds,
    assignedIds, systemRoleOptions,
    resolveRoleName, resolveRoleTagType, resolveRoleTypeLabel, formatDate,
    loadAccessList, loadAllRoles, openAddDialog, handleAddRole, handleRemove,
  }
}
