/**
 * Property 10: BPMN XML 决策表绑定持久化
 * **Validates: Requirements 19.3**
 *
 * For any valid BPMN XML and decision table ID, after binding the XML
 * should contain the correct decisionId attribute on the specified service task node.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { parseBpmnServiceTasks, bindDecisionToNode } from '../decisionListHelpers'

// ─── BPMN XML template with service task nodes ──────────────────────────────

function buildBpmnXml(serviceTaskIds: string[]): string {
  const tasks = serviceTaskIds
    .map(id => `<bpmn:serviceTask id="${id}" name="Service ${id}" />`)
    .join('\n    ')
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" />
    ${tasks}
    <bpmn:endEvent id="EndEvent_1" />
  </bpmn:process>
</bpmn:definitions>`
}

// ─── Arbitraries ─────────────────────────────────────────────────────────────

const serviceTaskIdArb = fc.string({ minLength: 3, maxLength: 20 })
  .filter(s => /^[a-zA-Z][a-zA-Z0-9_-]*$/.test(s))

const decisionIdArb = fc.integer({ min: 1, max: 99999 })

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 10: BPMN XML 决策表绑定持久化', () => {
  it('binding a decision to a service task node persists the decisionId attribute', () => {
    fc.assert(
      fc.property(
        fc.array(serviceTaskIdArb, { minLength: 1, maxLength: 5 })
          .filter(ids => new Set(ids).size === ids.length), // unique IDs
        decisionIdArb,
        (taskIds, decisionId) => {
          const bpmnXml = buildBpmnXml(taskIds)
          // Pick the first service task to bind
          const targetNodeId = taskIds[0]

          const updatedXml = bindDecisionToNode(bpmnXml, targetNodeId, decisionId)

          // Parse the updated XML and verify the binding
          const { bindings } = parseBpmnServiceTasks(updatedXml)
          const boundNodes = bindings.get(decisionId) || []

          expect(boundNodes.length).toBeGreaterThanOrEqual(1)
          expect(boundNodes.some(n => n.nodeId === targetNodeId)).toBe(true)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('parseBpmnServiceTasks correctly extracts all service task nodes', () => {
    fc.assert(
      fc.property(
        fc.array(serviceTaskIdArb, { minLength: 1, maxLength: 5 })
          .filter(ids => new Set(ids).size === ids.length),
        (taskIds) => {
          const bpmnXml = buildBpmnXml(taskIds)
          const { nodes } = parseBpmnServiceTasks(bpmnXml)

          expect(nodes.length).toBe(taskIds.length)
          for (const id of taskIds) {
            expect(nodes.some(n => n.id === id)).toBe(true)
          }
        },
      ),
      { numRuns: 100 },
    )
  })

  it('binding does not affect other service task nodes', () => {
    fc.assert(
      fc.property(
        fc.array(serviceTaskIdArb, { minLength: 2, maxLength: 5 })
          .filter(ids => new Set(ids).size === ids.length),
        decisionIdArb,
        (taskIds, decisionId) => {
          const bpmnXml = buildBpmnXml(taskIds)
          const targetNodeId = taskIds[0]

          const updatedXml = bindDecisionToNode(bpmnXml, targetNodeId, decisionId)
          const { nodes } = parseBpmnServiceTasks(updatedXml)

          // All other nodes should still exist and not have the decisionId
          for (let i = 1; i < taskIds.length; i++) {
            const node = nodes.find(n => n.id === taskIds[i])
            expect(node).toBeDefined()
          }

          // Verify only the target node has the decisionId by re-parsing
          const parser = new DOMParser()
          const xmlDoc = parser.parseFromString(updatedXml, 'text/xml')
          const allElements = xmlDoc.getElementsByTagName('*')
          for (let i = 0; i < allElements.length; i++) {
            const el = allElements[i]
            if (el.localName === 'serviceTask') {
              const id = el.getAttribute('id')
              if (id !== targetNodeId) {
                expect(el.getAttribute('decisionId')).toBeNull()
              }
            }
          }
        },
      ),
      { numRuns: 100 },
    )
  })
})
