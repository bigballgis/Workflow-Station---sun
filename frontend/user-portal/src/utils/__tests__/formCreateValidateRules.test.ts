import { describe, expect, it } from 'vitest'

import type { FormField } from '@/components/formRendererHelpers'

import {

  applyFormCreateValidationToFormField,

  convertFormCreateDesignerValidateEntry,

  isFormCreateRuleRequired,

  mapFormCreateValidateToElementPlusRules,

} from '../formCreateValidateRules'



describe('formCreateValidateRules', () => {

  it('isFormCreateRuleRequired reads $required flag', () => {
    expect(isFormCreateRuleRequired({ $required: true })).toBe(true)
    expect(isFormCreateRuleRequired({ $required: 'Required' })).toBe(true)
    expect(isFormCreateRuleRequired({ validate: [{ required: true, message: 'x' }] })).toBe(true)
    expect(isFormCreateRuleRequired({ validate: [{ mode: 'required', trigger: 'blur' }] })).toBe(true)
    expect(isFormCreateRuleRequired({ validate: [{ len: 2, message: 'x' }] })).toBe(false)
  })



  it('convertFormCreateDesignerValidateEntry maps maxLen to async-validator max', () => {

    expect(

      convertFormCreateDesignerValidateEntry({

        mode: 'maxLen',

        maxLen: 2,

        message: 'too long',

        trigger: 'blur',

      }),

    ).toEqual({

      max: 2,

      type: 'string',

      message: 'too long',

      trigger: 'blur',

    })

  })



  it('convertFormCreateDesignerValidateEntry maps minLen to async-validator min', () => {

    expect(

      convertFormCreateDesignerValidateEntry({

        mode: 'minLen',

        minLen: 3,

        trigger: 'blur',

      }),

    ).toEqual({

      min: 3,

      type: 'string',

      trigger: 'blur',

    })

  })



  it('mapFormCreateValidateToElementPlusRules preserves len and adds required from $required', () => {

    const rules = mapFormCreateValidateToElementPlusRules({

      $required: true,

      validate: [{ len: 2, mode: 'len', adapter: true, message: 'xxxxx', trigger: 'blur' }],

    }, 'text')



    expect(rules).toHaveLength(2)

    expect(rules[0]).toMatchObject({ required: true, trigger: 'blur' })

    expect(rules[1]).toMatchObject({ len: 2, message: 'xxxxx', trigger: 'blur' })

    expect(rules[1]).not.toHaveProperty('mode')

    expect(rules[1]).not.toHaveProperty('adapter')

  })



  it('mapFormCreateValidateToElementPlusRules maps designer maxLen mode', () => {

    const rules = mapFormCreateValidateToElementPlusRules({

      validate: [{ mode: 'maxLen', maxLen: 2, message: 'xxxx', trigger: 'blur' }],

    }, 'text')



    expect(rules).toHaveLength(1)

    expect(rules[0]).toEqual({

      max: 2,

      type: 'string',

      message: 'xxxx',

      trigger: 'blur',

    })

  })



  it('applyFormCreateValidationToFormField sets field.required and field.rules', () => {

    const field: FormField = { key: 'I', label: 'lDDFF', type: 'text' }

    applyFormCreateValidationToFormField(field, {

      $required: true,

      validate: [{ mode: 'maxLen', maxLen: 2, message: 'xxxxx', trigger: 'blur' }],

    })



    expect(field.required).toBe(true)

    expect(field.rules).toHaveLength(2)

    expect(field.rules?.[1]).toMatchObject({ max: 2, type: 'string' })
  })

  it('applyFormCreateValidationToFormField maps props.maxlength to field.maxLength and max rule', () => {
    const field: FormField = { key: 'I', label: 'IDDFF', type: 'text' }
    applyFormCreateValidationToFormField(field, {
      props: { maxlength: 255 },
    })

    expect(field.maxLength).toBe(255)
    expect(field.rules).toHaveLength(1)
    expect(field.rules?.[0]).toMatchObject({ max: 255, type: 'string' })
  })
})


