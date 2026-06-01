import { describe, expect, it } from 'vitest'

import {

  emptyComponentEventFunction,

  emptyFormLevelEventFunction,

  ensureEmptyFormOptionsEvents,

  ensureEmptyRuleComponentEvents,

  extractFormCreateHandlerBody,

  isEmptyFormCreateHandler,

  normalizeFormLevelEventHandler,

  flattenComponentEventsForPersist,
  normalizeEventEditorBody,

  walkRulesEnsureComponentEvents,

} from '../formCreateDefaultEvents'



/** Mirrors form-create FnEditor.trimString — editor body must be extractable or whole string shows as invalid JS. */

function fnEditorBody(raw: string): string {

  const firstIndex = raw.indexOf('{')

  const lastIndex = raw.lastIndexOf('}')

  if (firstIndex === -1 || lastIndex === -1 || firstIndex >= lastIndex) {

    return raw

  }

  return raw.slice(firstIndex + 1, lastIndex).replace(/^\n+|\n+$/g, '')

}



describe('formCreateDefaultEvents', () => {

  it('builds form-level handler matching FnConfig named-function template', () => {

    const fn = emptyFormLevelEventFunction('onSubmit', 'formData, api')

    expect(fn).toBe('[[FORM-CREATE-PREFIX-function onSubmit(formData, api){}-FORM-CREATE-SUFFIX]]')

    expect(isEmptyFormCreateHandler(fn)).toBe(true)

    expect(fnEditorBody(fn)).toBe('')

  })



  it('FnEditor can extract body from normalized form-level handler', () => {

    const fn = emptyFormLevelEventFunction('onChange', 'field, value, options')

    expect(fnEditorBody(fn)).toBe('')

  })



  it('normalizes legacy anonymous form-level wrapper to named function', () => {

    const legacy = '[[FORM-CREATE-PREFIX-function (formData, api){\n}-FORM-CREATE-SUFFIX]]'

    const fixed = normalizeFormLevelEventHandler('onSubmit', 'formData, api', legacy)

    expect(fixed).toContain('function onSubmit(formData, api){')

    expect(fnEditorBody(fixed)).toBe('')

  })



  it('preserves custom form-level handler body when re-wrapping', () => {

    const legacy =

      "[[FORM-CREATE-PREFIX-function (field, value, options){\n  options.hidden(true, 'x')\n}-FORM-CREATE-SUFFIX]]"

    const fixed = normalizeFormLevelEventHandler('onChange', 'field, value, options', legacy)

    expect(fixed).toContain('function onChange(field, value, options){')

    expect(extractFormCreateHandlerBody(fixed)).toContain("options.hidden(true, 'x')")

  })



  it('seeds form-level onChange and onSubmit', () => {

    const opts = ensureEmptyFormOptionsEvents({ form: { labelPosition: 'left' } })

    expect(typeof opts.onChange).toBe('string')

    expect(typeof opts.onSubmit).toBe('string')

    expect(String(opts.onSubmit)).toContain('function onSubmit(formData, api){')

    expect(isEmptyFormCreateHandler(opts.onChange)).toBe(true)

  })



  it('seeds component on and _hook as $FNX body without overwriting custom body', () => {

    const rule: Record<string, unknown> = {

      type: 'input',

      field: 'case_number',

      on: {

        change: emptyComponentEventFunction('api.setValue("legal_hold", true)'),

      },

    }

    const changed = ensureEmptyRuleComponentEvents(rule)

    expect(changed).toBe(true)

    expect(rule.on?.blur).toBeDefined()

    expect(String(rule.on?.change)).toContain('legal_hold')

    expect(String(rule.on?.change)).toMatch(/^\$FNX:/)

    expect(rule.hook?.value).toBeDefined()

    expect(String(rule.hook?.value)).toBe('$FNX:')

  })

  it('uses _on and _hook on live designer canvas rules', () => {
    const rule: Record<string, unknown> = {
      type: 'switch',
      field: 'legal_hold',
      _fc_id: 'id_test',
    }
    ensureEmptyRuleComponentEvents(rule)
    expect(rule._on?.change).toBeDefined()
    expect(String(rule._on?.change)).toBe('$FNX:')
    expect(rule._hook?.value).toBeDefined()
    expect(rule.on).toBeUndefined()
  })

  it('walkRulesEnsureComponentEvents updates nested card children', () => {

    const rules = [

      {

        type: 'elCard',

        children: [{ type: 'switch', field: 'legal_hold', title: 'Legal Hold' }],

      },

    ]

    expect(walkRulesEnsureComponentEvents(rules)).toBe(true)

    const child = (rules[0] as Record<string, unknown>).children as Record<string, unknown>[]

    expect(child[0].hook?.value).toBeDefined()

    expect(String(child[0].hook?.value)).toBe('$FNX:')

  })

  it('flattenComponentEventsForPersist copies _on.blur to on for portal', () => {
    const rules = [
      {
        type: 'input',
        field: 'case_number',
        _on: {
          blur: '$FNX:\nif ($inject.value === "abc") { $inject.api.setValue("legal_hold", true) }',
        },
      },
    ] as Record<string, unknown>[]
    flattenComponentEventsForPersist(rules)
    const rule = rules[0] as Record<string, unknown>
    expect(rule._on).toBeUndefined()
    expect(String((rule.on as Record<string, unknown>).blur)).toContain('legal_hold')
  })

  it('normalizeEventEditorBody strips $FNX and pasted function wrappers', () => {
    expect(normalizeEventEditorBody('$FNX:\n  if (true) {}\n')).toBe('if (true) {}')
    expect(
      normalizeEventEditorBody(
        'function hook_value($inject) {\n  if ($inject.value === "abc") {}\n}',
      ),
    ).toBe('if ($inject.value === "abc") {}')
    expect(normalizeEventEditorBody('if ($inject.value === 1) {}')).toBe(
      'if ($inject.value === 1) {}',
    )
    expect(
      normalizeEventEditorBody(
        `function hook_value($inject) {
  if ($inject.rule.field === 'case_number' && $inject.value === 'abc') {
    $inject.api.setValue('legal_hold', true)
  }
}`,
      ),
    ).toContain("$inject.api.setValue('legal_hold', true)")
  })

})


