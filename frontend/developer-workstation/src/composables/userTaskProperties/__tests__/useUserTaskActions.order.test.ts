import { describe, it, expect, vi } from 'vitest'
import { reactive, ref } from 'vue'
import { useUserTaskActions } from '../useUserTaskActions'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import type { UserTaskPropertyContext, UserTaskPropsAccessor } from '../types'

vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

function setup(ids: Array<string | number> | string = [12, 22, 35], globals: Array<string | number> | string = [], nested = false, directGlobal = false, directNode = false) {
  const initialIds = typeof ids === 'string' ? [] : ids
  const property = (name: string, value: unknown) => ({ $type: 'custom:Property', name, value: typeof value === 'string' ? value : JSON.stringify(value) })
  const process = { $type: 'bpmn:Process', extensionElements: { values: [{ $type: 'custom:Properties', property: [property('globalActionIds', globals)] }] } }
  if (directGlobal) process.extensionElements.values = [property('globalActionIds', globals)] as never
  const element = { businessObject: { $parent: nested ? { $type: 'bpmn:SubProcess', $parent: process } : process, extensionElements: { values: [
    { $type: 'custom:Properties', property: [property('actionIds', ids), property('actionNames', initialIds.map(id => `Action ${id}`)), property('formId', 99)] },
    { $type: 'custom_1:Properties', values: [property('actionIds', [35]), property('description', 'preserve')] },
    { $type: 'flowable:ExecutionListener', event: 'start' },
  ] } } }
  if (directNode) element.businessObject.extensionElements.values = [property('actionIds', ids), property('actionNames', initialIds.map(id => `Action ${id}`)), { $type: 'custom:Properties', property: [property('formId', 99)] }] as never
  // bpmn-js diagram elements expose non-configurable internals; Vue must not proxy-read them.
  Object.defineProperty(element, 'labels', { value: [], configurable: false, writable: false, enumerable: true })
  const history: unknown[] = []
  let redoValue: unknown
  const modeling = { updateProperties: vi.fn((_element, props) => {
    history.push(element.businessObject.extensionElements)
    element.businessObject.extensionElements = props.extensionElements
  }) }
  const modeler = { get: (name: string) => name === 'moddle'
    ? { create: ($type: string, attrs: object) => ({ $type, ...attrs }) } : modeling }
  const ctx = {
    actionIds: ref([...initialIds]), actions: ref([12, 22, 35, 48, 'legacy-save', 'reject'].map(id => ({ id, actionName: `Action ${id}` }))),
    forms: ref([]), requestFormId: ref(null), requestFormName: ref(''), updateExtProp: vi.fn(), t: (k: string) => k,
  } as unknown as UserTaskPropertyContext
  const api = useUserTaskActions({ element: reactive(element), modeler } as unknown as UserTaskPropsAccessor, ctx)
  return { api, ctx, element, modeling,
    undo() { redoValue = element.businessObject.extensionElements; element.businessObject.extensionElements = history.pop() as never; api.syncActionsFromModel() },
    redo() { element.businessObject.extensionElements = redoValue as never; api.syncActionsFromModel() },
    read() { return getExtensionProperties(element as never) },
  }
}

