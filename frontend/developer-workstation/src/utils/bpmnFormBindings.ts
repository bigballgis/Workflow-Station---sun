/**
 * Parse BPMN userTask / serviceTask → formId bindings (custom extension properties).
 * Shared by Form Designer and Process Debug.
 */

export interface BpmnNodeFormBinding {
  nodeId: string
  nodeName: string
  nodeType: 'userTask' | 'serviceTask'
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
      if (ln !== 'userTask' && ln !== 'serviceTask') continue

      const nodeId = el.getAttribute('id') || ''
      if (!nodeId) continue

      const { formId, formName, readOnly, actionIds } = readTaskFormProps(el)
      if (formId == null) continue

      result.set(nodeId, {
        nodeId,
        nodeName: el.getAttribute('name') || nodeId,
        nodeType: ln as 'userTask' | 'serviceTask',
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
