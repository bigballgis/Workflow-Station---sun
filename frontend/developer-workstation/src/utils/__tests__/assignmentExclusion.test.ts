import { describe, expect, it, vi } from 'vitest'
import { createRequire } from 'node:module'
import type { BpmnElement, BpmnModeler, BpmnExtensionValue } from '@/types/bpmn'
import { assignmentExclusionProblem, emptyAssignmentExclusion, parseAssignmentExclusion, serializeAssignmentExclusion } from '../assignmentExclusion'
import { findInvalidAssignmentExclusionNodes, readAssignmentExclusion, writeAssignmentExclusion } from '../assignmentExclusionBpmn'
import { bpmnIoCustomModdleDescriptor, workflowPlatformModdleDescriptor } from '../customModdle'
import en from '@/i18n/locales/assignmentExclusion/en'
import zhCN from '@/i18n/locales/assignmentExclusion/zh-CN'
import zhTW from '@/i18n/locales/assignmentExclusion/zh-TW'

interface Definition { rootElements: Array<{ flowElements: BpmnElement['businessObject'][] }> }
interface Moddle {
  fromXML(xml: string): Promise<{ rootElement: Definition; warnings: unknown[] }>
  toXML(root: Definition): Promise<{ xml: string }>
  create(type: string, attrs: Record<string, unknown>): BpmnExtensionValue
}
const require = createRequire(import.meta.url)
const ModelerModdle = require(require.resolve('bpmn-moddle', { paths: [require.resolve('bpmn-js/package.json')] })) as new (extensions: Record<string, unknown>) => Moddle

describe('assignment exclusion configuration', () => {
  it('treats absence as default-off and refuses damaged boolean/version data', () => {
    expect(parseAssignmentExclusion(undefined)).toEqual(emptyAssignmentExclusion())
    expect(() => parseAssignmentExclusion({ ...emptyAssignmentExclusion(), excludeInitiator: 'false' })).toThrow()
    expect(() => parseAssignmentExclusion({ ...emptyAssignmentExclusion(), schemaVersion: 2 })).toThrow()
  })

  it('persists portable codes and usernames while omitting inactive backup fields', () => {
    const draft = emptyAssignmentExclusion()
    draft.excludeInitiator = true
    draft.backup.enabled = true
    draft.backup.mode = 'USERS'
    draft.backup.userRefs = ['case-sensitive-account']
    expect(JSON.parse(serializeAssignmentExclusion(draft)).backup).toEqual({ enabled: true, mode: 'USERS', userRefs: ['case-sensitive-account'] })
    draft.backup.mode = 'BU_ROLE'; draft.backup.buSource = 'FIXED_BU'; draft.backup.businessUnitCode = 'BU'; draft.backup.roleCodes = ['REVIEW']
    expect(JSON.parse(serializeAssignmentExclusion(draft)).backup).toEqual({ enabled: true, mode: 'BU_ROLE', buSource: 'FIXED_BU', businessUnitCode: 'BU', roleCodes: ['REVIEW'] })
  })

  it('blocks incomplete node references and unsupported node-BU sources before auto-save', () => {
    const draft = emptyAssignmentExclusion()
    draft.excludeSelectedNodeHandlers = true; draft.selectedNodeKeys = ['deleted-node']
    expect(assignmentExclusionProblem(draft, new Set(['current-node']), true)).toBe('selectNodes')
    draft.selectedNodeKeys = ['current-node']; draft.backup.enabled = true; draft.backup.buSource = 'NODE_ASSIGNMENT_BU'
    expect(assignmentExclusionProblem(draft, new Set(['current-node']), false)).toBe('invalidNodeBu')
  })

  it('preserves prior moddle objects for undo and round-trips through real BPMN XML without accumulating containers', async () => {
    const moddle = new ModelerModdle({ custom: workflowPlatformModdleDescriptor, custom_1: bpmnIoCustomModdleDescriptor })
    const loaded = await moddle.fromXML(`<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:custom="http://workflow.platform/schema/custom" targetNamespace="test"><process id="p"><userTask id="work"><extensionElements><custom:properties><custom:property name="formId" value="99"/></custom:properties></extensionElements></userTask></process></definitions>`)
    expect(loaded.warnings).toEqual([])
    const businessObject = loaded.rootElement.rootElements[0].flowElements[0]
    const element: BpmnElement = { id: 'work', type: 'bpmn:UserTask', businessObject }
    const previous = businessObject.extensionElements
    const modeling = { updateProperties: vi.fn((_node: BpmnElement, props: { extensionElements: BpmnElement['businessObject']['extensionElements'] }) => {
      businessObject.extensionElements = props.extensionElements
    }) }
    const modeler = { get: (service: string) => service === 'moddle' ? moddle : modeling } as BpmnModeler
    const draft = emptyAssignmentExclusion(); draft.excludeInitiator = true
    writeAssignmentExclusion(modeler, element, draft)
    expect(previous?.values).toHaveLength(1)
    expect(readAssignmentExclusion({ ...element, businessObject: { ...businessObject, extensionElements: previous } })).toBeUndefined()
    draft.excludePreviousHandlers = true
    writeAssignmentExclusion(modeler, element, draft)
    expect(businessObject.extensionElements?.values).toHaveLength(2)
    const saved = await moddle.toXML(loaded.rootElement)
    const reloaded = await moddle.fromXML(saved.xml)
    expect(reloaded.warnings).toEqual([])
    const task = reloaded.rootElement.rootElements[0].flowElements[0]
    const policy = parseAssignmentExclusion(readAssignmentExclusion({ ...element, businessObject: task }))
    expect(policy.excludeInitiator).toBe(true)
    expect(policy.excludePreviousHandlers).toBe(true)
    expect(saved.xml).toContain('name="formId" value="99"')
  })

  it('rejects duplicate extension properties instead of choosing the last namespace', () => {
    const property = { $type: 'custom:Property', name: 'assignmentExclusion', value: serializeAssignmentExclusion(emptyAssignmentExclusion()) }
    const node = { id: 'work', type: 'bpmn:UserTask', businessObject: { id: 'work', $type: 'bpmn:UserTask', extensionElements: { values: [
      { $type: 'custom:Properties', property: [property] }, { $type: 'custom_1:Properties', values: [property] }
    ] } } } as BpmnElement
    expect(() => readAssignmentExclusion(node)).toThrow('Duplicate')
    expect(findInvalidAssignmentExclusionNodes({ get: () => ({ getAll: () => [node] }) } as unknown as BpmnModeler)).toEqual(['work'])
  })

  it('keeps all visible locale keys aligned', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(zhCN).sort())
    expect(Object.keys(en).sort()).toEqual(Object.keys(zhTW).sort())
  })
})
