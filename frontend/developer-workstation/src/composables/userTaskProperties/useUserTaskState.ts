/**
 * UserTask 属性面板的共享状态与基础读写。
 *
 * 持有原 SFC 顶层的全部 ref/computed，以及 updateBasicProp/updateExtProp；
 * 由功能 composable（assignee / multi-instance）共享。行为零变化。
 */
import { ref, computed } from 'vue'
import type {
  FormDefinition,
  ActionDefinition,
  TableDefinition
} from '@/api/functionUnit'
import type { BusinessUnitInfo, RoleInfo } from '@/api/adminCenter'
import {
  getBasicProperties,
  setBasicProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'
import type {
  AssigneeTypeEnum,
  TFn,
  UserTaskPropertyContext,
  UserTaskPropsAccessor
} from './types'

export function useUserTaskState(
  props: UserTaskPropsAccessor,
  t: TFn
): UserTaskPropertyContext {
  // Basic properties
  const taskName = ref('')
  const taskDescription = ref('')

  // Assignee config
  const assigneeType = ref<AssigneeTypeEnum>('INITIATOR')
  const lastLoadedAssigneeType = ref<AssigneeTypeEnum>('INITIATOR')
  const roleId = ref('')
  const roleIds = ref<string[]>([])
  const businessUnitId = ref('')
  const assigneeLabel = ref('')
  const candidateUsers = ref('')
  const candidateGroups = ref('')
  const manualAssignVariable = ref('')
  const manualAssignBuVariable = ref('')
  const manualAssignRoleVariable = ref('')
  const assigneeVariableName = ref('')
  const elementSubTableId = ref<number | ''>('')
  const elementSubTableName = ref('')
  const assigneeField = ref('')
  const rowIdVariable = ref('')
  const subTables = ref<TableDefinition[]>([])
  const loadingSubTables = ref(false)

  // Multi-instance sub-process row progress columns (stored on parent SubProcess)
  const miTaskStatusField = ref('task_status')
  const miTaskCurrentNodeField = ref('task_current_node')
  const FIELD_NAME_RE = /^[a-zA-Z_][a-zA-Z0-9_]*$/
  const miStatusFieldInvalid = computed(() => {
    return !!miTaskStatusField.value && !FIELD_NAME_RE.test(miTaskStatusField.value.trim())
  })
  const miCurrentNodeFieldInvalid = computed(() => {
    return !!miTaskCurrentNodeField.value && !FIELD_NAME_RE.test(miTaskCurrentNodeField.value.trim())
  })

  /** 顺序流变化时递增，驱动「单入线」校验刷新 */
  const topologyTick = ref(0) // retained for backward compatibility; anchor UI removed

  // Business unit and role data
  const businessUnits = ref<BusinessUnitInfo[]>([])
  const buBoundedRoles = ref<RoleInfo[]>([])
  const buUnboundedRoles = ref<RoleInfo[]>([])
  const eligibleRoles = ref<RoleInfo[]>([])
  const loadingBusinessUnits = ref(false)
  const loadingRoles = ref(false)

  // Form binding
  const formId = ref<number | null>(null)
  const forms = ref<FormDefinition[]>([])

  // Action binding
  const actionIds = ref<number[]>([])
  const actions = ref<ActionDefinition[]>([])

  // Timeout config
  const timeoutEnabled = ref(false)
  const timeoutDuration = ref('')
  const timeoutAction = ref<'remind' | 'approve' | 'reject'>('remind')

  // Multi-instance config
  const multiInstance = ref(false)
  const sequential = ref(false)
  const collection = ref('')
  const completionCondition = ref('')

  const basicProps = computed(() => getBasicProperties(props.element))

  function updateBasicProp(name: string, value: any) {
    if (!props.element || !props.modeler) return
    setBasicProperties(props.modeler, props.element, { [name]: value })
  }

  function updateExtProp(name: string, value: any) {
    if (!props.element || !props.modeler) return
    setExtensionProperty(props.modeler, props.element, name, value)
  }

  return {
    taskName,
    taskDescription,
    assigneeType,
    lastLoadedAssigneeType,
    roleId,
    roleIds,
    businessUnitId,
    assigneeLabel,
    candidateUsers,
    candidateGroups,
    manualAssignVariable,
    manualAssignBuVariable,
    manualAssignRoleVariable,
    assigneeVariableName,
    elementSubTableId,
    elementSubTableName,
    assigneeField,
    rowIdVariable,
    subTables,
    loadingSubTables,
    miTaskStatusField,
    miTaskCurrentNodeField,
    miStatusFieldInvalid,
    miCurrentNodeFieldInvalid,
    topologyTick,
    businessUnits,
    buBoundedRoles,
    buUnboundedRoles,
    eligibleRoles,
    loadingBusinessUnits,
    loadingRoles,
    formId,
    forms,
    actionIds,
    actions,
    timeoutEnabled,
    timeoutDuration,
    timeoutAction,
    multiInstance,
    sequential,
    collection,
    completionCondition,
    basicProps,
    updateBasicProp,
    updateExtProp,
    FIELD_NAME_RE,
    t
  }
}
