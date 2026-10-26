/**
 * UserTask 属性面板的动作（action）与表单数据加载逻辑。
 *
 * 维护节点动作的有序绑定、动作类型标签，以及表单 / 动作列表加载。
 */
import { computed, ref, toRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import { parseActionIds } from '@/utils/actionIds'
import type { ActionDefinition } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import type { BpmnExtensionValue, BpmnCustomProperty } from '@/types/bpmn'
import type { UserTaskPropertyContext, UserTaskPropsAccessor } from './types'

export function useUserTaskActions(
  props: UserTaskPropsAccessor,
  ctx: UserTaskPropertyContext
) {
  const { actions, forms, requestFormId, requestFormName, updateExtProp, t } = ctx

  const globalActionIds = ref<Array<ActionDefinition['id']>>([])
  const isGlobalAction = (id: ActionDefinition['id']) => globalActionIds.value.some(global => String(global) === String(id))
  const describeAction = (id: ActionDefinition['id']) => ({
    id,
    name: actions.value.find(action => String(action.id) === String(id))?.actionName ?? String(id),
  })
  const selectedActions = computed(() => ctx.actionIds.value.filter(id => !isGlobalAction(id)).map(describeAction))
  const globalActions = computed(() => globalActionIds.value.map(describeAction))

  /** Match the engine's first-occurrence order, including mixed numeric/string IDs. */
  function uniqueActionIds(value: unknown) {
    const seen = new Set<string>()
    return parseActionIds(value).filter(id => {
      const key = String(id)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
  }

  /** One immutable command keeps IDs and names aligned through undo / redo. */
  function persistActions(ids: Array<ActionDefinition['id']>) {
    const names: string[] = []
    const resolvedIds: Array<ActionDefinition['id']> = []
    for (const id of uniqueActionIds(ids).filter(id => !isGlobalAction(id))) {
      const action = actions.value.find(a => String(a.id) === String(id))
      if (!action) {
        ElMessage.error(t('properties.actionBindingMissing'))
        syncActionsFromModel()
        return
      }
      names.push(action.actionName)
      resolvedIds.push(action.id)
    }
    const element = toRaw(props.element)
    const moddle = props.modeler.get('moddle')
    const current = element.businessObject.extensionElements
    const values = current?.values ?? []
    const isProperties = (ext: { $type: string }) => ext.$type === 'custom:Properties' || ext.$type === 'custom_1:Properties'
    // Replace both legacy and current namespace entries so engine and designer see one order.
    const directProperties = new Set(['custom:Property', 'custom:Values', 'custom_1:Property', 'custom_1:Values'])
    const nextValues: BpmnExtensionValue[] = values
      .filter(ext => !(directProperties.has(ext.$type) && (ext.name === 'actionIds' || ext.name === 'actionNames')))
      .map((ext: BpmnExtensionValue) => {
        if (!isProperties(ext)) return ext
        return moddle.create(ext.$type, {
          ...ext.$attrs,
          property: (ext.property ?? []).filter((p: BpmnCustomProperty) => p.name !== 'actionIds' && p.name !== 'actionNames'),
          values: (ext.values ?? []).filter((p: BpmnCustomProperty) => p.name !== 'actionIds' && p.name !== 'actionNames'),
        })
      })
    const properties: BpmnExtensionValue = nextValues.find(ext => ext.$type === 'custom:Properties')
      ?? moddle.create('custom:Properties', { property: [] })
    if (!nextValues.includes(properties)) nextValues.push(properties)
    properties.property.push(
      moddle.create('custom:Property', { name: 'actionIds', value: JSON.stringify(resolvedIds) }),
      moddle.create('custom:Property', { name: 'actionNames', value: JSON.stringify(names) }),
    )
    const extensionElements = moddle.create('bpmn:ExtensionElements', { ...(current as { $attrs?: Record<string, string> } | undefined)?.$attrs, values: nextValues })
    props.modeler.get('modeling').updateProperties(element, { extensionElements })
    ctx.actionIds.value = resolvedIds
  }

  function syncActionsFromModel() {
    // Climb through nested subprocesses to the containing process, not the canvas root.
    let process = toRaw(props.element).businessObject
    while (process && process.$type !== 'bpmn:Process') process = process.$parent
    globalActionIds.value = uniqueActionIds(process
      ? getExtensionProperties({ ...toRaw(props.element), businessObject: process }).globalActionIds
      : undefined)
    ctx.actionIds.value = uniqueActionIds(getExtensionProperties(props.element).actionIds)
      .filter((id: ActionDefinition['id']) => !isGlobalAction(id))
  }

  function handleActionsChange(ids: Array<ActionDefinition['id']>) {
    const previous = parseActionIds(getExtensionProperties(props.element).actionIds)
    // Selection changes membership only; surviving bindings retain their positions.
    persistActions([
      ...previous.filter(id => ids.some(selected => String(selected) === String(id))),
      ...ids.filter(id => !previous.some(bound => String(bound) === String(id))),
    ])
  }

  function reorderActions(ordered: Array<{ id: ActionDefinition['id'] }>) {
    const ids = ordered.map(action => action.id)
    if (ids.every((id, index) => String(id) === String(ctx.actionIds.value[index]))) return
    persistActions(ids)
  }

  function moveAction(index: number, direction: -1 | 1) {
    const ids = selectedActions.value.map(action => action.id)
    const target = index + direction
    if (index < 0 || index >= ids.length || target < 0 || target >= ids.length) return
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    persistActions(ids)
  }

  function removeAction(id: ActionDefinition['id']) {
    persistActions(ctx.actionIds.value.filter(actionId => String(actionId) !== String(id)))
  }

  function handleFormChange(id: number | null) {
    updateExtProp('formId', id)
    const form = forms.value.find(f => f.id === id)
    if (form) {
      updateExtProp('formName', form.formName)
    }
  }

  /**
   * The My Requests design bound to this node — mirrors handleFormChange (To Do), but
   * writes requestFormId/requestFormName instead. Editable here in addition to Form
   * Design > My Requests > row menu > Bound Node; both paths write the same BPMN ext
   * props, so whichever was used last wins (last-write, same as any single BPMN field).
   */
  const requestableForms = computed(() =>
    forms.value.filter(f => (f.scene ?? 'TASK') === 'REQUEST' && f.formType !== 'DETAIL'))

  function handleRequestFormChange(id: number | null) {
    requestFormId.value = id
    updateExtProp('requestFormId', id)
    const form = id != null ? forms.value.find(f => f.id === id) : undefined
    requestFormName.value = form?.formName || ''
    updateExtProp('requestFormName', requestFormName.value)
  }

  const actionTypeLabel = (type: string) => {
    const map: Record<string, string> = {
      APPROVE: t('action.approve'),
      REJECT: t('action.reject'),
      TRANSFER: t('action.transfer'),
      DELEGATE: t('action.delegate'),
      ROLLBACK: t('action.rollback'),
      WITHDRAW: t('action.withdraw'),
      DRAFT: t('action.draft'),
      SAVE: t('action.saveDraft'),
      PROCESS_SUBMIT: t('action.processSubmit'),
      PROCESS_REJECT: t('action.processReject'),
      COMPOSITE: t('action.composite'),
      API_CALL: t('action.apiCall'),
      FORM_POPUP: t('action.formPopup'),
      CUSTOM_SCRIPT: t('action.customScript')
    }
    return map[type] || type
  }

  async function loadForms() {
    try {
      const res = await functionUnitApi.getForms(props.functionUnitId)
      forms.value = res.data || []
    } catch {
      forms.value = []
    }
  }

  async function loadActions() {
    try {
      const res = await functionUnitApi.getActions(props.functionUnitId)
      actions.value = res.data || []
    } catch {
      actions.value = []
    }
  }

  syncActionsFromModel()

  return {
    selectedActions,
    globalActions,
    isGlobalAction,
    syncActionsFromModel,
    moveAction,
    reorderActions,
    removeAction,
    handleActionsChange,
    handleFormChange,
    requestableForms,
    handleRequestFormChange,
    actionTypeLabel,
    loadForms,
    loadActions
  }
}
