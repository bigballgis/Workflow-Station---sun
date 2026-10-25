import { describe, it, expect, vi } from 'vitest'
import { createRequire } from 'node:module'
import { ref } from 'vue'
import { useActionNodeBinding } from '../useActionNodeBinding'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import { readXmlActionProperties } from '@/utils/actionBpmnProperties'
import { bpmnIoCustomModdleDescriptor, workflowPlatformModdleDescriptor } from '@/utils/customModdle'
import type { ActionDefinition } from '@/api/functionUnit'
import type { BpmnElement } from '@/types/bpmn'

vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), success: vi.fn(), error: vi.fn() } }))
// Use the same moddle version as the actual Modeler, without adding an untyped static import.
const require = createRequire(import.meta.url)
const ModelerModdle = require(require.resolve('bpmn-moddle', { paths: [require.resolve('bpmn-js/package.json')] }))
const opening = '<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" xmlns:l="http://custom.bpmn.io/schema" targetNamespace="binding"><b:process id="p">'
const props = (ids: number[]) => `<c:properties><c:property name="actionIds" value='${JSON.stringify(ids)}'/><c:property name="actionNames" value='${JSON.stringify(ids.map(id => `Action ${id}`))}'/></c:properties>`
const close = '</b:process></b:definitions>'
function binding(xml: string, id = 12) {
  const store = { process: { bpmnXml: xml }, actions: [12, 22, 35, 48].map(id => ({ id, actionName: `Action ${id}` } as ActionDefinition)), fetchProcess: vi.fn(), saveProcess: vi.fn(async (_id, payload) => { store.process.bpmnXml = payload.bpmnXml }) }
  const api = useActionNodeBinding({ functionUnitId: 1, selectedAction: ref(store.actions.find(action => action.id === id)!), store, t: k => k })
  api.parseActionBindingsFromBpmn()
  api.loadActionBinding(id)
  return { store, api }
}
async function roundTrip(xml: string) {
  const moddle = new ModelerModdle({ custom: workflowPlatformModdleDescriptor, custom_1: bpmnIoCustomModdleDescriptor })
  const { rootElement, warnings } = await moddle.fromXML(xml)
  const { xml: saved } = await moddle.toXML(rootElement)
  const process = rootElement.rootElements[0]
  return { warnings, saved, process: getExtensionProperties({ businessObject: process } as BpmnElement), node: getExtensionProperties({ businessObject: process.flowElements[0] } as BpmnElement) }
}
describe('Action bindings survive the Modeler schema and have one effective scope', () => {
  it('repairs a previous mixed-namespace binding on unchanged save', async () => {
    const x = binding(`${opening}<b:userTask id="a"><b:extensionElements><c:properties><c:property name="formId" value="99"/><l:property name="actionIds" value="[12]"/><l:property name="actionNames" value='["Action 12"]'/></c:properties></b:extensionElements></b:userTask>${close}`)
    await x.api.handleSaveBinding()
    const out = await roundTrip(x.store.process.bpmnXml)
    expect(out.warnings).toEqual([])
    expect(out.node.actionIds).toEqual([12])
    expect(out.node.formId).toBe(99)
  })

  it('reads inherited-default-namespace legacy bindings and rewrites the edited pair canonically', async () => {
    const x = binding('<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="legacy"><process id="p"><userTask id="a"><extensionElements><properties><property name="actionIds" value="[22]"/><property name="actionNames" value=\'["Action 22"]\'/></properties></extensionElements></userTask></process></definitions>', 22)
    expect(x.api.selectedNodeIds.value).toEqual(['a'])
    x.api.bindingType.value = 'global'
    await x.api.handleSaveBinding()
    const out = await roundTrip(x.store.process.bpmnXml)
    expect(out.warnings).toEqual([])
    expect(out.node.actionIds).toEqual([])
    expect(out.process.globalActionIds).toEqual([22])
  })
  it('uses the same property/values and namespace precedence in DOM and moddle, including empty lists', async () => {
    const xml = `${opening}<b:extensionElements><l:properties><l:property name="globalActionIds" value="[48]"/></l:properties><c:properties><c:property name="globalActionIds" value="[]"/></c:properties></b:extensionElements><b:userTask id="a"><b:extensionElements><c:properties><c:values name="actionIds" value="[35,12]"/><c:property name="actionIds" value="[22]"/></c:properties><l:property name="actionIds" value="[48]"/></b:extensionElements></b:userTask>${close}`
    const doc = new DOMParser().parseFromString(xml, 'text/xml')
    const node = doc.getElementsByTagNameNS('*', 'userTask')[0]
    expect(JSON.parse(readXmlActionProperties(node).actionIds)).toEqual([35, 12])
    const out = await roundTrip(xml)
    expect(out.node.actionIds).toEqual([35, 12])
    expect(out.process.globalActionIds).toEqual([])
    expect((await roundTrip(out.saved)).node.actionIds).toEqual([35, 12])
  })
  it('normalizes only the edited pair and preserves unrelated legacy form metadata', async () => {
    const x = binding(`${opening}<b:userTask id="a"><b:extensionElements><l:properties><l:values name="formId" value="99"/><l:property name="actionIds" value="[48]"/></l:properties>${props([35, 22])}</b:extensionElements></b:userTask>${close}`)
    x.api.selectedNodeIds.value = ['a']
    await x.api.handleSaveBinding()
    const doc = new DOMParser().parseFromString(x.store.process.bpmnXml, 'text/xml')
    expect(Array.from(doc.getElementsByTagNameNS('*', '*')).filter(prop => prop.getAttribute('name') === 'actionIds')).toHaveLength(1)
    const out = await roundTrip(x.store.process.bpmnXml)
    expect(out.node.actionIds).toEqual([35, 22, 12])
    expect(out.node.actionNames).toEqual(['Action 35', 'Action 22', 'Action 12'])
    expect(out.node.formId).toBe(99)
    expect(out.warnings).toEqual([])
  })
  it.each(['node', 'global'] as const)('preserves the first %s binding in an existing platform properties container', async mode => {
    const x = binding(`${opening}<b:extensionElements><c:properties><c:property name="description" value="keep"/></c:properties></b:extensionElements><b:userTask id="a"><b:extensionElements><c:properties><c:property name="formId" value="99"/></c:properties></b:extensionElements></b:userTask>${close}`)
    x.api.bindingType.value = mode
    x.api.selectedNodeIds.value = ['a']
    await x.api.handleSaveBinding()
    expect(x.store.saveProcess).toHaveBeenCalledOnce()
    const out = await roundTrip(x.store.process.bpmnXml)
    expect(out.warnings).toEqual([])
    expect(mode === 'node' ? out.node.actionIds : out.process.globalActionIds).toEqual([12])
    expect(out.node.formId).toBe(99)
    expect(out.process.description).toBe('keep')
    expect((await roundTrip(out.saved)).warnings).toEqual([])
  })
  it('agrees with the node panel on the last platform binding and preserves it on unchanged save', async () => {
    const x = binding(`${opening}<b:userTask id="a"><b:extensionElements>${props([12, 22])}${props([35, 12])}</b:extensionElements></b:userTask>${close}`, 35)
    expect(x.api.selectedNodeIds.value).toEqual(['a'])
    expect(x.api.getActionBoundNodes(22)).toEqual([])
    await x.api.handleSaveBinding()
    expect((await roundTrip(x.store.process.bpmnXml)).node.actionIds).toEqual([35, 12])
  })
  it('does not promote a legitimate node id process into global scope', async () => {
    const x = binding(`${opening}<b:userTask id="process"><b:extensionElements>${props([12])}</b:extensionElements></b:userTask><b:userTask id="other"/>${close}`)
    expect(x.api.bindingType.value).toBe('node')
    expect(x.api.selectedNodeIds.value).toEqual(['process'])
    await x.api.handleSaveBinding()
    const out = await roundTrip(x.store.process.bpmnXml)
    expect(out.process.globalActionIds).toBeUndefined()
    expect(out.node.actionIds).toEqual([12])
  })
  it('keeps both a real node id process and an independent global binding for the same action', () => {
    const x = binding(`${opening}<b:extensionElements><c:properties><c:property name="globalActionIds" value="[12]"/></c:properties></b:extensionElements><b:userTask id="process"><b:extensionElements>${props([12])}</b:extensionElements></b:userTask>${close}`)
    expect(x.api.bindingType.value).toBe('global')
    expect(x.api.selectedNodeIds.value).toEqual(['process'])
    expect(x.api.getActionBoundNodes(12)).toHaveLength(2)
    expect(x.api.getActionBindingSummary(12)).toHaveLength(1)
  })
})
