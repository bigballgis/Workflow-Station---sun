import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { useActionNodeBinding } from '../useActionNodeBinding'
import type { ActionDefinition } from '@/api/functionUnit'

vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() } }))

function task(id: string, ids: Array<string | number>) {
  return `<bpmn:userTask id="${id}"><bpmn:extensionElements><custom:properties><custom:property name="actionIds" value='${JSON.stringify(ids)}'/><custom:property name="actionNames" value='${JSON.stringify(ids.map(n => `Action ${n}`))}'/></custom:properties></bpmn:extensionElements></bpmn:userTask>`
}

async function saveBinding(selected: string[], mode: 'node' | 'global' = 'node', prefixed = true) {
  const process = { bpmnXml: `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://custom.bpmn.io/schema"><bpmn:process id="p">${task('a', [12, 22, 35])}${task('b', [35, 12, 22])}${task('c', [22, 35])}</bpmn:process></bpmn:definitions>` }
  if (!prefixed) process.bpmnXml = process.bpmnXml.replaceAll('bpmn:', '').replaceAll('custom:', '')
  const store = {
    process,
    actions: [12, 22, 35].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)),
    fetchProcess: vi.fn(),
    saveProcess: vi.fn(async (_id, payload) => { process.bpmnXml = payload.bpmnXml as string }),
  }
  const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref({ id: 12, actionName: 'Action 12' } as ActionDefinition), store, t: k => k })
  binding.bindingType.value = mode
  binding.selectedNodeIds.value = selected
  await binding.handleSaveBinding()
  expect(store.saveProcess).toHaveBeenCalledOnce()
  const doc = new DOMParser().parseFromString(process.bpmnXml, 'text/xml')
  function read(nodeId: string, name: string) {
    const node = Array.from(doc.getElementsByTagNameNS('*', 'userTask')).find(n => n.id === nodeId)!
    const prop = Array.from(node.getElementsByTagNameNS('*', 'property')).find(p => p.getAttribute('name') === name)!
    return JSON.parse(prop.getAttribute('value')!)
  }
  return { read, doc }
}

