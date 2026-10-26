import type { BpmnExtensionValue } from '@/types/bpmn'

export const ACTION_NAMESPACE = 'http://workflow.platform/schema/custom'
const LEGACY_NAMESPACE = 'http://custom.bpmn.io/schema'
const ACTION_KEYS = new Set(['actionIds', 'actionNames', 'globalActionIds', 'globalActionNames'])
interface Entry { namespace: string | null; name: string; value: string; element?: Element }
// FALLBACK(migration): unqualified legacy properties can inherit the default BPMN namespace.
const rank = (namespace: string | null) => namespace === ACTION_NAMESPACE ? 2 : namespace === LEGACY_NAMESPACE ? 1 : !namespace || namespace === 'http://www.omg.org/spec/BPMN/20100524/MODEL' ? 0 : -1
const moddleNamespace = (type: string) => type.startsWith('custom_1:') ? LEGACY_NAMESPACE : ACTION_NAMESPACE

/** The platform namespace wins; within one namespace later entries replace earlier ones, including []. */
function resolve(entries: Entry[]): Record<string, string> {
  const values: Record<string, string> = {}
  const ranks: Record<string, number> = {}
  for (const entry of entries) {
    if (!ACTION_KEYS.has(entry.name) || rank(entry.namespace) < 0) continue
    if (rank(entry.namespace) >= (ranks[entry.name] ?? -1)) {
      values[entry.name] = entry.value
      ranks[entry.name] = rank(entry.namespace)
    }
  }
  return values
}

export function readModdleActionProperties(values: BpmnExtensionValue[]): Record<string, string> {
  const entries: Entry[] = []
  for (const ext of values) {
    if (ext.$type === 'custom:Properties' || ext.$type === 'custom_1:Properties') {
      // Keep the established moddle property/values order in every reader.
      for (const prop of [...(ext.property ?? []), ...(ext.values ?? [])]) {
        entries.push({ namespace: moddleNamespace(ext.$type), name: prop.name, value: prop.value ?? '' })
      }
    } else if (['custom:Property', 'custom:Values', 'custom_1:Property', 'custom_1:Values'].includes(ext.$type)) {
      entries.push({ namespace: moddleNamespace(ext.$type), name: ext.name, value: ext.value ?? '' })
    }
  }
  return resolve(entries)
}

function xmlEntries(scope: Element): Entry[] {
  const entries: Entry[] = []
  const add = (prop: Element, namespace: string | null) => {
    if (prop.localName === 'property' || prop.localName === 'values') entries.push({ namespace, name: prop.getAttribute('name') ?? '', value: prop.getAttribute('value') ?? prop.textContent ?? '', element: prop })
  }
  for (const extension of Array.from(scope.children).filter(child => child.localName === 'extensionElements')) {
    for (const child of Array.from(extension.children)) {
      if (rank(child.namespaceURI) < 0) continue
      if (child.localName === 'properties') {
        for (const type of ['property', 'values']) {
          Array.from(child.children).filter(prop => prop.localName === type).forEach(prop => add(prop, child.namespaceURI))
        }
      } else add(child, child.namespaceURI)
    }
  }
  return entries
}

export function readXmlActionProperties(scope: Element): Record<string, string> {
  return resolve(xmlEntries(scope))
}

/** Canonicalize only the edited pair in this scope, preserving all unrelated properties. */
export function writeXmlActionBinding(scope: Element, key: 'actionIds' | 'globalActionIds', ids: Array<string | number>, names: string[]): void {
  const nameKey = key === 'actionIds' ? 'actionNames' : 'globalActionNames'
  writeRawBinding(scope, key, { [key]: JSON.stringify(ids), [nameKey]: JSON.stringify(names) })
}

function writeRawBinding(scope: Element, key: 'actionIds' | 'globalActionIds', values: Record<string, string>): void {
  const nameKey = key === 'actionIds' ? 'actionNames' : 'globalActionNames'
  for (const entry of xmlEntries(scope)) {
    if (entry.name === key || entry.name === nameKey) entry.element?.remove()
  }
  // Empty unqualified legacy containers are not part of the moddle schema.
  for (const extension of Array.from(scope.children).filter(child => child.localName === 'extensionElements')) {
    Array.from(extension.children).filter(child => child.localName === 'properties' && rank(child.namespaceURI) === 0 && !child.children.length && Array.from(child.attributes).every(attr => attr.namespaceURI === 'http://www.w3.org/2000/xmlns/')).forEach(child => child.remove())
  }
  let extension = Array.from(scope.children).find(child => child.localName === 'extensionElements')
  if (!extension) {
    extension = scope.ownerDocument.createElementNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'bpmn:extensionElements')
    scope.insertBefore(extension, scope.firstChild)
  }
  let container = Array.from(extension.children).find(child => child.localName === 'properties' && child.namespaceURI === ACTION_NAMESPACE)
  if (!container) {
    container = scope.ownerDocument.createElementNS(ACTION_NAMESPACE, 'custom:properties')
    extension.appendChild(container)
  }
  for (const name of [key, nameKey]) {
    if (values[name] === undefined) continue
    const prop = scope.ownerDocument.createElementNS(ACTION_NAMESPACE, `${container.prefix ? `${container.prefix}:` : ''}property`)
    prop.setAttribute('name', name)
    prop.setAttribute('value', values[name])
    container.appendChild(prop)
  }
}

/** Repair legacy Action shapes before moddle can discard them; preserve the effective values. */
export function normalizeActionBpmnXml(xml: string): string {
  const document = new DOMParser().parseFromString(xml, 'text/xml')
  if (document.getElementsByTagName('parsererror').length) throw new Error('Invalid BPMN XML')
  let changed = false
  const scopes = new Set(Array.from(document.getElementsByTagNameNS('*', 'extensionElements')).map(extension => extension.parentElement!))
  for (const scope of scopes) {
    const entries = xmlEntries(scope)
    const values = resolve(entries)
    for (const key of ['actionIds', 'globalActionIds'] as const) {
      const nameKey = key === 'actionIds' ? 'actionNames' : 'globalActionNames'
      const needsRepair = entries.some(entry => {
        if (entry.name !== key && entry.name !== nameKey) return false
        const element = entry.element!
        const parent = element.parentElement!
        return rank(element.namespaceURI) < 1 || !element.hasAttribute('value')
          || (parent.localName === 'properties' && (rank(parent.namespaceURI) < 1 || element.namespaceURI !== parent.namespaceURI))
      })
      if (needsRepair) {
        writeRawBinding(scope, key, values)
        changed = true
      }
    }
  }
  return changed ? new XMLSerializer().serializeToString(document) : xml
}
