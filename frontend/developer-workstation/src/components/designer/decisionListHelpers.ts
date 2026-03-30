/**
 * Pure helper functions for DecisionList BPMN XML manipulation.
 * Extracted for testability (cannot export from <script setup>).
 */

/**
 * Parse BPMN XML to extract service task nodes and existing decision bindings.
 */
export function parseBpmnServiceTasks(bpmnXml: string): {
  nodes: Array<{ id: string; name: string }>;
  bindings: Map<number, Array<{ nodeId: string; nodeName: string }>>;
} {
  const nodes: Array<{ id: string; name: string }> = []
  const bindings = new Map<number, Array<{ nodeId: string; nodeName: string }>>()

  const parser = new DOMParser()
  const xmlDoc = parser.parseFromString(bpmnXml, 'text/xml')

  const allElements = xmlDoc.getElementsByTagName('*')
  for (let i = 0; i < allElements.length; i++) {
    const el = allElements[i]
    if (el.localName === 'serviceTask') {
      const id = el.getAttribute('id') || ''
      const name = el.getAttribute('name') || id
      nodes.push({ id, name })

      const decisionIdAttr = el.getAttribute('decisionId')
      if (decisionIdAttr) {
        const dId = parseInt(decisionIdAttr, 10)
        if (!isNaN(dId)) {
          const existing = bindings.get(dId) || []
          existing.push({ nodeId: id, nodeName: name })
          bindings.set(dId, existing)
        }
      }
    }
  }

  return { nodes, bindings }
}

/**
 * Update BPMN XML to set decisionId attribute on a service task node.
 */
export function bindDecisionToNode(bpmnXml: string, nodeId: string, decisionId: number): string {
  const parser = new DOMParser()
  const xmlDoc = parser.parseFromString(bpmnXml, 'text/xml')

  const allElements = xmlDoc.getElementsByTagName('*')
  for (let i = 0; i < allElements.length; i++) {
    const el = allElements[i]
    if (el.localName === 'serviceTask' && el.getAttribute('id') === nodeId) {
      el.setAttribute('decisionId', String(decisionId))
      break
    }
  }

  const serializer = new XMLSerializer()
  return serializer.serializeToString(xmlDoc)
}
