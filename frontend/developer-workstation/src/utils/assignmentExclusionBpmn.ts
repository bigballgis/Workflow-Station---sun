import { toRaw } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import type { AssignmentExclusionDraft } from '@/types/assignmentExclusion'
import { assignmentExclusionProblem, parseAssignmentExclusion, serializeAssignmentExclusion } from './assignmentExclusion'
import { getExtensionProperties } from './bpmnExtensions'

interface Property { $type: string; name?: string; value?: string }
interface Extension { $type: string; property?: Property[]; values?: Property[] }
interface Moddle { create(type: string, attrs: Record<string, unknown>): Extension }
interface Modeling { updateProperties(element: BpmnElement, properties: Record<string, unknown>): void }
const containers = new Set(['custom:Properties', 'custom_1:Properties'])
interface Owner { $type: string; $parent?: Owner }

export function assignmentProcessOwner(element: BpmnElement): object | undefined {
  let owner: Owner | undefined = toRaw(element).businessObject as Owner
  const seen = new Set<Owner>()
  while (owner && owner.$type !== 'bpmn:Process') {
    if (seen.has(owner)) throw new Error('Invalid BPMN parent cycle')
    seen.add(owner); owner = owner.$parent
  }
  return owner
}

export function assignmentNodeBuSupported(element: BpmnElement): boolean {
  const ext = getExtensionProperties(element) as Record<string, unknown>
  return ext.assigneeType === 'BU_ROLE' || ext.assigneeType === 'FIXED_BU_ROLE'
    || ext.assigneeType === 'ELEMENT_VARIABLE' && (typeof ext.buField === 'string' && !!ext.buField.trim() || ext.assigneeMode === 'role')
}

export function findInvalidAssignmentExclusionNodes(modeler: BpmnModeler): string[] {
  const nodes = (modeler.get('elementRegistry') as { getAll(): BpmnElement[] }).getAll().filter(node => node.type === 'bpmn:UserTask')
  const groups = new Map<object, Set<string>>()
  for (const node of nodes) {
    const owner = assignmentProcessOwner(node)
    if (owner) { const keys = groups.get(owner) ?? new Set<string>(); keys.add(node.id); groups.set(owner, keys) }
  }
  return nodes.filter(node => {
    try {
      const raw = readAssignmentExclusion(node)
      if (raw === undefined) return false
      const owner = assignmentProcessOwner(node)
      return !owner || !!assignmentExclusionProblem(parseAssignmentExclusion(raw), groups.get(owner) ?? new Set(), assignmentNodeBuSupported(node))
    } catch { return true }
  }).map(node => node.id)
}

export function readAssignmentExclusion(element: BpmnElement): string | undefined {
  const values = (toRaw(element).businessObject.extensionElements?.values ?? []) as Extension[]
  const properties = values.flatMap(ext => containers.has(ext.$type) ? [...(ext.property ?? []), ...(ext.values ?? [])] : [ext as Property])
    .filter(entry => entry.name === 'assignmentExclusion')
  if (properties.length > 1) throw new Error('Duplicate assignment exclusion property')
  if (properties.length === 0) return undefined
  if (typeof properties[0].value !== 'string') throw new Error('Invalid assignment exclusion property')
  return properties[0].value
}

/** Replace extension objects instead of mutating the old command value, so undo can restore the exact prior policy. */
export function writeAssignmentExclusion(modeler: BpmnModeler, element: BpmnElement, draft: AssignmentExclusionDraft): void {
  element = toRaw(element)
  const moddle = modeler.get('moddle') as Moddle
  const modeling = modeler.get('modeling') as Modeling
  const previous = (element.businessObject.extensionElements?.values ?? []) as Extension[]
  const values: Extension[] = []
  for (const extension of previous) {
    if (!containers.has(extension.$type)) {
      if ((extension as Property).name !== 'assignmentExclusion') values.push(extension)
      continue
    }
    const property = (extension.property ?? []).filter(entry => entry.name !== 'assignmentExclusion')
    const children = (extension.values ?? []).filter(entry => entry.name !== 'assignmentExclusion')
    if (property.length || children.length) values.push(moddle.create(extension.$type, { property, values: children }))
  }
  values.push(moddle.create('custom:Properties', { property: [moddle.create('custom:Property', {
    name: 'assignmentExclusion', value: serializeAssignmentExclusion(draft)
  })] }))
  const extensionElements = moddle.create('bpmn:ExtensionElements', { values })
  modeling.updateProperties(element, { extensionElements })
}
