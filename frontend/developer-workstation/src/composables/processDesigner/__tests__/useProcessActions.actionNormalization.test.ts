import { describe, it, expect, vi } from 'vitest'
import { createRequire } from 'node:module'
import { ref } from 'vue'
import { useProcessActions } from '../useProcessActions'
import { getExtensionProperties } from '@/utils/bpmnExtensions'
import { bpmnIoCustomModdleDescriptor, workflowPlatformModdleDescriptor } from '@/utils/customModdle'
import type { BpmnElement } from '@/types/bpmn'
vi.mock('@/api/functionUnit', () => ({ functionUnitApi: {} }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn(), success: vi.fn() }, ElMessageBox: {} }))
const require = createRequire(import.meta.url)
const ModelerModdle = require(require.resolve('bpmn-moddle', { paths: [require.resolve('bpmn-js/package.json')] }))

describe('legacy Action bindings enter the Modeler without being discarded', () => {
  it.each(['mixed', 'default'] as const)('normalizes %s properties during reload/discard', async shape => {
    const extension = shape === 'mixed'
      ? '<c:properties><c:property name="formId" value="99"/><l:property name="actionIds" value="[12,22]"/></c:properties>'
      : '<properties xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><property name="actionIds" value="[12,22]"/></properties>'
    const xml = `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" xmlns:l="http://custom.bpmn.io/schema" targetNamespace="legacy"><b:process id="p"><b:extensionElements><c:properties><l:property name="globalActionIds" value="[35]"/></c:properties></b:extensionElements><b:userTask id="a"><b:extensionElements>${extension}</b:extensionElements></b:userTask></b:process></b:definitions>`
    const moddle = new ModelerModdle({ custom: workflowPlatformModdleDescriptor, custom_1: bpmnIoCustomModdleDescriptor })
    let rootElement!: { rootElements: Array<BpmnElement['businessObject'] & { flowElements: Array<BpmnElement['businessObject']> }> }
    let warnings: unknown[] = []
    const modeler = {
      importXML: vi.fn(async (source: string) => { ({ rootElement, warnings } = await moddle.fromXML(source)) }),
      saveXML: async () => moddle.toXML(rootElement),
    }
    const api = useProcessActions({ functionUnitId: 1, getModeler: () => modeler, store: { process: { bpmnXml: xml }, fetchProcess: vi.fn() }, showImportDialog: ref(false), importXml: ref(''), diagramIsFallback: ref(false), t: (k: string) => k } as never)
    await api.discardChanges()
    expect(warnings).toEqual([])
    const process = rootElement.rootElements[0]
    expect(getExtensionProperties({ id: process.id, type: process.$type, businessObject: process }).globalActionIds).toEqual([35])
    const node = getExtensionProperties({ id: process.flowElements[0].id, type: process.flowElements[0].$type, businessObject: process.flowElements[0] })
    expect(node.actionIds).toEqual([12,22])
    if (shape === 'mixed') expect(node.formId).toBe(99)
    const saved = await modeler.saveXML()
    const reloaded = await moddle.fromXML(saved.xml)
    expect(reloaded.warnings).toEqual([])
  })
})
