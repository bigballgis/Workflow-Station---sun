import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { extractFieldsRecursive } from '../formRendererRuleParsing'
import { createTaskDetailFieldExtraction } from '@/composables/taskDetail/useTaskDetailFieldExtraction'
import { createApplicationDetailFormSchema } from '@/composables/applicationDetail/useApplicationDetailFormSchema'
import { createFieldExtractor } from '@/composables/processStart/useProcessStartFieldExtractor'

describe('Record Note identity across actual entry point extractors', () => {
  const context = () => ({
    lookupDbConfigs: ref({}), relationViewConfigs: ref({}),
    formFields: ref([]), formTabs: ref([]), formFieldsAfterTabs: ref([]), formFormOptions: ref({}),
    formReadOnly: ref(false), cachedContentForms: [],
  })
  const entryPoints = {
    nested: extractFieldsRecursive,
    request: createFieldExtractor(context()).extractFieldsRecursive,
    audit: createApplicationDetailFormSchema(context() as never).extractFieldsRecursive,
    task: createTaskDetailFieldExtraction({ ...context(), taskForm: context() } as never).extractFieldsRecursive,
  }
  for (const [entry, extract] of Object.entries(entryPoints)) {
    it(`${entry} preserves portable control identity and distinct same-scope controls`, () => {
      const rules = ['first', 'second'].map(controlId => ({
        type: 'recordNote', props: { scope: 'TABLE', formKey: 'portable-form', controlId, readonly: true },
      }))
      const fields = extract(rules)
      expect(fields.map(field => field.key)).toEqual(['__recordNote_first', '__recordNote_second'])
      expect(fields.map(field => field._recordNote)).toEqual([
        expect.objectContaining({ formKey: 'portable-form', controlId: 'first', readonly: true }),
        expect.objectContaining({ formKey: 'portable-form', controlId: 'second', readonly: true }),
      ])
    })
  }
})
