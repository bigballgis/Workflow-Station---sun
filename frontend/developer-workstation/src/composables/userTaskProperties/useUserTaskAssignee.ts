/**
 * UserTask 属性面板的指派（assignee）相关逻辑。
 *
 * 涵盖指派类型判定、角色 / 业务单元数据加载与筛选，以及对应的变更处理。
 * 行为零变化。
 */
import { computed } from 'vue'
import { adminCenterApi, type BusinessUnitInfo } from '@/api/adminCenter'
import type {
  AssigneeTypeEnum,
  UserTaskPropertyContext
} from './types'

export function useUserTaskAssignee(ctx: UserTaskPropertyContext) {
  const {
    assigneeType,
    lastLoadedAssigneeType,
    roleId,
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

  // Role selection tip
  const roleSelectTip = computed(() => {
    if (assigneeType.value === 'BU_UNBOUNDED_ROLE') {
      return t('properties.buUnboundedRoleTip')
    }
    if (assigneeType.value === 'FIXED_BU_ROLE' || assigneeType.value === 'BU_ROLE') {
      return t('properties.fixedBuRoleTip')
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
    businessUnitId.value = ''
    updateExtProp('roleId', '')
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

  function handleRoleChange(id: string) {
    updateExtProp('roleId', id)

    // Update label
    const role = filteredRoles.value.find(r => r.id === id)
    if (role) {
      const typeLabel = getAssigneeTypeLabel(assigneeType.value)
      assigneeLabel.value = `${typeLabel}: ${role.name}`
      updateExtProp('assigneeLabel', assigneeLabel.value)
    }
  }

  function handleBusinessUnitChange(id: string) {
    updateExtProp('businessUnitId', id)

    // Clear role selection
    roleId.value = ''
    updateExtProp('roleId', '')

    // Load eligible roles for the business unit
    if (id) {
      loadEligibleRoles(id)
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
    needsRoleId,
    showRoleSelector,
    roleSelectPlaceholder,
    needsClaim,
    filteredRoles,
    roleSelectTip,
    handleAssigneeTypeChange,
    handleRoleChange,
    handleBusinessUnitChange,
    getAssigneeTypeLabel,
    findBusinessUnitById,
    loadRoles,
    loadBusinessUnits,
    loadEligibleRoles
  }
}