describe('Action Design binding preserves node button order', () => {
  it('reads legacy global and node ID lists and preserves other bindings when switching scope', async () => {
    const process = { bpmnXml: `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow.platform/schema/custom"><bpmn:process id="p"><bpmn:extensionElements><custom:properties><custom:property name="globalActionIds" value="12,35"/></custom:properties></bpmn:extensionElements>${task('a', [22]).replace('value=\'[22]\'', 'value="[legacy-save,22]"')}</bpmn:process></bpmn:definitions>` }
    const store = { process, actions: [12, 22, 35, 'legacy-save'].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)), fetchProcess: vi.fn(), saveProcess: vi.fn(async (_id, payload) => { process.bpmnXml = payload.bpmnXml }) }
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref({ id: 12 } as ActionDefinition), store, t: k => k })
    binding.parseActionBindingsFromBpmn()
    expect(binding.getActionBindingSummary(12).map(node => node.id)).toEqual(['process'])
    expect(binding.getActionBindingSummary('legacy-save').map(node => node.id)).toEqual(['a'])
    binding.bindingType.value = 'node'
    binding.selectedNodeIds.value = ['a']
    await binding.handleSaveBinding()
    expect(store.saveProcess).toHaveBeenCalledOnce()
    const doc = new DOMParser().parseFromString(process.bpmnXml, 'text/xml')
    const values = Array.from(doc.getElementsByTagNameNS('*', 'property'))
    expect(JSON.parse(values.find(p => p.getAttribute('name') === 'globalActionIds')!.getAttribute('value')!)).toEqual(['35'])
    expect(JSON.parse(values.find(p => p.getAttribute('name') === 'actionIds')!.getAttribute('value')!)).toEqual(['legacy-save', '22', 12])
  })
  it('summarizes a global scope without showing legacy node tags or losing editor selections', () => {
    const store = { actions: [], process: null, fetchProcess: vi.fn(), saveProcess: vi.fn() }
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref(null), store, t: k => k })
    const global = { scope: 'global' as const, id: 'process', name: 'Process Global' }
    const a = { scope: 'node' as const, id: 'a', name: 'Transaction Assignment' }
    const b = { scope: 'node' as const, id: 'b', name: 'Review' }
    binding.actionNodeBindings.value.set(12, [global, a])
    binding.actionNodeBindings.value.set('12', [b])
    binding.actionNodeBindings.value.set(22, [a, b])
    expect(binding.getActionBindingSummary(12)).toEqual([global])
    expect(binding.getActionBindingSummary('12')).toEqual([global])
    expect(binding.getActionBindingSummary(22)).toEqual([a, b])
    expect(binding.getActionBindingSummary(99)).toEqual([])
    binding.loadActionBinding(12)
    expect(binding.selectedNodeIds.value).toEqual(['a', 'b'])
    expect(binding.getActionBoundNodes(12)).toEqual([global, a, b])
    expect(store.saveProcess).not.toHaveBeenCalled()
  })
  it('switches global to selected nodes explicitly without changing other bindings', async () => {
    const process = { bpmnXml: `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://custom.bpmn.io/schema"><bpmn:process id="p"><bpmn:extensionElements><custom:properties><custom:property name="globalActionIds" value='[12,35]'/></custom:properties></bpmn:extensionElements>${task('a', [22])}${task('b', [35, 22])}</bpmn:process></bpmn:definitions>` }
    const store = { process, actions: [12, 22, 35].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)), fetchProcess: vi.fn(), saveProcess: vi.fn(async (_id, payload) => { process.bpmnXml = payload.bpmnXml }) }
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref({ id: 12 } as ActionDefinition), store, t: k => k })
    binding.parseActionBindingsFromBpmn()
    binding.loadActionBinding(12)
    binding.bindingType.value = 'node'
    binding.selectedNodeIds.value = ['a']
    await binding.handleSaveBinding()
    const doc = new DOMParser().parseFromString(process.bpmnXml, 'text/xml')
    const global = Array.from(doc.getElementsByTagNameNS('*', 'property')).find(p => p.getAttribute('name') === 'globalActionIds')!
    expect(JSON.parse(global.getAttribute('value')!)).toEqual([35])
    expect(binding.getActionBoundNodes(12).map(n => n.id)).toEqual(['a'])
    const nodes = Array.from(doc.getElementsByTagNameNS('*', 'userTask')).map(node => JSON.parse(node.getElementsByTagNameNS('*', 'property')[0].getAttribute('value')!))
    expect(nodes).toEqual([[22, 12], [35, 22]])
  })
  it('reads direct extension properties without losing existing node bindings on save', async () => {
    const process = { bpmnXml: `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://custom.bpmn.io/schema"><bpmn:process id="p">${task('a', [12, 22]).replaceAll('<custom:properties>', '').replaceAll('</custom:properties>', '')}</bpmn:process></bpmn:definitions>` }
    const store = { process, actions: [12, 22].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)), fetchProcess: vi.fn(), saveProcess: vi.fn(async (_id, payload) => { process.bpmnXml = payload.bpmnXml }) }
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref({ id: 12 } as ActionDefinition), store, t: k => k })
    binding.parseActionBindingsFromBpmn()
    binding.loadActionBinding(12)
    expect(binding.selectedNodeIds.value).toEqual(['a'])
    await binding.handleSaveBinding()
    expect(process.bpmnXml).toContain('[12,22]')
  })
  it('merges numeric and string ID groups and preserves both nodes on unchanged save', async () => {
    const process = { bpmnXml: `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://custom.bpmn.io/schema"><bpmn:process id="p">${task('a', [12, 22])}${task('b', ['12', 35])}</bpmn:process></bpmn:definitions>` }
    const store = { process, actions: [12, 22, 35].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)), fetchProcess: vi.fn(), saveProcess: vi.fn(async (_id, payload) => { process.bpmnXml = payload.bpmnXml }) }
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref({ id: 12 } as ActionDefinition), store, t: k => k })
    binding.parseActionBindingsFromBpmn()
    binding.loadActionBinding(12)
    expect(binding.selectedNodeIds.value).toEqual(['a', 'b'])
    await binding.handleSaveBinding()
    expect(store.saveProcess).toHaveBeenCalledOnce()
    expect(process.bpmnXml).toContain('[&quot;12&quot;,35]')
  })
  it('keeps redundant legacy node bindings when saving an unchanged global scope', async () => {
    const process = { bpmnXml: `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://custom.bpmn.io/schema"><bpmn:process id="p"><bpmn:extensionElements><custom:properties><custom:property name="globalActionIds" value='[12]'/></custom:properties></bpmn:extensionElements>${task('a', [12, 22])}${task('b', [35, '12'])}</bpmn:process></bpmn:definitions>` }
    const store = { process, actions: [12, 22, 35].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)), fetchProcess: vi.fn(), saveProcess: vi.fn(async (_id, payload) => { process.bpmnXml = payload.bpmnXml }) }
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref({ id: 12 } as ActionDefinition), store, t: k => k })
    binding.parseActionBindingsFromBpmn()
    binding.loadActionBinding(12)
    expect(binding.bindingType.value).toBe('global')
    await binding.handleSaveBinding()
    const doc = new DOMParser().parseFromString(process.bpmnXml, 'text/xml')
    const orders = Array.from(doc.getElementsByTagNameNS('*', 'userTask')).map(node => JSON.parse(node.getElementsByTagNameNS('*', 'property')[0].getAttribute('value')!))
    expect(orders).toEqual([[12, 22], [35, '12']])
  })
  it('loads legacy string IDs as the same bindings as numeric designer IDs', () => {
    const binding = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref(null), store: { actions: [], process: null, fetchProcess: vi.fn(), saveProcess: vi.fn() }, t: k => k })
    binding.actionNodeBindings.value.set('12', [{ scope: 'node', id: 'a', name: 'Review' }])
    binding.loadActionBinding(12)
    expect(binding.selectedNodeIds.value).toEqual(['a'])
  })
  it('does not move an unchanged action to the tail in legacy unprefixed XML', async () => {
    const { read } = await saveBinding(['a', 'b'], 'node', false)
    expect(read('a', 'actionIds')).toEqual([12, 22, 35])
    expect(read('b', 'actionIds')).toEqual([35, 12, 22])
  })
  it('keeps an existing action at its different positions in two nodes', async () => {
    const { read } = await saveBinding(['a', 'b'])
    expect(read('a', 'actionIds')).toEqual([12, 22, 35])
    expect(read('b', 'actionIds')).toEqual([35, 12, 22])
    expect(read('a', 'actionNames')).toEqual(['Action 12', 'Action 22', 'Action 35'])
  })
  it('only removes deselected bindings and appends newly selected ones', async () => {
    const { read } = await saveBinding(['b', 'c'])
    expect(read('a', 'actionIds')).toEqual([22, 35])
    expect(read('a', 'actionNames')).toEqual(['Action 22', 'Action 35'])
    expect(read('b', 'actionIds')).toEqual([35, 12, 22])
    expect(read('c', 'actionIds')).toEqual([22, 35, 12])
    expect(read('c', 'actionNames')).toEqual(['Action 22', 'Action 35', 'Action 12'])
  })
  it('removes node bindings when switching to global', async () => {
    const { read, doc } = await saveBinding([], 'global')
    expect(read('a', 'actionIds')).toEqual([22, 35])
    expect(read('b', 'actionIds')).toEqual([35, 22])
    const global = Array.from(doc.getElementsByTagNameNS('*', 'property')).find(p => p.getAttribute('name') === 'globalActionIds')!
    expect(JSON.parse(global.getAttribute('value')!)).toEqual([12])
  })
})
