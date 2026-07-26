/**
 * Shared types for the UserTask properties panel composables.
 *
 * 行为零变化：这些类型仅描述原 SFC 中已有的局部类型/上下文，未引入新约束。
 */
import type { Ref, ComputedRef } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type {
  FormDefinition,
  ActionDefinition,
  TableDefinition
} from '@/api/functionUnit'
import type { BusinessUnitInfo, RoleInfo } from '@/api/adminCenter'

/** 与引擎 {@code AssigneeType.fromCode} / 旧版 BPMN 字符串对齐；BU_UNBOUNDED 仅历史加载 */
export type AssigneeTypeEnum =
  | 'INITIATOR'
  | 'ENTITY_MANAGER'
  | 'FUNCTION_MANAGER'
  | 'HIERARCHY_ROLE'
  | 'BU_ROLE'
  | 'MANUAL_ASSIGN'
  | 'ASSIGNEE_FROM_VARIABLE'
  | 'ELEMENT_VARIABLE'
  | 'CURRENT_BU_ROLE'
  | 'CURRENT_PARENT_BU_ROLE'
  | 'INITIATOR_BU_ROLE'
  | 'INITIATOR_PARENT_BU_ROLE'
  | 'FIXED_BU_ROLE'
  | 'BU_UNBOUNDED_ROLE'

/** i18n 翻译函数（与 useI18n 的 t 等价签名） */
export type TFn = (key: string, ...args: any[]) => string

/** 组件 props 的只读访问器；composable 内统一通过它读取 element/modeler */
export interface UserTaskPropsAccessor {
  readonly modeler: BpmnModeler
  readonly element: BpmnElement
  readonly functionUnitId: number
}

/**
 * 由状态 composable 暴露、供功能 composable 共享的上下文。
 * 字段与原 SFC 顶层 ref/computed/函数一一对应，名称逐字保留。
 */
export interface UserTaskPropertyContext {
  // Basic properties
  taskName: Ref<string>
  taskDescription: Ref<string>

  // Assignee config
  assigneeType: Ref<AssigneeTypeEnum>
  lastLoadedAssigneeType: Ref<AssigneeTypeEnum>
  roleId: Ref<string>
  roleIds: Ref<string[]>
  businessUnitId: Ref<string>
  assigneeLabel: Ref<string>
  candidateUsers: Ref<string>
  candidateGroups: Ref<string>
  manualAssignVariable: Ref<string>
  manualAssignBuVariable: Ref<string>
  manualAssignRoleVariable: Ref<string>
  assigneeVariableName: Ref<string>
  elementSubTableId: Ref<number | ''>
  elementSubTableName: Ref<string>
  assigneeField: Ref<string>
  assigneeMode: Ref<'user' | 'role' | 'both'>
  allowUser: Ref<boolean>
  allowRole: Ref<boolean>
  roleField: Ref<string>
  buField: Ref<string>
  rowIdVariable: Ref<string>
  subTables: Ref<TableDefinition[]>
  loadingSubTables: Ref<boolean>

  // Multi-instance sub-process row progress columns
  miTaskStatusField: Ref<string>
  miTaskCurrentNodeField: Ref<string>
  miStatusFieldInvalid: ComputedRef<boolean>
  miCurrentNodeFieldInvalid: ComputedRef<boolean>

  // Topology tick (retained for backward compatibility)
  topologyTick: Ref<number>

  // Business unit and role data
  businessUnits: Ref<BusinessUnitInfo[]>
  buBoundedRoles: Ref<RoleInfo[]>
  buUnboundedRoles: Ref<RoleInfo[]>
  eligibleRoles: Ref<RoleInfo[]>
  loadingBusinessUnits: Ref<boolean>
  loadingRoles: Ref<boolean>

  // Form binding
  formId: Ref<number | null>
  forms: Ref<FormDefinition[]>

  // Action binding
  actionIds: Ref<number[]>
  actions: Ref<ActionDefinition[]>

  // Timeout config
  timeoutEnabled: Ref<boolean>
  timeoutDuration: Ref<string>
  timeoutAction: Ref<'remind' | 'approve' | 'reject'>

  // Multi-instance config
  multiInstance: Ref<boolean>
  sequential: Ref<boolean>
  collection: Ref<string>
  completionCondition: Ref<string>

  // Computed
  basicProps: ComputedRef<ReturnType<typeof import('@/utils/bpmnExtensions').getBasicProperties>>

  // Property writers
  updateBasicProp: (name: string, value: any) => void
  updateExtProp: (name: string, value: any) => void

  // Field-name validation regex
  FIELD_NAME_RE: RegExp

  // i18n 翻译函数
  t: TFn
}
