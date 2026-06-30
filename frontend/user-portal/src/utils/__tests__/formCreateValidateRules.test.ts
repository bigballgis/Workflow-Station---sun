import { describe, expect, it } from 'vitest'

import type { FormField } from '@/components/formRendererHelpers'

import {

  applyFormCreateValidationToFormField,

  convertFormCreateDesignerValidateEntry,

  FORM_CREATE_VALIDATOR_ADAPTER_KEY,

  FORM_CREATE_VALIDATOR_SOURCE_KEY,

  isFormCreateRuleRequired,

  mapFormCreateValidateToElementPlusRules,

  materializeFormCreateValidationRules,

} from '../formCreateValidateRules'

const B_GREATER_THAN_A_AND_C_VALIDATOR =
  "[[FORM-CREATE-PREFIX-function validator(rule, value, callback){\n  const api = this.api;\n  const b = Number(value);\n  const a = Number(api.getValue('a'));\n  const c = Number(api.getValue('c'));\n  if (value === '' || value == null) { callback(); return; }\n  if (Number.isNaN(b) || Number.isNaN(a) || Number.isNaN(c)) { callback('invalid number'); return; }\n  if (b <= a || b <= c) { callback('b must be greater than a and c'); return; }\n  callback();\n}-FORM-CREATE-SUFFIX]]"

/** Designer adapter-mode editor: validator(value, callback) */
const B_GREATER_THAN_A_AND_C_ADAPTER_VALIDATOR =
  "[[FORM-CREATE-PREFIX-function validator(value, callback){\n  const api = this.api;\n  const b = Number(value);\n  const a = Number(api.getValue('a'));\n  const c = Number(api.getValue('c'));\n  if (value === '' || value == null) { callback(); return; }\n  if (Number.isNaN(b) || Number.isNaN(a) || Number.isNaN(c)) { callback('invalid number'); return; }\n  if (b <= a || b <= c) { callback('b must be greater than a and c'); return; }\n  callback();\n}-FORM-CREATE-SUFFIX]]"



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



  it('mapFormCreateValidateToElementPlusRules uses boolean required for switch fields', () => {
    const rules = mapFormCreateValidateToElementPlusRules({
      $required: true,
    }, 'switch')

    expect(rules).toHaveLength(1)
    expect(rules[0]).toMatchObject({ type: 'boolean', required: true, trigger: 'change' })
  })

  it('convertFormCreateDesignerValidateEntry maps required mode for switch as boolean', () => {
    expect(
      convertFormCreateDesignerValidateEntry({
        mode: 'required',
        message: 'Legal Hold is required',
        trigger: 'change',
      }, 'switch'),
    ).toEqual({
      type: 'boolean',
      required: true,
      trigger: 'change',
      message: 'Legal Hold is required',
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

  it('convertFormCreateDesignerValidateEntry preserves string validator for portal materialization', () => {
    const entry = convertFormCreateDesignerValidateEntry({
      mode: 'validator',
      validator: B_GREATER_THAN_A_AND_C_VALIDATOR,
      trigger: 'blur',
      message: 'b invalid',
    })

    expect(entry?.[FORM_CREATE_VALIDATOR_SOURCE_KEY]).toBe(B_GREATER_THAN_A_AND_C_VALIDATOR)
    expect(entry).not.toHaveProperty('validator')
  })

  it('materializeFormCreateValidationRules binds cross-field validator against form data', async () => {
    const deferred = convertFormCreateDesignerValidateEntry({
      mode: 'validator',
      validator: B_GREATER_THAN_A_AND_C_VALIDATOR,
      trigger: 'blur',
    })
    expect(deferred).toBeTruthy()

    const materialized = materializeFormCreateValidationRules(
      [deferred as Record<string, unknown>],
      () => ({ a: 1, b: 5, c: 3 }),
    )
    expect(typeof materialized[0].validator).toBe('function')

    await new Promise<void>((resolve, reject) => {
      ;(materialized[0].validator as (rule: unknown, value: unknown, cb: (err?: Error | string) => void) => void)(
        {},
        5,
        (err) => {
          try {
            expect(err).toBeUndefined()
            resolve()
          } catch (e) {
            reject(e)
          }
        },
      )
    })

    await new Promise<void>((resolve, reject) => {
      ;(materialized[0].validator as (rule: unknown, value: unknown, cb: (err?: Error | string) => void) => void)(
        {},
        2,
        (err) => {
          try {
            expect(err).toBeInstanceOf(Error)
            expect((err as Error).message).toBe('b must be greater than a and c')
            resolve()
          } catch (e) {
            reject(e)
          }
        },
      )
    })
  })

  it('materializeFormCreateValidationRules supports adapter validator(value,callback) fill order a,c,b', async () => {
    const materialized = materializeFormCreateValidationRules(
      [{
        [FORM_CREATE_VALIDATOR_SOURCE_KEY]: B_GREATER_THAN_A_AND_C_ADAPTER_VALIDATOR,
        [FORM_CREATE_VALIDATOR_ADAPTER_KEY]: true,
        message: 'xxxx',
        trigger: 'blur',
      }],
      () => ({ a: 5, c: 3, b: 6 }),
    )

    await new Promise<void>((resolve, reject) => {
      ;(materialized[0].validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
        {},
        6,
        (err) => {
          try {
            expect(err).toBeUndefined()
            resolve()
          } catch (e) {
            reject(e)
          }
        },
      )
    })
  })

  it('materializeFormCreateValidationRules honors adapter:true with 3-param validator (a=5,b=6,c=3)', async () => {
    const materialized = materializeFormCreateValidationRules(
      [{
        [FORM_CREATE_VALIDATOR_SOURCE_KEY]: B_GREATER_THAN_A_AND_C_VALIDATOR,
        [FORM_CREATE_VALIDATOR_ADAPTER_KEY]: true,
        message: 'xxxx',
        trigger: 'blur',
      }],
      () => ({ a: 5, b: 6, c: 3 }),
    )

    await new Promise<void>((resolve, reject) => {
      ;(materialized[0].validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
        {},
        6,
        (err) => {
          try {
            expect(err).toBeUndefined()
            resolve()
          } catch (e) {
            reject(e)
          }
        },
      )
    })
  })

  it('applyFormCreateValidationToFormField keeps deferred validator on field.rules', () => {
    const field: FormField = { key: 'b', label: 'b', type: 'text' }
    applyFormCreateValidationToFormField(field, {
      validate: [{
        mode: 'validator',
        validator: B_GREATER_THAN_A_AND_C_VALIDATOR,
        trigger: 'blur',
      }],
    })

    expect(field.rules).toHaveLength(1)
    expect(field.rules?.[0]?.[FORM_CREATE_VALIDATOR_SOURCE_KEY]).toBe(B_GREATER_THAN_A_AND_C_VALIDATOR)
  })
})


