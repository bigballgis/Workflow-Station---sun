/**
 * Parse BPMN task → formId bindings (custom extension properties).
 * Covers all BPMN 2.0 task types: task, userTask, serviceTask, scriptTask,
 * manualTask, sendTask, receiveTask, businessRuleTask.
 * Shared by Form Designer and Process Debug.
 */

/** BPMN 2.0 task element local names that can carry form bindings. */
export const TASK_LOCAL_NAMES = [
  'task',
  'userTask',
  'serviceTask',
  'scriptTask',
  'manualTask',
  'sendTask',
  'receiveTask',
  'businessRuleTask',
] as const

export type TaskLocalName = (typeof TASK_LOCAL_NAMES)[number]

/** Check whether a BPMN element local-name is a task-like element that can carry form bindings. */
export function isTaskElement(ln: string | undefined): ln is TaskLocalName {
  return !!ln && TASK_LOCAL_NAMES.includes(ln as TaskLocalName)
}

export interface BpmnNodeFormBinding {
  nodeId: string
  nodeName: string
  nodeType: TaskLocalName
  formId: number
  formName?: string
  readOnly: boolean
  actionIds?: Array<string | number>
}

function localName(el: Element): string {
  return el.localName || el.nodeName.split(':').pop() || ''
}

function parseActionIds(value: string): Array<string | number> {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed
  } catch {
    // fallback to comma-separated format
  }
  const cleaned = value.replace(/[\[\]\s]/g, '')
  if (!cleaned) return []
  return cleaned.split(',').map(v => v.trim()).filter(Boolean)
}

function readTaskFormProps(task: Element): {
  formId: number | null
  formName?: string
  readOnly: boolean
  actionIds: Array<string | number>
} {
  let formId: number | null = null
  let formName: string | undefined
  let readOnly = false
  let actionIds: Array<string | number> = []

  const descendants = task.getElementsByTagName('*')
  for (let i = 0; i < descendants.length; i++) {
    const prop = descendants[i]
    const ln = localName(prop)
    if (ln !== 'property' && ln !== 'values') continue
    const name = prop.getAttribute('name')
    const value = prop.getAttribute('value')
    if (name === 'formId' && value) {
      const parsed = parseInt(value, 10)
      if (!Number.isNaN(parsed)) formId = parsed
    }
    if (name === 'formName' && value) formName = value
    if (name === 'formReadOnly' && value === 'true') readOnly = true
    if (name === 'actionIds' && value) actionIds = parseActionIds(value)
  }

  return { formId, formName, readOnly, actionIds }
}

/**
 * Build nodeId → form binding map from BPMN XML string.
 */
export function parseBpmnNodeFormBindings(bpmnXml: string | null | undefined): Map<string, BpmnNodeFormBinding> {
  const result = new Map<string, BpmnNodeFormBinding>()
  if (!bpmnXml?.trim()) return result

  try {
    const doc = new DOMParser().parseFromString(bpmnXml, 'text/xml')
    const all = doc.getElementsByTagName('*')
    for (let i = 0; i < all.length; i++) {
      const el = all[i]
      const ln = localName(el)
      if (!isTaskElement(ln)) continue

      const nodeId = el.getAttribute('id') || ''
      if (!nodeId) continue

      const { formId, formName, readOnly, actionIds } = readTaskFormProps(el)
      if (formId == null) continue

      result.set(nodeId, {
        nodeId,
        nodeName: el.getAttribute('name') || nodeId,
        nodeType: ln,
        formId,
        formName,
        readOnly,
        actionIds,
      })
    }
  } catch {
    // ignore malformed XML
  }

  return result
}

export function lookupNodeFormBinding(
  bindings: Map<string, BpmnNodeFormBinding>,
  nodeId: string | null | undefined,
): BpmnNodeFormBinding | null {
  if (!nodeId) return null
  return bindings.get(nodeId) ?? null
}