describe('node action binding order', () => {
  it.each([[12, 12, 22], [' 12 ', 12, '22', 22]])('deduplicates imported node IDs without changing stored bindings on load: %j', (...ids) => {
    const test = setup(ids)
    expect(test.api.selectedActions.value.map(action => String(action.id))).toEqual(['12', '22'])
    expect(test.modeling.updateProperties).not.toHaveBeenCalled()
    test.api.reorderActions([{ id: 22 }, { id: 12 }])
    expect(test.read().actionIds).toEqual([22, 12])
    expect(test.read().actionNames).toEqual(['Action 22', 'Action 12'])
    expect(test.modeling.updateProperties).toHaveBeenCalledOnce()
    test.undo()
    expect(test.api.selectedActions.value.map(action => String(action.id))).toEqual(['12', '22'])
    test.redo()
    expect(test.read().actionIds).toEqual([22, 12])
  })
  it('deduplicates surviving and newly selected bindings and keeps globals separate', () => {
    const test = setup([12, '12', 22, 22, 48], [48])
    test.api.handleActionsChange([12, '12', 22, 35, '35', 48])
    expect(test.read().actionIds).toEqual([12, 22, 35])
    expect(test.read().actionNames).toEqual(['Action 12', 'Action 22', 'Action 35'])
    expect(test.api.globalActions.value.map(action => action.id)).toEqual([48])
  })
  it('resolves trimmed JSON IDs and reorders them with undo support', () => {
    const test = setup('[" 12 ",22]', [])
    expect(test.api.selectedActions.value.map(a => a.name)).toEqual(['Action 12','Action 22'])
    test.api.moveAction(1, -1)
    expect(test.read().actionIds).toEqual([22,12])
    test.undo()
    expect(test.api.selectedActions.value.map(a => a.id)).toEqual(['12',22])
  })

  it.each(['[reject,48]', 'reject,48'])('loads legacy global IDs %s on an otherwise empty node', globals => {
    const test = setup([], globals)
    expect(test.api.globalActions.value.map(action => action.id)).toEqual(['reject', '48'])
    expect(test.api.selectedActions.value).toEqual([])
    expect(test.modeling.updateProperties).not.toHaveBeenCalled()
  })
  it('reorders and changes legacy node selections while preserving undo and globals', () => {
    const test = setup('[35,legacy-save,22]', '[reject,48]', true, true, true)
    expect(test.api.selectedActions.value.map(action => action.id)).toEqual(['35', 'legacy-save', '22'])
    test.api.moveAction(1, -1)
    expect(test.read().actionIds).toEqual(['legacy-save', 35, 22])
    test.undo()
    expect(test.api.selectedActions.value.map(action => action.id)).toEqual(['35', 'legacy-save', '22'])
    test.api.handleActionsChange(['legacy-save', 35, 12, 'reject'])
    expect(test.read().actionIds).toEqual([35, 'legacy-save', 12])
    expect(test.api.globalActions.value.map(action => action.id)).toEqual(['reject', '48'])
    expect(test.read().formId).toBe(99)
  })
  it('replaces direct legacy action properties when saving a new order and supports undo', () => {
    const test = setup([12, 22, 35], [], false, false, true)
    test.api.moveAction(0, 1)
    expect(test.read().actionIds).toEqual([22, 12, 35])
    expect(test.element.businessObject.extensionElements.values.some(ext => 'name' in ext && ext.name === 'actionIds')).toBe(false)
    expect(test.read().formId).toBe(99)
    test.undo()
    expect(test.read().actionIds).toEqual([12, 22, 35])
  })
  it('inherits global IDs from legacy direct BPMN extension properties', () => {
    const test = setup([12, 22], ['12', 48], false, true)
    expect(test.api.globalActions.value.map(a => a.id)).toEqual(['12', 48])
    expect(test.api.selectedActions.value.map(a => a.id)).toEqual([22])
  })
  it('shows global actions separately, including on nested tasks, without writing bindings', () => {
    const test = setup([12, 22, 35], ['12', 48, '48'], true)
    expect(test.api.selectedActions.value.map(a => a.id)).toEqual([22, 35])
    expect(test.api.globalActions.value.map(a => a.id)).toEqual(['12', 48])
    expect(test.api.isGlobalAction(12)).toBe(true)
    expect(test.read().actionIds).toEqual([12, 22, 35])
    expect(test.modeling.updateProperties).not.toHaveBeenCalled()
  })
  it('only moves node actions and never adds inherited actions to node selections', () => {
    const test = setup([12, 22, 35], [12, 48])
    test.api.moveAction(1, -1)
    expect(test.read().actionIds).toEqual([35, 22])
    expect(test.api.globalActions.value.map(a => a.id)).toEqual([12, 48])
    test.undo()
    expect(test.api.selectedActions.value.map(a => a.id)).toEqual([22, 35])
    test.api.handleActionsChange([22, 12, 48])
    expect(test.read().actionIds).toEqual([22])
  })
  it('shows inherited actions when no actions are bound to the node', () => {
    const test = setup([], [48, '12'])
    expect(test.api.selectedActions.value).toEqual([])
    expect(test.api.globalActions.value.map(a => a.name)).toEqual(['Action 48', 'Action 12'])
  })
  it('drops a row across multiple positions in one undoable command', () => {
    const test = setup()
    test.api.reorderActions([{ id: 35 }, { id: 12 }, { id: 22 }])
    expect(test.read().actionIds).toEqual([35, 12, 22])
    expect(test.read().actionNames).toEqual(['Action 35', 'Action 12', 'Action 22'])
    expect(test.modeling.updateProperties).toHaveBeenCalledOnce()
    test.undo()
    expect(test.ctx.actionIds.value).toEqual([12, 22, 35])
    test.redo()
    expect(test.ctx.actionIds.value).toEqual([35, 12, 22])
  })
  it('does not create an undo entry when the drag order stays the same', () => {
    const test = setup()
    test.api.reorderActions([{ id: '12' }, { id: 22 }, { id: 35 }])
    expect(test.modeling.updateProperties).not.toHaveBeenCalled()
  })
  it('keeps another node unchanged when dragging a row', () => {
    const a = setup()
    const b = setup([35, 22, 12])
    a.api.reorderActions([{ id: 22 }, { id: 35 }, { id: 12 }])
    expect(a.read().actionIds).toEqual([22, 35, 12])
    expect(b.read().actionIds).toEqual([35, 22, 12])
  })
  it('matches imported string IDs with numeric catalog IDs without reordering survivors', () => {
    const test = setup(['35', '12', 22])
    test.api.handleActionsChange([22, 35, 48])
    expect(test.read().actionIds).toEqual([35, 22, 48])
    expect(test.read().actionNames).toEqual(['Action 35', 'Action 22', 'Action 48'])
  })
  it('moves IDs and names in one command without mutating the previous extension tree', () => {
    const test = setup()
    const old = JSON.stringify(test.element.businessObject.extensionElements)
    const previous = test.element.businessObject.extensionElements
    test.api.moveAction(2, -1)
    expect(test.read().actionIds).toEqual([12, 35, 22])
    expect(test.read().actionNames).toEqual(['Action 12', 'Action 35', 'Action 22'])
    expect(JSON.stringify(previous)).toBe(old)
    expect(test.read().formId).toBe(99)
    expect(test.read().description).toBe('preserve')
    expect(test.modeling.updateProperties).toHaveBeenCalledOnce()
    test.undo()
    expect(test.ctx.actionIds.value).toEqual([12, 22, 35])
    test.redo()
    expect(test.ctx.actionIds.value).toEqual([12, 35, 22])
  })
  it('preserves survivors and appends newly selected actions', () => {
    const test = setup([35, 12, 22])
    test.api.handleActionsChange([48, 22, 35])
    expect(test.read().actionIds).toEqual([35, 22, 48])
    test.api.removeAction(22)
    expect(test.read().actionIds).toEqual([35, 48])
    test.api.handleActionsChange([22, 35, 48])
    expect(test.read().actionIds).toEqual([35, 48, 22])
  })
  it('supports empty nodes, clears and boundary movement', () => {
    const test = setup([])
    test.api.handleActionsChange([12])
    test.api.moveAction(0, -1)
    test.api.moveAction(0, 1)
    expect(test.read().actionIds).toEqual([12])
    test.api.removeAction(12)
    expect(test.read().actionIds).toEqual([])
    expect(test.read().actionNames).toEqual([])
  })
  it('does not persist unresolved IDs or silently drop their names', () => {
    const test = setup()
    test.api.handleActionsChange([12, 999])
    expect(test.modeling.updateProperties).not.toHaveBeenCalled()
    expect(test.ctx.actionIds.value).toEqual([12, 22, 35])
  })
  it('keeps different orders independent across nodes sharing the same actions', () => {
    const a = setup([12, 22, 35])
    const b = setup([35, 12, 22])
    a.api.moveAction(0, 1)
    expect(a.read().actionIds).toEqual([22, 12, 35])
    expect(b.read().actionIds).toEqual([35, 12, 22])
  })
})
