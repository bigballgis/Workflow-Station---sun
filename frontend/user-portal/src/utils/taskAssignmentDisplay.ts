export type AssignmentDisplayKey =
  | 'user'
  | 'processInitiator'
  | 'virtualGroup'
  | 'deptRole'
  | 'delegated'
  | 'candidateUsers'
  | 'fixedBuRole'
  | 'buRole'

export interface AssignmentTypeRow {
  assignmentType?: string
  bpmnAssigneeType?: string
  claimPoolTask?: boolean
}

export function assignmentDisplayKey(task: AssignmentTypeRow): AssignmentDisplayKey {
  if (task.claimPoolTask) {
    const bpmn = task.bpmnAssigneeType?.trim().toUpperCase()
    if (bpmn === 'FIXED_BU_ROLE' || bpmn === 'FIXEDDEPT' || bpmn === 'FIXED_DEPT') {
      return 'fixedBuRole'
    }
    return 'buRole'
  }
  const bpmn = task.bpmnAssigneeType?.trim().toUpperCase()
  if (bpmn === 'INITIATOR' || bpmn === 'PROCESS_INITIATOR') {
    return 'processInitiator'
  }
  if (bpmn === 'FIXED_BU_ROLE') {
    return 'fixedBuRole'
  }
  if (bpmn === 'BU_ROLE') {
    return 'buRole'
  }
  const map: Record<string, AssignmentDisplayKey> = {
    USER: 'user',
    VIRTUAL_GROUP: 'virtualGroup',
    DEPT_ROLE: 'deptRole',
    DELEGATED: 'delegated',
    CANDIDATE_USERS: 'candidateUsers',
  }
  return map[task.assignmentType ?? ''] || 'user'
}

/** CSS class on `.el-tag.assignment-tag` — one colour per category. */
export function assignmentTagClass(task: AssignmentTypeRow): string {
  const key = assignmentDisplayKey(task)
  const map: Record<AssignmentDisplayKey, string> = {
    user: 'user',
    processInitiator: 'user',
    virtualGroup: 'virtual-group',
    deptRole: 'dept-role',
    delegated: 'delegated',
    candidateUsers: 'candidate-users',
    fixedBuRole: 'bu-role',
    buRole: 'bu-role',
  }
  return map[key]
}
