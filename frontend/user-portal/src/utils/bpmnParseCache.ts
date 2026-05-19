/** Reuse parsed BPMN DOM per XML string on a single detail page load (avoids 5+ DOMParser passes). */
const docCache = new Map<string, Document>()

export function getCachedBpmnDocument(xml: string): Document | null {
  if (!xml) return null
  let doc = docCache.get(xml)
  if (!doc) {
    doc = new DOMParser().parseFromString(xml, 'text/xml')
    docCache.set(xml, doc)
  }
  return doc
}

export function clearBpmnParseCache(): void {
  docCache.clear()
}
