import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { createApplicationDetailFormSchema } from '../applicationDetail/useApplicationDetailFormSchema'
import { createTaskDetailFieldExtraction } from '../taskDetail/useTaskDetailFieldExtraction'
import { createFieldExtractor } from '../processStart/useProcessStartFieldExtractor'
import { extractFieldsRecursive as sharedExtract } from '../../components/formRendererHelpers/formRendererRuleParsing'

/**
 * The `inlineSubForm` widget carries no `field`, so every fork of extractFieldsRecursive
 * whose fallthrough is `if (item.field)` will SILENTLY drop it — no error, no warning, the
 * component simply never appears in the portal. That is exactly how the `linkForm` drag
 * widget died: placeable and configurable in the DW canvas, invisible at runtime.
 *
 * Unlike the miAssignment parity test (which deliberately covers only the three page-specific
 * forks and treats formRendererRuleParsing.ts as the reference implementation), this suite
 * covers ALL FOUR — the shared copy is the Inline Form's main runtime render path.
 */

/** Top-level `_bindingId`, as persisted: the drag rule's parseRule strips the props copy on save. */
const topLevelRule = () => [
  { type: 'input', field: 'before', title: 'Before' },
  { type: 'inlineSubForm', _bindingId: 66, title: 'Inline Form' },
  { type: 'input', field: 'after', title: 'After' },
]

/** Props-only `_bindingId`, as the designer canvas holds it after loadRule. */
const propsOnlyRule = () => [
  { type: 'inlineSubForm', props: { _bindingId: 66 }, title: 'Inline Form' },
]

const hiddenRule = () => [
  { type: 'inlineSubForm', _bindingId: 66, title: 'Inline Form', hidden: true },
]

/** An unbound placeholder (designer dropped it but never picked a table) must not be emitted. */
const unboundRule = () => [
  { type: 'input', field: 'before', title: 'Before' },
  { type: 'inlineSubForm', title: 'Inline Form' },
]

describe.each([
  ['formRendererRuleParsing (shared runtime copy)', () => {
    // This fork takes a converter for ordinary field-bearing rules (default: drop them).
    // The page forks build their own inline; supply a minimal one so sibling ordering
    // is observable here too.
    const converter = (item: Record<string, unknown>) => ({
      key: String(item.field),
      label: String(item.title ?? ''),
      type: 'text',
    })
    return (items: unknown[]) => sharedExtract(items as never, converter as never)
  }],
  ['applicationDetail (My Requests)', () => {
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
  ['processStart (New Request)', () => {
    const { extractFieldsRecursive } = createFieldExtractor({
      lookupDbConfigs: ref({}),
      relationViewConfigs: ref({}),
    })
    return (items: unknown[]) => extractFieldsRecursive(items as never)
  }],
])('%s extractFieldsRecursive — inlineSubForm', (_name, makeExtract) => {
  it('emits an inlineSubForm field from a top-level _bindingId', () => {
    const fields = makeExtract()(topLevelRule())
    const found = fields.find(f => f.type === 'inlineSubForm')
    expect(found).toBeDefined()
    expect(found!.key).toBe('__inlineSubForm_66')
    expect(found!._bindingId).toBe(66)
  })

  it('keeps the widget in document order among sibling fields', () => {
    const fields = makeExtract()(topLevelRule())
    expect(fields.map(f => f.key)).toEqual(['before', '__inlineSubForm_66', 'after'])
  })

  it('resolves _bindingId out of props when the top-level copy is absent', () => {
    const fields = makeExtract()(propsOnlyRule())
    const found = fields.find(f => f.type === 'inlineSubForm')
    expect(found).toBeDefined()
    expect(found!._bindingId).toBe(66)
  })

  it('does not collide with the subTable key namespace', () => {
    const fields = makeExtract()(topLevelRule())
    expect(fields.map(f => f.key)).not.toContain('__subTable_66')
  })

  it('carries the designer Hide flag', () => {
    const fields = makeExtract()(hiddenRule())
    const found = fields.find(f => f.type === 'inlineSubForm')
    expect(found).toBeDefined()
    expect(found!.hidden).toBe(true)
  })

  it('drops an unbound placeholder rather than emitting a broken field', () => {
    const fields = makeExtract()(unboundRule())
    expect(fields.some(f => f.type === 'inlineSubForm')).toBe(false)
    expect(fields.map(f => f.key)).toEqual(['before'])
  })
})

describe('applicationDetail skipSubTable gate', () => {
  it('suppresses the widget alongside subTable when skipSubTable is set', () => {
    const { extractFieldsRecursive } = createApplicationDetailFormSchema({
      lookupDbConfigs: ref({}),
      relationViewConfigs: ref({}),
      formFields: ref([]),
      formTabs: ref([]),
      formFieldsAfterTabs: ref([]),
      formFormOptions: ref({}),
    } as never)
    const fields = extractFieldsRecursive(topLevelRule() as never, { skipSubTable: true })
    expect(fields.some(f => f.type === 'inlineSubForm')).toBe(false)
    // Ordinary fields are untouched by the gate.
    expect(fields.map(f => f.key)).toEqual(['before', 'after'])
  })
})
