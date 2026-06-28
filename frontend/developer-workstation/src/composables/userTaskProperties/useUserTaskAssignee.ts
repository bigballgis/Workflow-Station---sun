/**
 * UserTask 属性面板的指派（assignee）相关逻辑。
 *
 * 涵盖指派类型判定、角色 / 业务单元数据加载与筛选，以及对应的变更处理。
 * 行为零变化。
 */
import { computed } from 'vue'
import { adminCenterApi, type BusinessUnitInfo, type RoleInfo } from '@/api/adminCenter'
import { parseRoleIdsFromExt, serializeRoleIds, filterRoleIdsForAssigneeType as filterRoleIdsPure, sanitizePersistedRoleIds as sanitizeRoleIdsPure } from '@/utils/assigneeRoleIds'
import type {
  AssigneeTypeEnum,
  UserTaskPropertyContext
} from './types'

export function useUserTaskAssignee(ctx: UserTaskPropertyContext) {
  const {
    assigneeType,
    lastLoadedAssigneeType,
    roleId,
    roleIds,
    businessUnitId,
    assigneeLabel,
    candidateUsers,
    candidateGroups,
    businessUnits,
    buBoundedRoles,
    buUnboundedRoles,
    eligibleRoles,
    loadingBusinessUnits,
    loadingRoles,
    updateExtProp,
    t
  } = ctx

  // ---- id ⇄ code mapping at the BPMN boundary ----
  // UI refs (roleId/roleIds/businessUnitId) always hold DB ids so existing select/filter/label logic is unchanged.
  // BPMN extension props (roleIds/roleId/businessUnitId) hold *codes* so an imported Function Unit resolves
  // against the target environment without manual editing (ids differ per env; codes are unique & stable).

  /** All role catalogs merged for id⇄code lookup, regardless of current assignee type. */
  function allKnownRoles(): RoleInfo[] {
    return [...buBoundedRoles.value, ...buUnboundedRoles.value, ...eligibleRoles.value]
  }

  function roleIdToCode(id: string): string {
    const r = allKnownRoles().find(role => role.id === id)
    return r?.code || id
  }

  function roleCodeToId(code: string): string {
    const r = allKnownRoles().find(role => role.code === code)
    return r?.id || code
  }

  function businessUnitIdToCode(id: string): string {
    const bu = findBusinessUnitById(businessUnits.value, id)
    return bu?.code || id
  }

  function findBusinessUnitByCode(units: BusinessUnitInfo[], code: string): BusinessUnitInfo | null {
    for (const unit of units) {
      if (unit.code === code) return unit
      if (unit.children) {
        const found = findBusinessUnitByCode(unit.children, code)
        if (found) return found
      }
    }
    return null
  }

  function businessUnitCodeToId(code: string): string {
    const bu = findBusinessUnitByCode(businessUnits.value, code)
    return bu?.id || code
  }

  const ROLE_ID_ASSIGNEE_TYPES: AssigneeTypeEnum[] = [
    'HIERARCHY_ROLE',
    'BU_ROLE',
    'CURRENT_BU_ROLE',
    'CURRENT_PARENT_BU_ROLE',
    'INITIATOR_BU_ROLE',
    'INITIATOR_PARENT_BU_ROLE',
    'FIXED_BU_ROLE',
    'BU_UNBOUNDED_ROLE'
  ]

  function assigneeTypeNeedsRoleId(t: AssigneeTypeEnum): boolean {
    return ROLE_ID_ASSIGNEE_TYPES.includes(t)
  }

  const needsBuForRole = computed(
    () => assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE'
  )

  const needsInitiatorHierarchyMultiRole = computed(
    () => assigneeType.value === 'INITIATOR_BU_ROLE'
      || assigneeType.value === 'INITIATOR_PARENT_BU_ROLE'
  )

  /** Multi-select role picker (Fixed BU + role, or initiator hierarchy role types) */
  const needsMultiRoleSelect = computed(
    () => needsBuForRole.value || needsInitiatorHierarchyMultiRole.value
  )

  // Whether role ID is needed
  const needsRoleId = computed(() => assigneeTypeNeedsRoleId(assigneeType.value))

  // Whether to show role selector
  const showRoleSelector = computed(() => {
    return needsRoleId.value
  })

  // Role selector placeholder
  const roleSelectPlaceholder = computed(() => {
    if (needsBuForRole.value && !businessUnitId.value) {
      return t('properties.selectBusinessUnitFirst')
    }
    if (needsMultiRoleSelect.value) {
      return t('properties.selectRoles')
    }
    return t('properties.selectRole')
  })

  // Whether claim is needed
  const needsClaim = computed(() => {
    return [
      'HIERARCHY_ROLE',
      'BU_ROLE',
      'MANUAL_ASSIGN',
      'ASSIGNEE_FROM_VARIABLE',
      'ELEMENT_VARIABLE',
      'CURRENT_BU_ROLE',
      'CURRENT_PARENT_BU_ROLE',
      'INITIATOR_BU_ROLE',
      'INITIATOR_PARENT_BU_ROLE',
      'FIXED_BU_ROLE',
      'BU_UNBOUNDED_ROLE'
    ].includes(assigneeType.value)
  })

  /** Roles fetched by id when selected ids are not yet in role lists (non–BU-scoped types only) */
  const eligibleRoleIdSet = computed(
    () => new Set(eligibleRoles.value.map(r => r.id).filter(Boolean))
  )

  const boundedRoleIdSet = computed(
    () => new Set(buBoundedRoles.value.map(r => r.id).filter(Boolean))
  )

  // Filter roles by assignment type
  const filteredRoles = computed(() => {
    if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
      return buUnboundedRoles.value
    }
    if ((assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE') && businessUnitId.value) {
      return eligibleRoles.value
    }
    return buBoundedRoles.value
  })

  /** el-select options — multi-select types only expose roles from the allowed catalog (no cross-list merge) */
  const roleSelectOptions = computed(() => {
    if (needsMultiRoleSelect.value) {
      return filteredRoles.value
    }
    const seen = new Set<string>()
    const out: RoleInfo[] = []
    for (const role of filteredRoles.value) {
      if (!seen.has(role.id)) {
        seen.add(role.id)
        out.push(role)
      }
    }
    if (roleId.value && !seen.has(roleId.value)) {
      const cached =
        filteredRoles.value.find(r => r.id === roleId.value)
        ?? buBoundedRoles.value.find(r => r.id === roleId.value)
        ?? buUnboundedRoles.value.find(r => r.id === roleId.value)
      out.push(cached ?? { id: roleId.value, name: roleId.value, code: '', type: 'BU_BOUNDED' })
    }
    return out
  })

  function filterRoleIdsForAssigneeType(ids: string[]): string[] {
    return filterRoleIdsPure(ids, {
      assigneeType: assigneeType.value,
      businessUnitId: businessUnitId.value,
      eligibleRoleIds: eligibleRoleIdSet.value,
      boundedRoleIds: boundedRoleIdSet.value,
    })
  }

  /** Drop persisted role ids outside the allowed catalog; sync BPMN extensions */
  function sanitizePersistedRoleIds() {
    const sanitized = sanitizeRoleIdsPure(
      roleIds.value,
      {
        assigneeType: assigneeType.value,
        businessUnitId: businessUnitId.value,
        eligibleRoleIds: eligibleRoleIdSet.value,
        boundedRoleIds: boundedRoleIdSet.value,
      },
      { needsMultiRoleSelect: needsMultiRoleSelect.value },
    )
    if (sanitized) {
      syncRoleExtProps(sanitized)
    }
  }

  // Role selection tip
  const roleSelectTip = computed(() => {
    if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
      return t('properties.buUnboundedRoleTip')
    }
    if (assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE') {
      return t('properties.fixedBuMultiRoleTip')
    }
    if (needsInitiatorHierarchyMultiRole.value) {
      return t('properties.hierarchyMultiRoleTip')
    }
    return t('properties.buBoundedRoleTip')
  })

  function handleAssigneeTypeChange(type: AssigneeTypeEnum) {
    lastLoadedAssigneeType.value = type
    updateExtProp('assigneeType', type)

    const labelMap: Record<AssigneeTypeEnum, string> = {
      INITIATOR: t('properties.initiator'),
      ENTITY_MANAGER: t('properties.entityManager'),
      FUNCTION_MANAGER: t('properties.functionManager'),
      HIERARCHY_ROLE: '',
      BU_ROLE: '',
      MANUAL_ASSIGN: '',
      ASSIGNEE_FROM_VARIABLE: '',
      ELEMENT_VARIABLE: '',
      CURRENT_BU_ROLE: '',
      CURRENT_PARENT_BU_ROLE: '',
      INITIATOR_BU_ROLE: '',
      INITIATOR_PARENT_BU_ROLE: '',
      FIXED_BU_ROLE: '',
      BU_UNBOUNDED_ROLE: ''
    }

    roleId.value = ''
    roleIds.value = []
    businessUnitId.value = ''
    updateExtProp('roleId', '')
    updateExtProp('roleIds', '')
    updateExtProp('businessUnitId', '')

    if (!assigneeTypeNeedsRoleId(type)) {
      assigneeLabel.value = labelMap[type] || ''
      updateExtProp('assigneeLabel', assigneeLabel.value)
    } else {
      assigneeLabel.value = ''
      updateExtProp('assigneeLabel', '')
    }

    if (assigneeTypeNeedsRoleId(type)) {
      loadRoles()
    }

    if (type === 'FIXED_BU_ROLE' || type === 'BU_ROLE') {
      loadBusinessUnits()
    }

    candidateUsers.value = ''
    candidateGroups.value = ''
    updateExtProp('candidateUsers', '')
    updateExtProp('candidateGroups', '')
  }

  function syncRoleExtProps(ids: string[]) {
    const normalized = filterRoleIdsForAssigneeType(ids)
    roleIds.value = normalized
    roleId.value = normalized[0] ?? ''
    // Persist codes (stable across environments), not ids
    const codes = normalized.map(roleIdToCode)
    updateExtProp('roleIds', serializeRoleIds(codes))
    updateExtProp('roleId', codes[0] ?? '')

    const typeLabel = getAssigneeTypeLabel(assigneeType.value)
    const names = normalized
      .map(id => filteredRoles.value.find(r => r.id === id)?.name ?? id)
      .filter(Boolean)
    if (names.length > 0) {
      assigneeLabel.value = `${typeLabel}: ${names.join(', ')}`
    } else if (needsBuForRole.value && businessUnitId.value) {
      const bu = findBusinessUnitById(businessUnits.value, businessUnitId.value)
      assigneeLabel.value = bu?.name ?? ''
    } else {
      assigneeLabel.value = ''
    }
    updateExtProp('assigneeLabel', assigneeLabel.value)
  }

  function handleRoleChange(id: string) {
    syncRoleExtProps(id ? [id] : [])
  }

  function handleRoleIdsChange(ids: string[]) {
    syncRoleExtProps(ids)
  }

  /**
   * Load persisted role *codes* from BPMN and map them back to ids for the UI.
   * Role catalogs must be loaded before calling this so code→id resolves; unknown
   * codes are kept as-is (degrade gracefully rather than dropping the selection).
   */
  function loadRoleIdsFromExt(ext: { roleIds?: string; roleId?: string }) {
    const parsedCodes = parseRoleIdsFromExt(ext)
    const ids = parsedCodes.map(roleCodeToId)
    roleIds.value = ids
    roleId.value = ids[0] ?? ''
  }

  async function handleBusinessUnitChange(id: string) {
    // Persist BU code (stable across environments), not id
    updateExtProp('businessUnitId', id ? businessUnitIdToCode(id) : '')

    // Clear role selection
    roleId.value = ''
    roleIds.value = []
    updateExtProp('roleId', '')
    updateExtProp('roleIds', '')

    // Load eligible roles for the business unit
    if (id) {
      await loadEligibleRoles(id)
    } else {
      eligibleRoles.value = []
    }

    // Update label
    const bu = findBusinessUnitById(businessUnits.value, id)
    if (bu) {
      assigneeLabel.value = bu.name
      updateExtProp('assigneeLabel', assigneeLabel.value)
    }
  }

  function getAssigneeTypeLabel(type: AssigneeTypeEnum): string {
    const labels: Record<AssigneeTypeEnum, string> = {
      INITIATOR: t('properties.initiator'),
      ENTITY_MANAGER: t('properties.entityManager'),
      FUNCTION_MANAGER: t('properties.functionManager'),
      HIERARCHY_ROLE: t('properties.hierarchyRole'),
      BU_ROLE: t('properties.buRoleConverged'),
      MANUAL_ASSIGN: t('properties.manualAssignType'),
      ASSIGNEE_FROM_VARIABLE: t('properties.assigneeFromVariableType'),
      ELEMENT_VARIABLE: t('properties.elementVariableType'),
      CURRENT_BU_ROLE: t('properties.currentBuRole'),
      CURRENT_PARENT_BU_ROLE: t('properties.currentParentBuRole'),
      INITIATOR_BU_ROLE: t('properties.initiatorBuRoleOption'),
      INITIATOR_PARENT_BU_ROLE: t('properties.initiatorParentBuRole'),
      FIXED_BU_ROLE: t('properties.fixedBuRole'),
      BU_UNBOUNDED_ROLE: t('properties.buUnboundedRole')
    }
    return labels[type] || type
  }

  // Recursively find business unit
  function findBusinessUnitById(units: BusinessUnitInfo[], id: string): BusinessUnitInfo | null {
    for (const unit of units) {
      if (unit.id === id) return unit
      if (unit.children) {
        const found = findBusinessUnitById(unit.children, id)
        if (found) return found
      }
    }
    return null
  }

  // Load roles
  async function loadRoles() {
    loadingRoles.value = true
    try {
      const [bounded, unbounded] = await Promise.all([
        adminCenterApi.getBuBoundedRoles(),
        adminCenterApi.getBuUnboundedRoles()
      ])
      buBoundedRoles.value = bounded || []
      buUnboundedRoles.value = unbounded || []
    } catch (e) {
      console.error('Failed to load roles:', e)
      buBoundedRoles.value = []
      buUnboundedRoles.value = []
    } finally {
      loadingRoles.value = false
    }
  }

  // Load business units
  async function loadBusinessUnits() {
    if (businessUnits.value.length > 0) return
    loadingBusinessUnits.value = true
    try {
      const data = await adminCenterApi.getBusinessUnitTree()
      businessUnits.value = data || []
    } catch (e) {
      console.error('Failed to load business units:', e)
      businessUnits.value = []
    } finally {
      loadingBusinessUnits.value = false
    }
  }

  // Load eligible roles for business unit
  async function loadEligibleRoles(unitId: string) {
    try {
      const data = await adminCenterApi.getBusinessUnitEligibleRoles(unitId)
      eligibleRoles.value = data || []
    } catch (e) {
      console.error('Failed to load eligible roles:', e)
      eligibleRoles.value = []
    }
  }

  return {
    assigneeTypeNeedsRoleId,
    needsBuForRole,
    needsInitiatorHierarchyMultiRole,
    needsMultiRoleSelect,
    needsRoleId,
    showRoleSelector,
    roleSelectPlaceholder,
    needsClaim,
    filteredRoles,
    roleSelectOptions,
    roleSelectTip,
    handleAssigneeTypeChange,
    handleRoleChange,
    handleRoleIdsChange,
    loadRoleIdsFromExt,
    handleBusinessUnitChange,
    getAssigneeTypeLabel,
    findBusinessUnitById,
    loadRoles,
    loadBusinessUnits,
    loadEligibleRoles,
    sanitizePersistedRoleIds,
    businessUnitCodeToId
  }
}
