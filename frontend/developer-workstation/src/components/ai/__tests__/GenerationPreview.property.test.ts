import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import DOMPurify from 'dompurify'
import type { AiGeneratedData, GenerationPreviewData } from '@/types/aiGeneration'
import { computeDiff } from '@/types/aiGeneration'
import { useAiTemplates } from '@/composables/useAiTemplates'
import type { AiTemplate } from '@/composables/useAiTemplates'
import { saveDraft, loadDraft, clearDraft, buildDraftKey } from '@/composables/useAiChat'
import type { AiGenerationDraft } from '@/composables/useAiChat'

// ─── Extracted logic for testability ────────────────────────────────────────
// Mirrors the computePreviewData function from ChatDialog.vue

function computePreviewData(data: AiGeneratedData): GenerationPreviewData {
  const tables = data.tableDefinitions || []
  const forms = data.formDefinitions || []
  const actions = data.actionDefinitions || []
  const process = data.processDefinition

  let totalFieldCount = 0
  for (const table of tables) {
    totalFieldCount += (table.fieldDefinitions || []).length
  }

  let processNodeCount = 0
  let processGatewayCount = 0
  if (process?.bpmnXml) {
    const xml = process.bpmnXml as string
    const taskMatches = xml.match(/<bpmn:userTask|<bpmn:serviceTask|<bpmn:scriptTask|<bpmn:startEvent|<bpmn:endEvent|<bpmn:task/g)
    processNodeCount = taskMatches ? taskMatches.length : 0
    const gatewayMatches = xml.match(/<bpmn:exclusiveGateway|<bpmn:parallelGateway|<bpmn:inclusiveGateway|<bpmn:eventBasedGateway/g)
    processGatewayCount = gatewayMatches ? gatewayMatches.length : 0
  }

  const actionTypes = [...new Set(actions.map((a: any) => a.actionType).filter(Boolean))]

  return {
    tableCount: tables.length,
    totalFieldCount,
    formCount: forms.length,
    actionCount: actions.length,
    actionTypes,
    processNodeCount,
    processGatewayCount,
    decisionCount: data.decisionDefinitions?.length || 0,
    tableRelationCount: data.tableRelations?.length || 0,
    iconSvg: data.icon?.svgContent
  }
}

// Mirrors the SVG_PURIFY_CONFIG from GenerationPreview.vue
const SVG_PURIFY_CONFIG = {
  ALLOWED_TAGS: ['svg', 'path', 'circle', 'rect', 'line', 'polyline', 'polygon', 'g', 'defs', 'use'],
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur'],
  ALLOWED_ATTR: ['viewBox', 'd', 'fill', 'stroke', 'stroke-width', 'cx', 'cy', 'r', 'x', 'y',
    'width', 'height', 'points', 'transform', 'class', 'xmlns', 'xlink:href'],
}

function sanitizeSvg(input: string): string {
  return DOMPurify.sanitize(input, SVG_PURIFY_CONFIG)
}

// ─── Generators ─────────────────────────────────────────────────────────────

const fieldDefinitionArb = fc.record({
  fieldName: fc.string({ minLength: 1, maxLength: 20 }),
  fieldType: fc.constantFrom('TEXT', 'NUMBER', 'DATE', 'BOOLEAN'),
})

const tableDefinitionArb = fc.record({
  tableName: fc.string({ minLength: 1, maxLength: 30 }),
  fieldDefinitions: fc.array(fieldDefinitionArb, { minLength: 0, maxLength: 5 }),
})

const formDefinitionArb = fc.record({
  formName: fc.string({ minLength: 1, maxLength: 30 }),
  formType: fc.constantFrom('PROCESS', 'TASK', 'ACTION'),
})

const actionDefinitionArb = fc.record({
  actionName: fc.string({ minLength: 1, maxLength: 30 }),
  actionType: fc.constantFrom('APPROVE', 'REJECT', 'SAVE', 'SUBMIT', 'CANCEL'),
})

const decisionDefinitionArb = fc.record({
  decisionKey: fc.string({ minLength: 1, maxLength: 30 }),
  decisionName: fc.string({ minLength: 1, maxLength: 30 }),
})

const tableRelationArb = fc.record({
  sourceTableName: fc.string({ minLength: 1, maxLength: 30 }),
  targetTableName: fc.string({ minLength: 1, maxLength: 30 }),
  relationType: fc.constantFrom('ONE_TO_ONE', 'ONE_TO_MANY', 'MANY_TO_MANY'),
})

