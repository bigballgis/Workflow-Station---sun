/**
 * Resolve MI sub-process scope config from deployed BPMN (Developer Workstation Process Design
 * extensions). DOM-walking helpers extracted from miSubProcessScope; behaviour-preserving.
 */

import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'

export interface MiSubProcessScopeConfig {
  /** Physical / designer table name from BPMN {@code subTableName} (e.g. HMDC_Transaction). */
  subTableName: string
  /** BPMN {@code assigneeField} on the MI user task (e.g. assignee_id). */
  assigneeField: string | null
  /** JUEL path for current collection row id (default currentItem.rowId). */
  rowIdVariable: string
  /** Sub-process extension {@code miTaskStatusField}. */
  miTaskStatusField: string | null
  /** Sub-process extension {@code miTaskCurrentNodeField}. */
  miTaskCurrentNodeField: string | null
  /** Flowable collection variable (e.g. multiInstance_HMDC_Transaction_collection). */
  collectionVariable: string | null
  /** Flowable element variable (e.g. currentItem). */
  elementVariable: string | null
}

const localName = (el: Element): string => el.localName || el.nodeName.split(':').pop() || ''

function readExtensionProperties(el: Element): Record<string, string> {
  const out: Record<string, string> = {}
  const props = el.getElementsByTagName('*')
  for (let i = 0; i < props.length; i++) {
    const p = props[i]!
    const ln = localName(p)
    if (ln !== 'property' && ln !== 'values') continue
    const n = p.getAttribute('name')
    const v = p.getAttribute('value')
    if (n && v != null && v !== '') out[n] = v
  }
  return out
}

function elementHasMultiInstanceLoop(el: Element): boolean {
  const desc = el.getElementsByTagName('*')
  for (let i = 0; i < desc.length; i++) {
    if (localName(desc[i]!) === 'multiInstanceLoopCharacteristics') return true
  }
  return false
}

function findAncestorSubProcess(from: Element): Element | null {
  let node: Node | null = from.parentNode
  while (node && node.nodeType === 1) {
    const wrap = node as Element
    if (localName(wrap) === 'subProcess') return wrap
    if (localName(wrap) === 'process' || localName(wrap) === 'definitions') break
    node = wrap.parentNode
  }
  return null
}

function findUserTaskByRef(doc: Document, userTaskId?: string, userTaskName?: string): Element | null {
  const idTrim = String(userTaskId ?? '').trim()
  const nameNorm = String(userTaskName ?? '').trim().replace(/\s+/g, ' ')
  const all = doc.getElementsByTagName('*')
  for (let i = 0; i < all.length; i++) {
    const el = all[i]!
    if (localName(el) !== 'userTask') continue
    const uid = (el.getAttribute('id') || '').trim()
    const uname = (el.getAttribute('name') || '').trim().replace(/\s+/g, ' ')
    if (idTrim && uid === idTrim) return el
    if (nameNorm && uname === nameNorm) return el
  }
  return null
}

/**
 * Sub-Task Config of the node that SPLITS this MI sub-process, i.e. the first user task inside it
 * that carries a `subTableName`.
 *
 * <p>A multi-instance sub-process is split exactly once. Only the splitting node is configured
 * (`subTableName` / `assigneeField` / `rowIdVariable`); every later node in the same sub-process —
 * `sub form2`, a review step, … — runs INSIDE an already-assigned sub-task and is deliberately left
 * unconfigured by the designer. Those nodes must therefore inherit the whole contract, not just the
 * table name: they describe the same collection row.
 *
 * <p>Previously only `subTableName` was inherited while `assigneeField` fell back to `null`. On
 * FU fu-20260422 that left `sub form2` with an incomplete scope, the participant identity resolved
 * differently on the read and write paths, and rows saved under one participant were filtered out
 * for the other — People rows vanished on reload.
 */
