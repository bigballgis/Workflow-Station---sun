import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailFieldExtraction } from '@/composables/taskDetail/useTaskDetailFieldExtraction'
import { createApplicationDetailFormSchema } from '@/composables/applicationDetail/useApplicationDetailFormSchema'
import { createFieldExtractor } from '@/composables/processStart/useProcessStartFieldExtractor'

const context = () => ({
  lookupDbConfigs: ref({}), relationViewConfigs: ref({}),
  formFields: ref([]), formTabs: ref([]), formFieldsAfterTabs: ref([]), formFormOptions: ref({}),
  formReadOnly: ref(false), cachedContentForms: [],
})

const entryPoints = {
  start: createFieldExtractor(context()).extractFieldsRecursive,
  request: createApplicationDetailFormSchema(context() as never).extractFieldsRecursive,
  task: createTaskDetailFieldExtraction({ ...context(), taskForm: context() } as never).extractFieldsRecursive,
}

for (const [entry, extract] of Object.entries(entryPoints)) {
  describe(`${entry} Select configuration`, () => {
    it.each([true, false, undefined])('honors multiple=%s inside a card', (multiple) => {
      const rules = [{ type: 'el-card', children: [{
        type: 'select', field: 'choices', title: 'Choices',
        props: { multiple }, options: [{ label: 'First', value: 1 }, { label: 'Second', value: 2 }],
      }] }]
      const field = extract(JSON.parse(JSON.stringify(rules)))[0].children![0]
      expect(field.multiple === true).toBe(multiple === true)
      expect(field.options).toEqual(rules[0].children[0].options)
    })

    it('keeps a single-item multiple default as an array', () => {
      const [field] = extract([{
        type: 'select', field: 'choices', value: [1], props: { multiple: true },
        options: [{ label: 'First', value: 1 }, { label: 'Second', value: 2 }],
      }])
      expect(field.defaultValue).toEqual([1])
    })
  })
}
