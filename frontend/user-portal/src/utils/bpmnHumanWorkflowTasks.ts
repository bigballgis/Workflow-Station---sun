/**
 * Human-interaction BPMN task types included in Portal workflow diagram status parsing.
 * Excludes automated serviceTask/sendTask (handled separately). Aligns with DW designer
 * task types in `frontend/developer-workstation/src/utils/bpmnFormBindings.ts`.
 */
export const HUMAN_WORKFLOW_TASK_LOCAL_NAMES = [
  'task',
  'userTask',
  'manualTask',
  'scriptTask',
  'businessRuleTask',
  'receiveTask',
] as const

export type HumanWorkflowTaskLocalName = (typeof HUMAN_WORKFLOW_TASK_LOCAL_NAMES)[number]

export const HUMAN_WORKFLOW_TASK_SELECTOR = HUMAN_WORKFLOW_TASK_LOCAL_NAMES.join(', ')

export function isHumanWorkflowTask(
  localName: string | undefined | null,
): localName is HumanWorkflowTaskLocalName {
  return !!localName && (HUMAN_WORKFLOW_TASK_LOCAL_NAMES as readonly string[]).includes(localName)
}

export function isHumanWorkflowTaskElement(el: Element): boolean {
  const ln = el.localName || el.nodeName.split(':').pop()
  return isHumanWorkflowTask(ln)
}

export function queryHumanWorkflowTasks(doc: Document | ParentNode): Element[] {
  const root = doc instanceof Document ? doc.documentElement : (doc as Element)
  if (!root) return []
  const results: Element[] = []
  const all = root.getElementsByTagName('*')
  for (let i = 0; i < all.length; i++) {
    const el = all[i]
    const ln = el.localName || el.nodeName.split(':').pop()
    if (isHumanWorkflowTask(ln)) results.push(el)
  }
  return results
}

export function findHumanWorkflowTaskById(doc: Document, bpmnId: string): Element | null {
  const id = String(bpmnId ?? '').trim()
  if (!id) return null
  for (const el of queryHumanWorkflowTasks(doc)) {
    if (String(el.getAttribute('id') ?? '').trim() === id) return el
  }
  return null
}
