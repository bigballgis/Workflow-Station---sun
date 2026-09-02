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
  // MI 子任务分派：两个独立开关，可同时开（都开=场景 C，运行时逐行二选一）。
  // assigneeMode 存 BPMN（user|role|both），由 allowUser/allowRole 组合派生。
  const assigneeMode = ref<'user' | 'role' | 'both'>('user')
  const allowUser = ref(true)
  const allowRole = ref(false)
  const roleField = ref('')
  const buField = ref('')
  const rowIdVariable = ref('')
  const subTables = ref<TableDefinition[]>([])
  const loadingSubTables = ref(false)

  // Multi-instance sub-process row progress columns (stored on parent SubProcess)
  // 空 = 未配置；候选项来自所选子表 Table Design 的真实字段，不预填约定列名。
  const miTaskStatusField = ref('')
  const miTaskCurrentNodeField = ref('')
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
  // The My Requests design bound to this node (requestFormId / requestFormName ext props).
  // Editable on both the regular and MI sub-task panels via useUserTaskActions.handleRequestFormChange,
  // in addition to Form Design > My Requests > row menu > Bound Node — both paths write the
  // same BPMN fields.
  const requestFormId = ref<number | null>(null)
  const requestFormName = ref('')

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
    assigneeMode,
    allowUser,
    allowRole,
    roleField,
    buField,
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
    requestFormId,
    requestFormName,
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