function splittingTaskPropsInSubProcess(sp: Element): Record<string, string> | null {
  const desc = sp.getElementsByTagName('*')
  for (let i = 0; i < desc.length; i++) {
    const el = desc[i]!
    if (localName(el) !== 'userTask') continue
    const props = readExtensionProperties(el)
    if (props.subTableName?.trim()) return props
  }
  return null
}

function readMiLoopAttributes(sp: Element): { collection: string | null; elementVariable: string | null } {
  const desc = sp.getElementsByTagName('*')
  for (let i = 0; i < desc.length; i++) {
    const el = desc[i]!
    if (localName(el) !== 'multiInstanceLoopCharacteristics') continue
    return {
      collection: el.getAttribute('flowable:collection') || el.getAttribute('collection') || null,
      elementVariable: el.getAttribute('flowable:elementVariable') || el.getAttribute('elementVariable') || null,
    }
  }
  return { collection: null, elementVariable: null }
}

function buildScopeFromElements(userTaskEl: Element, miSubProcess: Element): MiSubProcessScopeConfig | null {
  const ownProps = readExtensionProperties(userTaskEl)
  const spProps = readExtensionProperties(miSubProcess)
  // Own config wins; anything this node leaves unset is inherited from the splitting node, which is
  // the only one the designer configures for the whole sub-process (see splittingTaskPropsInSubProcess).
  const inherited = ownProps.subTableName?.trim() ? null : splittingTaskPropsInSubProcess(miSubProcess)
  const taskProps: Record<string, string> = { ...(inherited ?? {}), ...ownProps }
  const subTableName = (taskProps.subTableName || '').trim()
  if (!subTableName) return null

  const loop = readMiLoopAttributes(miSubProcess)
  const rowIdVar = (taskProps.rowIdVariable || 'currentItem.rowId').trim()

  return {
    subTableName,
    assigneeField: taskProps.assigneeField?.trim() || null,
    rowIdVariable: rowIdVar,
    miTaskStatusField: spProps.miTaskStatusField?.trim() || null,
    miTaskCurrentNodeField: spProps.miTaskCurrentNodeField?.trim() || null,
    collectionVariable: loop.collection,
    elementVariable: loop.elementVariable,
  }
}

/**
 * Resolve MI collection scope for the active user task from deployed BPMN (Process Design extensions).
 */
export function resolveMiSubProcessScopeFromBpmn(
  xml: string | null | undefined,
  options?: { userTaskId?: string | null; userTaskName?: string | null },
): MiSubProcessScopeConfig | null {
  if (!xml) return null
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return null

    const userTaskEl = findUserTaskByRef(
      doc,
      options?.userTaskId ?? undefined,
      options?.userTaskName ?? undefined,
    )

    if (userTaskEl) {
      const sp = findAncestorSubProcess(userTaskEl)
      if (sp && elementHasMultiInstanceLoop(sp)) {
        return buildScopeFromElements(userTaskEl, sp)
      }
    }

    // Running process on My Request: current node name only — pick first MI subProcess whose inner task matches or any MI scope.
    const all = doc.getElementsByTagName('*')
    for (let i = 0; i < all.length; i++) {
      const el = all[i]!
      if (localName(el) !== 'subProcess' || !elementHasMultiInstanceLoop(el)) continue
      const innerTasks: Element[] = []
      const desc = el.getElementsByTagName('*')
      for (let j = 0; j < desc.length; j++) {
        if (localName(desc[j]!) === 'userTask') innerTasks.push(desc[j]!)
      }
      if (innerTasks.length === 0) continue
      const nameNorm = String(options?.userTaskName ?? '').trim().replace(/\s+/g, ' ')
      const matchTask =
        (nameNorm
          ? innerTasks.find(t => (t.getAttribute('name') || '').trim().replace(/\s+/g, ' ') === nameNorm)
          : null) ?? innerTasks[0]!
      return buildScopeFromElements(matchTask, el)
    }
  } catch {
    /* ignore parse errors */
  }
  return null
}