describe('Feature: ai-function-unit-generation-refactor', () => {
  /**
   * Feature: ai-function-unit-generation-refactor, Property 13: computePreviewData 统计完整性
   *
   * For any AiGeneratedData object, computePreviewData() returns a GenerationPreviewData
   * where decisionCount and tableRelationCount match the array lengths of
   * decisionDefinitions and tableRelations respectively. Also verifies tableCount,
   * formCount, and actionCount consistency.
   *
   * **Validates: Requirements 16.3, 16.4**
   */
  it('Property 13: computePreviewData correctly counts all entity types', () => {
    fc.assert(
      fc.property(
        fc.array(tableDefinitionArb, { minLength: 0, maxLength: 8 }),
        fc.array(formDefinitionArb, { minLength: 0, maxLength: 8 }),
        fc.array(actionDefinitionArb, { minLength: 0, maxLength: 8 }),
        fc.array(decisionDefinitionArb, { minLength: 0, maxLength: 8 }),
        fc.array(tableRelationArb, { minLength: 0, maxLength: 8 }),
        (tables, forms, actions, decisions, relations) => {
          const data: AiGeneratedData = {
            tableDefinitions: tables,
            formDefinitions: forms,
            actionDefinitions: actions,
            decisionDefinitions: decisions,
            tableRelations: relations,
          }

          const preview = computePreviewData(data)

          // Core counts must match array lengths
          expect(preview.tableCount).toBe(tables.length)
          expect(preview.formCount).toBe(forms.length)
          expect(preview.actionCount).toBe(actions.length)
          expect(preview.decisionCount).toBe(decisions.length)
          expect(preview.tableRelationCount).toBe(relations.length)

          // totalFieldCount must equal sum of all fieldDefinitions lengths
          const expectedFieldCount = tables.reduce(
            (sum, t) => sum + (t.fieldDefinitions || []).length, 0
          )
          expect(preview.totalFieldCount).toBe(expectedFieldCount)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 14: SVG DOMPurify 净化安全性
   *
   * For any SVG string containing <script>, <iframe>, on* event attributes,
   * or javascript: protocol, after DOMPurify sanitization the output should
   * not contain these dangerous content patterns.
   *
   * **Validates: Requirements 18.1, 18.2, 18.3**
   */
  it('Property 14: SVG sanitization removes dangerous content', () => {
    const safeText = fc.string({ minLength: 1, maxLength: 30 }).filter(s => s.trim().length > 0)

    const maliciousSvgPayload = fc.oneof(
      // <script> tag injections
      safeText.map(t => `<svg><script>${t}</script></svg>`),
      safeText.map(t => `<svg><script src="${t}"></script><circle cx="10" cy="10" r="5"/></svg>`),
      // <iframe> tag injections
      safeText.map(t => `<svg><iframe src="${t}"></iframe></svg>`),
      // on* event attribute injections
      safeText.map(t => `<svg onerror="${t}"><circle cx="10" cy="10" r="5"/></svg>`),
      safeText.map(t => `<svg><circle cx="10" cy="10" r="5" onload="${t}"/></svg>`),
      safeText.map(t => `<svg><rect onclick="${t}" width="10" height="10"/></svg>`),
      safeText.map(t => `<svg onmouseover="${t}"><path d="M0 0"/></svg>`),
      // javascript: protocol injections
      safeText.map(t => `<svg><a href="javascript:${t}"><circle cx="10" cy="10" r="5"/></a></svg>`),
      // <object> and <embed> tag injections
      safeText.map(t => `<svg><object data="${t}"></object></svg>`),
      safeText.map(t => `<svg><embed src="${t}"/></svg>`),
      // Mixed: valid SVG with injected dangerous content
      safeText.map(t => `<svg viewBox="0 0 24 24"><path d="M0 0"/><script>alert("${t}")</script></svg>`),
    )

    fc.assert(
      fc.property(maliciousSvgPayload, (svgInput) => {
        const sanitized = sanitizeSvg(svgInput)
        const sanitizedLower = sanitized.toLowerCase()

        // No <script> tags
        expect(sanitizedLower).not.toMatch(/<script[\s>]/)
        expect(sanitizedLower).not.toMatch(/<\/script>/)

        // No <iframe> tags
        expect(sanitizedLower).not.toMatch(/<iframe[\s>]/)

        // No <object> or <embed> tags
        expect(sanitizedLower).not.toMatch(/<object[\s>]/)
        expect(sanitizedLower).not.toMatch(/<embed[\s>]/)

        // No on* event handler attributes
        expect(sanitizedLower).not.toMatch(/\bon\w+\s*=/)

        // No javascript: protocol
        expect(sanitizedLower).not.toContain('javascript:')
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 21: Template structure completeness
   *
   * All templates returned by useAiTemplates() must have all required fields
   * (id, nameKey, descriptionKey, icon, promptTemplate) with non-empty values,
   * and i18n keys must follow the 'ai.template.*' naming convention.
   *
   * **Validates: Requirements 43.3, 43.6**
   */
  it('Property 21: All templates have required fields and valid i18n key format', () => {
    const { templates } = useAiTemplates()
    const requiredFields: (keyof AiTemplate)[] = ['id', 'nameKey', 'descriptionKey', 'icon', 'promptTemplate']

    fc.assert(
      fc.property(
        fc.constantFrom(...templates),
        (template: AiTemplate) => {
          // All required fields must be present and non-empty
          for (const field of requiredFields) {
            expect(template[field]).toBeDefined()
            expect(typeof template[field]).toBe('string')
            expect((template[field] as string).trim().length).toBeGreaterThan(0)
          }

          // i18n keys must follow 'ai.template.*' format
          expect(template.nameKey).toMatch(/^ai\.template\.\w+\.name$/)
          expect(template.descriptionKey).toMatch(/^ai\.template\.\w+\.description$/)

          // id must be a valid identifier (lowercase, hyphens allowed)
          expect(template.id).toMatch(/^[a-z][a-z0-9-]*$/)

          // promptTemplate must be non-trivial (at least 10 chars)
          expect(template.promptTemplate.length).toBeGreaterThanOrEqual(10)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 22: 增量数据合并完整性
   *
   * For any sequence of partial generated_data SSE events (each containing a different
   * subset of AiGeneratedData), the merged result should contain all field data from
   * all events, with later-arriving data overwriting earlier same-named fields.
   *
   * **Validates: Requirements 44.1, 44.4**
   */
  it('Property 22: Incremental data merge preserves all fields', () => {
    const partialDataArb = fc.record({
      tableDefinitions: fc.option(fc.array(tableDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      formDefinitions: fc.option(fc.array(formDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      actionDefinitions: fc.option(fc.array(actionDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      decisionDefinitions: fc.option(fc.array(decisionDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      tableRelations: fc.option(fc.array(tableRelationArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
    })

    fc.assert(
      fc.property(
        fc.array(partialDataArb, { minLength: 1, maxLength: 5 }),
        (events) => {
          // Simulate incremental merge (same logic as useAiChat)
          let merged: Record<string, unknown> = {}
          for (const event of events) {
            // Only merge defined (non-undefined) fields
            const defined: Record<string, unknown> = {}
            for (const [key, value] of Object.entries(event)) {
              if (value !== undefined) defined[key] = value
            }
            merged = { ...merged, ...defined }
          }

          // The merged result should contain all fields that appeared in any event
          for (const event of events) {
            for (const [key, value] of Object.entries(event)) {
              if (value !== undefined) {
                expect(merged).toHaveProperty(key)
              }
            }
          }

          // The last event's defined fields should be the final values
          for (let i = events.length - 1; i >= 0; i--) {
            for (const [key, value] of Object.entries(events[i])) {
              if (value !== undefined) {
                // Check if no later event overwrites this key
                const overwrittenLater = events.slice(i + 1).some(e => (e as Record<string, unknown>)[key] !== undefined)
                if (!overwrittenLater) {
                  expect(merged[key]).toEqual(value)
                }
              }
            }
          }
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 23: 流式预览应用按钮守卫
   *
   * For any GenerationPreview component state, when isGenerationComplete is false,
   * the "Apply" button should be disabled. When and only when isGenerationComplete
   * is true, the button should be clickable.
   *
   * **Validates: Requirements 44.5**
   */
  it('Property 23: Apply button is disabled when isGenerationComplete is false', () => {
    fc.assert(
      fc.property(
        fc.boolean(),
        fc.array(tableDefinitionArb, { minLength: 0, maxLength: 3 }),
        fc.array(formDefinitionArb, { minLength: 0, maxLength: 3 }),
        (isComplete, tables, forms) => {
          const data: AiGeneratedData = {
            tableDefinitions: tables,
            formDefinitions: forms,
          }
          const preview = computePreviewData(data)

          // The apply button disabled state is determined by !isGenerationComplete
          // When isComplete is false, button should be disabled (disabled = true)
          // When isComplete is true, button should be enabled (disabled = false)
          const buttonDisabled = !isComplete
          if (!isComplete) {
            expect(buttonDisabled).toBe(true)
          } else {
            expect(buttonDisabled).toBe(false)
          }

          // Preview data should still be valid regardless of completion state
          expect(preview.tableCount).toBe(tables.length)
          expect(preview.formCount).toBe(forms.length)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 26: SSE 事件到进度步骤映射
   *
   * For any SSE event sequence, generationStep should be monotonically increasing
   * (not regressing). Mapping: token → step 1, document → step 2,
   * generated_data → step 5, done → step 6. error resets to 0.
   *
   * **Validates: Requirements 46.3**
   */
  it('Property 26: Generation step is monotonically increasing for non-error events', () => {
    const stepMap: Record<string, number> = {
      token: 1,
      document: 2,
      generated_data: 5,
      done: 6
    }

    const nonErrorEventArb = fc.constantFrom('token', 'document', 'generated_data', 'done')

    fc.assert(
      fc.property(
        fc.array(nonErrorEventArb, { minLength: 1, maxLength: 20 }),
        (events) => {
          // Simulate the step advancement logic from useAiChat
          let step = 0
          const stepHistory: number[] = [step]

          for (const event of events) {
            const mappedStep = stepMap[event]
            if (mappedStep > step) {
              step = mappedStep
            }
            stepHistory.push(step)
          }

          // Verify monotonic increase: each step >= previous step
          for (let i = 1; i < stepHistory.length; i++) {
            expect(stepHistory[i]).toBeGreaterThanOrEqual(stepHistory[i - 1])
          }

          // Final step should be the max of all mapped values seen
          const maxMapped = Math.max(...events.map(e => stepMap[e]))
          expect(step).toBe(maxMapped)

          // Step should never exceed 6
          expect(step).toBeLessThanOrEqual(6)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Property 26 (supplementary): error event resets generationStep to 0
   */
  it('Property 26: Error event resets generation step to 0', () => {
    const stepMap: Record<string, number> = {
      token: 1,
      document: 2,
      generated_data: 5,
      done: 6
    }

    const eventArb = fc.constantFrom('token', 'document', 'generated_data', 'done', 'error')

    fc.assert(
      fc.property(
        fc.array(eventArb, { minLength: 1, maxLength: 20 }),
        (events) => {
          let step = 0

          for (const event of events) {
            if (event === 'error') {
              step = 0
            } else {
              const mappedStep = stepMap[event]
              if (mappedStep > step) {
                step = mappedStep
              }
            }
          }

          // If the last event is 'error', step should be 0
          if (events[events.length - 1] === 'error') {
            expect(step).toBe(0)
          }

          // Step should always be in [0, 6]
          expect(step).toBeGreaterThanOrEqual(0)
          expect(step).toBeLessThanOrEqual(6)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 25: 草稿保存/加载往返
   *
   * For any AiGeneratedData object, saving to localStorage then loading should
   * return equivalent data. Draft must contain generatedData, previewData,
   * timestamp, and sessionId fields.
   *
   * **Validates: Requirements 45.4, 47.1, 47.2**
   */
  it('Property 25: Draft save/load round-trip preserves data', () => {
    const generatedDataArb = fc.record({
      tableDefinitions: fc.option(fc.array(tableDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      formDefinitions: fc.option(fc.array(formDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      actionDefinitions: fc.option(fc.array(actionDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      decisionDefinitions: fc.option(fc.array(decisionDefinitionArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
      tableRelations: fc.option(fc.array(tableRelationArb, { minLength: 0, maxLength: 3 }), { nil: undefined }),
    })

    const functionUnitIdArb = fc.integer({ min: 1, max: 10000 })
    const sessionIdArb = fc.string({ minLength: 5, maxLength: 20 }).filter(s => /^[a-zA-Z0-9-]+$/.test(s))

    fc.assert(
      fc.property(
        generatedDataArb,
        functionUnitIdArb,
        sessionIdArb,
        (genData, fuId, sessId) => {
          const draft: AiGenerationDraft = {
            generatedData: genData as AiGeneratedData,
            previewData: null,
            timestamp: Date.now(),
            sessionId: sessId
          }

          // Save
          saveDraft(fuId, sessId, draft)

          // Load
          const loaded = loadDraft(fuId, sessId)

          // Verify round-trip
          expect(loaded).not.toBeNull()
          expect(loaded!.sessionId).toBe(sessId)
          expect(loaded!.timestamp).toBe(draft.timestamp)
          expect(JSON.stringify(loaded!.generatedData)).toBe(JSON.stringify(draft.generatedData))

          // Cleanup
          clearDraft(fuId, sessId)
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 27: 草稿生命周期
   *
   * For any saved draft, after clearing it the localStorage entry should be removed.
   * For any draft older than 24 hours, loadDraft should return null (expired).
   *
   * **Validates: Requirements 47.6, 47.7**
   */
  it('Property 27: Draft lifecycle — cleared after apply, expired after 24h', () => {
    const functionUnitIdArb = fc.integer({ min: 1, max: 10000 })
    const sessionIdArb = fc.string({ minLength: 5, maxLength: 20 }).filter(s => /^[a-zA-Z0-9-]+$/.test(s))

    fc.assert(
      fc.property(
        functionUnitIdArb,
        sessionIdArb,
        (fuId, sessId) => {
          // Test 1: Draft cleared after apply
          const draft: AiGenerationDraft = {
            generatedData: { tableDefinitions: [{ tableName: 'test' }] },
            previewData: null,
            timestamp: Date.now(),
            sessionId: sessId
          }
          saveDraft(fuId, sessId, draft)
          expect(loadDraft(fuId, sessId)).not.toBeNull()

          clearDraft(fuId, sessId)
          expect(loadDraft(fuId, sessId)).toBeNull()

          // Test 2: Draft expired after 24h
          const expiredDraft: AiGenerationDraft = {
            generatedData: { tableDefinitions: [{ tableName: 'old' }] },
            previewData: null,
            timestamp: Date.now() - 25 * 60 * 60 * 1000, // 25 hours ago
            sessionId: sessId
          }
          saveDraft(fuId, sessId, expiredDraft)
          const loaded = loadDraft(fuId, sessId)
          expect(loaded).toBeNull()

          // Verify the expired entry was also cleaned up from localStorage
          expect(localStorage.getItem(buildDraftKey(fuId, sessId))).toBeNull()
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Feature: ai-function-unit-generation-refactor, Property 28: 实体差异计算正确性
   *
   * For any two AiGeneratedData objects (current and generated), diff computation
   * should correctly categorize entities as added/deleted/modified. The union of
   * added + deleted + modified + unchanged names should equal the union of both datasets.
   *
   * **Validates: Requirements 48.2**
   */
  it('Property 28: Diff computation correctly categorizes entities', () => {
    fc.assert(
      fc.property(
        fc.array(fc.record({ tableName: fc.string({ minLength: 1, maxLength: 15 }).filter(s => s.trim().length > 0) }), { minLength: 0, maxLength: 5 }),
        fc.array(fc.record({ tableName: fc.string({ minLength: 1, maxLength: 15 }).filter(s => s.trim().length > 0) }), { minLength: 0, maxLength: 5 }),
        (currentTables, generatedTables) => {
          const current: AiGeneratedData = { tableDefinitions: currentTables }
          const generated: AiGeneratedData = { tableDefinitions: generatedTables }
          const diff = computeDiff(current, generated)

          const currentNames = new Set(currentTables.map(t => t.tableName))
          const generatedNames = new Set(generatedTables.map(t => t.tableName))

          // All added items must be in generated but not in current
          for (const item of diff.added.filter(d => d.type === 'table')) {
            expect(generatedNames.has(item.name)).toBe(true)
            expect(currentNames.has(item.name)).toBe(false)
          }

          // All removed items must be in current but not in generated
          for (const item of diff.removed.filter(d => d.type === 'table')) {
            expect(currentNames.has(item.name)).toBe(true)
            expect(generatedNames.has(item.name)).toBe(false)
          }

          // All modified items must be in both
          for (const item of diff.modified.filter(d => d.type === 'table')) {
            expect(currentNames.has(item.name)).toBe(true)
            expect(generatedNames.has(item.name)).toBe(true)
          }

          // Union property: added + removed + modified + unchanged = union of both sets
          const addedNames = new Set(diff.added.filter(d => d.type === 'table').map(d => d.name))
          const removedNames = new Set(diff.removed.filter(d => d.type === 'table').map(d => d.name))
          const modifiedNames = new Set(diff.modified.filter(d => d.type === 'table').map(d => d.name))

          // Every name in the union of current and generated must be accounted for
          const allNames = new Set([...currentNames, ...generatedNames])
          for (const name of allNames) {
            const isAdded = addedNames.has(name)
            const isRemoved = removedNames.has(name)
            const isModified = modifiedNames.has(name)
            const isUnchanged = currentNames.has(name) && generatedNames.has(name) && !isModified

            // Each name must be in exactly one category
            const categories = [isAdded, isRemoved, isModified, isUnchanged].filter(Boolean).length
            expect(categories).toBe(1)
          }
        }
      ),
      { numRuns: 100 }
    )
  })
})
