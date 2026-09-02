/**
 * UserTask 属性面板的多实例子任务 / element-variable 相关逻辑。
 *
 * 涵盖子表加载、父级 MI 子流程探测、MI 进度列读写，
 * 以及子表 / 指派字段 / 表单的变更处理。行为零变化。
 */
import { computed } from 'vue'
import { functionUnitApi } from '@/api/functionUnit'
import {
  getExtensionProperties,
  setExtensionProperty
} from '@/utils/bpmnExtensions'
import type { UserTaskPropertyContext, UserTaskPropsAccessor } from './types'

export function useUserTaskMultiInstance(
  props: UserTaskPropsAccessor,
  ctx: UserTaskPropertyContext
) {
  const {
    assigneeType,
    lastLoadedAssigneeType,
    elementSubTableName,
    assigneeField,
    assigneeMode,
    allowUser,
    allowRole,
    roleField,
    buField,
    rowIdVariable,
    elementSubTableId,
    subTables,
    loadingSubTables,
    miTaskStatusField,
    miTaskCurrentNodeField,
    miStatusFieldInvalid,
    miCurrentNodeFieldInvalid,
    forms,
    updateExtProp,
    FIELD_NAME_RE
  } = ctx

  function ensureSubTaskAssigneeMode() {
    if (!isFirstMultiInstanceSubTask.value || assigneeType.value === 'ELEMENT_VARIABLE') {
      return
    }
    assigneeType.value = 'ELEMENT_VARIABLE'
    lastLoadedAssigneeType.value = 'ELEMENT_VARIABLE'
    updateExtProp('assigneeType', 'ELEMENT_VARIABLE')
    updateExtProp('assigneeLabel', ctx.t('properties.elementVariableType'))
  }

  function handleFormChange(id: number | null) {
    ensureSubTaskAssigneeMode()
    updateExtProp('formId', id)
    const form = forms.value.find(f => f.id === id)
    if (form) {
      updateExtProp('formName', form.formName)
    } else {
      updateExtProp('formName', '')
    }
  }

  // 子表 id 在「el-select v-model / 选项 value / 后端 JSON」之间可能是 number 或
  // string（后端把 Long 序列化为字符串时尤甚），严格 === 会漏匹配，导致切换子表后
  // Sub-table name 不更新、指派字段列表为空。统一按字符串比较即可避免类型漂移。
  function findSubTableById(id: number | string | '') {
    if (id === '' || id === null || id === undefined) return undefined
    return subTables.value.find(tb => String(tb.id) === String(id))
  }

  function handleSubTableChange(id: number | '') {
    ensureSubTaskAssigneeMode()
    if (id === '' || id === null || id === undefined) {
      elementSubTableName.value = ''
      assigneeField.value = ''
      roleField.value = ''
      buField.value = ''
      rowIdVariable.value = ''
      updateExtProp('subTableId', '')
      updateExtProp('subTableName', '')
      updateExtProp('assigneeField', '')
      updateExtProp('roleField', '')
      updateExtProp('buField', '')
      updateExtProp('rowIdVariable', '')
      return
    }
    updateExtProp('subTableId', id)
    const table = findSubTableById(id)
    if (table) {
      elementSubTableName.value = table.tableName
      updateExtProp('subTableName', table.tableName)

      // If current assigneeField not in this table's fields, reset it and try to auto-pick a plausible one
      const fieldNames = (table.fieldDefinitions || []).map(fd => fd.fieldName)
      if (!assigneeField.value || !fieldNames.includes(assigneeField.value)) {
        const preferred = (table.fieldDefinitions || []).find(fd =>
          /^(assignee|assignee_user_id|user_id|handler|owner_user_id|approver)$/i.test(fd.fieldName)
        )
        assigneeField.value = preferred?.fieldName || ''
        updateExtProp('assigneeField', assigneeField.value)
      }

      // role 模式：若当前 roleField 不在该表字段中，重置并尝试自动预选一个 role 列
      if (!roleField.value || !fieldNames.includes(roleField.value)) {
        const preferredRole = (table.fieldDefinitions || []).find(fd =>
          /^(role|role_code|role_id|role_codes)$/i.test(fd.fieldName)
        )
        roleField.value = preferredRole?.fieldName || ''
        updateExtProp('roleField', roleField.value)
      }
      // BU 列可选：若当前 buField 不在该表字段中则清空（不强制预选）
      if (buField.value && !fieldNames.includes(buField.value)) {
        buField.value = ''
        updateExtProp('buField', '')
      }

      // Default rowIdVariable convention uses the element variable (currentItem) of the parent SubProcess
      if (!rowIdVariable.value) {
        rowIdVariable.value = 'currentItem.rowId'
        updateExtProp('rowIdVariable', rowIdVariable.value)
      }
    }
  }

  function handleAssigneeFieldChange(value: string) {
    ensureSubTaskAssigneeMode()
    updateExtProp('assigneeField', value || '')
  }

  /**
   * 由 allowUser / allowRole 两个开关派生 assigneeMode（user|role|both）并持久化到 BPMN。
   * 用 checkbox @change 的显式写入路径，避免 radio v-model+@change 的时序问题。
   */
  function persistAssigneeMode() {
    ensureSubTaskAssigneeMode()
    // 至少保证一个开关开着（都关时回落到个人）
    if (!allowUser.value && !allowRole.value) {
      allowUser.value = true
    }
    const mode = allowUser.value && allowRole.value ? 'both' : (allowRole.value ? 'role' : 'user')
    assigneeMode.value = mode
    updateExtProp('assigneeMode', mode)
  }

  function autoPickRoleField() {
    if (!roleField.value && elementSubTableId.value) {
      const table = findSubTableById(elementSubTableId.value)
      const preferredRole = (table?.fieldDefinitions || []).find(fd =>
        /^(role|role_code|role_id|role_codes)$/i.test(fd.fieldName)
      )
      roleField.value = preferredRole?.fieldName || ''
      updateExtProp('roleField', roleField.value)
    }
    if (!buField.value && elementSubTableId.value) {
      const table = findSubTableById(elementSubTableId.value)
      const preferredBu = (table?.fieldDefinitions || []).find(fd =>
        /^(bu|bu_code|business_unit|business_unit_code)$/i.test(fd.fieldName)
      )
      buField.value = preferredBu?.fieldName || ''
      updateExtProp('buField', buField.value)
    }
  }

  // Element Plus el-checkbox @change 回调值类型是 CheckboxValueType(string|number|boolean)，用宽松入参 + !! 归一化。
  function handleAllowUserChange(checkedRaw: string | number | boolean) {
    const checked = !!checkedRaw
    persistAssigneeMode()
    if (!checked) {
      assigneeField.value = ''
      updateExtProp('assigneeField', '')
    }
  }

  function handleAllowRoleChange(checkedRaw: string | number | boolean) {
    const checked = !!checkedRaw
    persistAssigneeMode()
    if (checked) {
      autoPickRoleField()
    } else {
      roleField.value = ''
      buField.value = ''
      updateExtProp('roleField', '')
      updateExtProp('buField', '')
    }
  }

  function handleRoleFieldChange(value: string) {
    ensureSubTaskAssigneeMode()
    updateExtProp('roleField', value || '')
  }

  function handleBuFieldChange(value: string) {
    ensureSubTaskAssigneeMode()
    updateExtProp('buField', value || '')
  }

  const assigneeFieldOptions = computed(() => {
    const table = findSubTableById(elementSubTableId.value)
    return table?.fieldDefinitions || []
  })

  // 进度列只能来自所选子表在 Table Design 里真实存在的字段：不注入任何硬编码
  // 约定列名（task_status / task_current_node），否则设计器会让人选到表上没有的列，
  // 运行时 UPDATE 静默写不进去。
  const miProgressFieldOptions = computed(() => {
    const seen = new Set<string>()
    const merged: string[] = []
    for (const f of assigneeFieldOptions.value || []) {
      const key = String((f as any)?.fieldName || '').trim()
      if (!key || seen.has(key)) continue
      seen.add(key)
      merged.push(key)
    }
    return merged
  })

  const assigneeFieldPlaceholder = computed(() => {
    if (!elementSubTableId.value) return ctx.t('properties.selectSubTableFirst')
    return ctx.t('properties.selectAssigneeField')
  })

  const miProgressFieldPlaceholder = computed(() => {
    if (!elementSubTableId.value) return ctx.t('properties.selectSubTableFirst')
    return ctx.t('properties.miProgressFieldSelectPlaceholder')
  })

  const parentIsMultiInstanceSubProcess = computed(() => {
    const parent = (props.element as any)?.parent
    const parentBo = parent?.businessObject
    if (!parentBo) return false
    if (parentBo.$type !== 'bpmn:SubProcess') return false
    return !!parentBo.loopCharacteristics
  })

  function getParentMiSubProcessElement(): any | null {
    const parent = (props.element as any)?.parent
    if (!parent) return null
    const bo = parent?.businessObject
    if (!bo || bo.$type !== 'bpmn:SubProcess' || !bo.loopCharacteristics) return null
    return parent
  }

  function persistMiProgressFieldProps() {
    if (!props.modeler || !props.element) return
    const parent = getParentMiSubProcessElement()
    if (!parent) return
    // 清空即写回空串（而不是回落到硬编码列名），让「没配」如实存进 BPMN。
    const st = (miTaskStatusField.value || '').trim()
    const nd = (miTaskCurrentNodeField.value || '').trim()
    if (!st || FIELD_NAME_RE.test(st)) {
      setExtensionProperty(props.modeler, parent, 'miTaskStatusField', st)
    }
    if (!nd || FIELD_NAME_RE.test(nd)) {
      setExtensionProperty(props.modeler, parent, 'miTaskCurrentNodeField', nd)
    }
  }

  function handleMiTaskStatusFieldChange() {
    miTaskStatusField.value = miTaskStatusField.value.trim()
    if (miStatusFieldInvalid.value) return
    persistMiProgressFieldProps()
  }

  function handleMiTaskCurrentNodeFieldChange() {
    miTaskCurrentNodeField.value = miTaskCurrentNodeField.value.trim()
    if (miCurrentNodeFieldInvalid.value) return
    persistMiProgressFieldProps()
  }

  function getElementRefId(ref: any): string {
    return typeof ref === 'string' ? ref : (ref?.id || '')
  }

  function findFirstUserTaskInSubProcess(parentBo: any): any {
    const flowElements: any[] = parentBo?.flowElements || []
    const byId = new Map(flowElements.filter(fe => fe?.id).map(fe => [fe.id, fe]))
    const sequenceFlows = flowElements.filter(fe => fe?.$type === 'bpmn:SequenceFlow')
    const outgoingBySource = new Map<string, string[]>()

    for (const flow of sequenceFlows) {
      const sourceId = getElementRefId(flow.sourceRef)
      const targetId = getElementRefId(flow.targetRef)
      if (!sourceId || !targetId) continue
      const outgoing = outgoingBySource.get(sourceId) || []
      outgoing.push(targetId)
      outgoingBySource.set(sourceId, outgoing)
    }

    const startIds = flowElements
      .filter(fe => fe?.$type === 'bpmn:StartEvent')
      .map(fe => fe.id)
      .filter(Boolean)

    const queue = [...startIds]
    const visited = new Set<string>()
    while (queue.length > 0) {
      const id = queue.shift()
      if (!id || visited.has(id)) continue
      visited.add(id)

      for (const targetId of outgoingBySource.get(id) || []) {
        const target = byId.get(targetId)
        if (target?.$type === 'bpmn:UserTask') {
          return target
        }
        queue.push(targetId)
      }
    }

    return flowElements.find(fe => fe?.$type === 'bpmn:UserTask')
  }

  const isFirstMultiInstanceSubTask = computed(() => {
    if (!parentIsMultiInstanceSubProcess.value) return false
    const parentBo = (props.element as any)?.parent?.businessObject
    const firstUserTask = findFirstUserTaskInSubProcess(parentBo)
    const currentId = props.element?.businessObject?.id || props.element?.id
    return !!firstUserTask && firstUserTask.id === currentId
  })

  async function loadSubTables() {
    loadingSubTables.value = true
    try {
      const res = await functionUnitApi.getTables(props.functionUnitId)
      const all = res.data || []
      subTables.value = all.filter(tb => (tb.tableType || '').toUpperCase() === 'SUB')
    } catch {
      subTables.value = []
    } finally {
      loadingSubTables.value = false
    }
  }

  /**
   * 子任务模式相关的属性加载（在 loadProperties 内、首个 MI 子任务时调用）。
   * 仅迁移原 loadProperties 中读取父级 SubProcess 进度列的分支，逻辑逐字保留。
   */
  function loadSubTaskMiProgressFields() {
    ensureSubTaskAssigneeMode()
    // Read progress columns from parent SubProcess extension properties
    const parent = getParentMiSubProcessElement()
    if (parent) {
      const pExt = getExtensionProperties(parent)
      const rawSt = pExt?.miTaskStatusField
      const rawNd = pExt?.miTaskCurrentNodeField
      // 没配过就显示为空，由用户从子表真实字段里选，不预填约定列名。
      miTaskStatusField.value =
        typeof rawSt === 'string' && rawSt.trim() && FIELD_NAME_RE.test(rawSt.trim())
          ? rawSt.trim()
          : ''
      miTaskCurrentNodeField.value =
        typeof rawNd === 'string' && rawNd.trim() && FIELD_NAME_RE.test(rawNd.trim())
          ? rawNd.trim()
          : ''
    }
  }

  return {
    ensureSubTaskAssigneeMode,
    handleFormChange,
    handleSubTableChange,
    handleAssigneeFieldChange,
    handleAllowUserChange,
    handleAllowRoleChange,
    handleRoleFieldChange,
    handleBuFieldChange,
    assigneeFieldOptions,
    miProgressFieldOptions,
    assigneeFieldPlaceholder,
    miProgressFieldPlaceholder,
    parentIsMultiInstanceSubProcess,
    getParentMiSubProcessElement,
    persistMiProgressFieldProps,
    handleMiTaskStatusFieldChange,
    handleMiTaskCurrentNodeFieldChange,
    findFirstUserTaskInSubProcess,
    isFirstMultiInstanceSubTask,
    loadSubTables,
    loadSubTaskMiProgressFields
  }
}
