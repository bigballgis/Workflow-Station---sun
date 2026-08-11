import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailFormSchema } from '../applicationDetail/useApplicationDetailFormSchema'
import { createTaskDetailFieldExtraction } from '../taskDetail/useTaskDetailFieldExtraction'
import { createFieldExtractor } from '../processStart/useProcessStartFieldExtractor'

/**
 * Regression: three page-specific forks of extractFieldsRecursive
 * (applicationDetail/New Requests, taskDetail/To Do & Completed Tasks,
 * processStart/start-a-request) had no `miAssignment` case at all, unlike the
 * shared formRendererRuleParsing.ts copy. The marker's owned children (assignee
 * / BU / role) fell through to generic child recursion and were hoisted as loose
 * fields — silently dropping the container and its Designer Hide flag. DW Form
 * Preview (real form-create rule renderer) honored Hidden correctly the whole
 * time, which is why the divergence only showed up in user-portal dialogs.
 */
const marker = (hidden: boolean) => ({
  type: 'miAssignment',
  name: 'assignment-marker',
  children: [
    { type: 'input', field: 'assignee', title: 'Assignee' },
    { type: 'input', field: 'bu_code', title: 'BU' },
  ],
  ...(hidden ? { hidden: true } : {}),
})

const rules = (hidden: boolean) => [
  { type: 'input', field: 'before', title: 'Before' },
  marker(hidden),
  { type: 'input', field: 'after', title: 'After' },
]

describe.each([
  ['applicationDetail (New Requests / My Requests)', () => {
    const { extractFieldsRecursive } = createApplicationDetailFormSchema({
      lookupDbConfigs: ref({}),
      relationViewConfigs: ref({}),
      formFields: ref([]),
      formTabs: ref([]),
      formFieldsAfterTabs: ref([]),
      formFormOptions: ref({}),
    } as never)
    return (items: unknown[]) => extractFieldsRecursive(items as never)
  }],
  ['taskDetail (To Do / Completed Tasks)', () => {
    const { extractFieldsRecursive } = createTaskDetailFieldExtraction({
      lookupDbConfigs: ref({}),
      relationViewConfigs: ref({}),
      taskForm: {
        formFields: ref([]),
        formTabs: ref([]),
        formFieldsAfterTabs: ref([]),
        formFormOptions: ref({}),
        formReadOnly: ref(false),
      },
    } as never)
    return (items: unknown[]) => extractFieldsRecursive(items as never)
  }],
  ['processStart (New Request start-a-process)', () => {
    const { extractFieldsRecursive } = createFieldExtractor({
      lookupDbConfigs: ref({}),
      relationViewConfigs: ref({}),
    })
    return (items: unknown[]) => extractFieldsRecursive(items as never)
  }],
])('%s extractFieldsRecursive — miAssignment', (_name, makeExtract) => {
  it('nests the owned fields under the marker instead of hoisting them', () => {
    const extract = makeExtract()
    const fields = extract(rules(false))
    // Top level holds the marker only — the owned fields live inside it, so a
    // hidden marker can take them with it.
    expect(fields.map(f => [f.key, f.type])).toEqual([
      ['before', 'text'],
      ['assignment-marker', 'miAssignment'],
      ['after', 'text'],
    ])
    expect(fields[1].hidden).toBeFalsy()
    expect((fields[1].children || []).map(c => c.key)).toEqual(['assignee', 'bu_code'])
  })

  it('marks the marker hidden when the designer Hide toggle is set', () => {
    const extract = makeExtract()
    const fields = extract(rules(true))
    const found = fields.find(f => f.type === 'miAssignment')
    expect(found).toBeDefined()
    expect(found!.hidden).toBe(true)
    // Owned fields stay nested (the dialog still needs them for validation/save)
    // and must NOT appear as loose top-level siblings — that leaked an undesigned
    // Assignee row into the dialog while the block itself was correctly hidden.
    expect((found!.children || []).map(c => c.key)).toEqual(['assignee', 'bu_code'])
    expect(fields.map(f => f.key)).not.toContain('assignee')
    expect(fields.map(f => f.key)).not.toContain('bu_code')
  })
})
