import { describe, expect, it, vi } from 'vitest'

import type { FormField } from '@/components/formRendererHelpers'

import {

  applyFormCreateValidationToFormField,

  convertFormCreateDesignerValidateEntry,

  inferFormCreateDesignerValidateMode,

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



  it('inferFormCreateDesignerValidateMode reads adapter validate entries without mode key', () => {
    expect(inferFormCreateDesignerValidateMode({ min: 1, adapter: true, trigger: 'blur' })).toBe('min')
    expect(inferFormCreateDesignerValidateMode({ max: 10, adapter: true, trigger: 'blur' })).toBe('max')
    expect(inferFormCreateDesignerValidateMode({ positive: true, adapter: true, trigger: 'blur' })).toBe('positive')
    expect(inferFormCreateDesignerValidateMode({ negative: true, adapter: true, trigger: 'blur' })).toBe('negative')
    expect(inferFormCreateDesignerValidateMode({ integer: true, adapter: true, trigger: 'blur' })).toBe('integer')
    expect(inferFormCreateDesignerValidateMode({ number: true, adapter: true, trigger: 'blur' })).toBe('number')
    expect(inferFormCreateDesignerValidateMode({ mode: 'phone', phone: true })).toBe('phone')
  })

  it('convertFormCreateDesignerValidateEntry maps adapter min/max without mode as numeric bounds', () => {
    type ValidatorFn = (rule: unknown, value: unknown, cb: (err?: Error) => void) => void
    const run = (entry: Record<string, unknown> | null, value: unknown) => {
      const cb = vi.fn()
      ;(entry?.validator as ValidatorFn)({}, value, cb)
      return cb
    }

    const minEntry = convertFormCreateDesignerValidateEntry({
      min: 1,
      message: 'xxxx',
      trigger: 'blur',
      adapter: true,
    }, 'text')
    expect(run(minEntry, '0').mock.calls[0][0]?.message).toBe('xxxx')
    expect(run(minEntry, '11')).toHaveBeenCalledWith()

    const minNegativeOne = convertFormCreateDesignerValidateEntry({
      min: -1,
      mode: 'min',
      message: 'lo',
      trigger: 'blur',
      adapter: true,
    }, 'text')
    expect(run(minNegativeOne, '0')).toHaveBeenCalledWith()

    const maxEntry = convertFormCreateDesignerValidateEntry({
      max: 10,
      message: 'yyyy',
      trigger: 'blur',
      adapter: true,
    }, 'text')
    expect(run(maxEntry, '11').mock.calls[0][0]?.message).toBe('yyyy')
    expect(run(maxEntry, '5')).toHaveBeenCalledWith()
  })

  it('applyFormCreateValidationToFormField does not add props maxlength rule when designer max exists', () => {
    const field: FormField = { key: 'T', label: 'T', type: 'text' }
    applyFormCreateValidationToFormField(field, {
      props: { maxlength: 2 },
      validate: [{ max: 10, message: 'xxxx', trigger: 'blur', adapter: true }],
    })
    expect(field.maxLength).toBe(2)
    expect(field.rules).toHaveLength(1)
    expect(field.rules?.[0]).toMatchObject({ message: 'xxxx', trigger: 'blur' })
    expect(field.rules?.[0]?.max).toBeUndefined()
  })

  it('convertFormCreateDesignerValidateEntry maps min/max mode to numeric comparison', () => {
    const minEntry = convertFormCreateDesignerValidateEntry({
      mode: 'min',
      min: 1,
      message: 'min err',
      trigger: 'blur',
    }, 'text')
    const maxEntry = convertFormCreateDesignerValidateEntry({
      mode: 'max',
      max: 10,
      message: 'max err',
      trigger: 'blur',
    }, 'text')
    expect(minEntry).toMatchObject({ message: 'min err', trigger: 'blur' })
    expect(maxEntry).toMatchObject({ message: 'max err', trigger: 'blur' })
    expect(typeof minEntry?.validator).toBe('function')
    expect(typeof maxEntry?.validator).toBe('function')

    const minPass = vi.fn()
    ;(minEntry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '5',
      minPass,
    )
    expect(minPass).toHaveBeenCalledWith()

    const minFail = vi.fn()
    ;(minEntry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '0',
      minFail,
    )
    expect(minFail.mock.calls[0][0]?.message).toBe('min err')

    const maxPass = vi.fn()
    ;(maxEntry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '10',
      maxPass,
    )
    expect(maxPass).toHaveBeenCalledWith()

    const maxFail = vi.fn()
    ;(maxEntry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '11',
      maxFail,
    )
    expect(maxFail.mock.calls[0][0]?.message).toBe('max err')

    const notNumber = vi.fn()
    ;(minEntry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      'abc',
      notNumber,
    )
    expect(notNumber.mock.calls[0][0]?.message).toBe('min err')
  })

  it('applyFormCreateValidationToFormField wires min and max validate from saved rule', () => {
    const field: FormField = { key: 'T', label: 'T', type: 'text' }
    applyFormCreateValidationToFormField(field, {
      field: 'T',
      type: 'input',
      validate: [
        { mode: 'min', min: 1, message: 'xxxx', trigger: 'blur' },
        { mode: 'max', max: 10, message: 'yyyy', trigger: 'blur' },
      ],
    })
    expect(field.rules).toHaveLength(2)
    expect(field.rules?.[0]).toMatchObject({ message: 'xxxx', trigger: 'blur' })
    expect(field.rules?.[1]).toMatchObject({ message: 'yyyy', trigger: 'blur' })
  })

  it('convertFormCreateDesignerValidateEntry maps adapter positive/integer without mode key', () => {
    type ValidatorFn = (rule: unknown, value: unknown, cb: (err?: Error) => void) => void
    const run = (entry: Record<string, unknown> | null, value: unknown) => {
      const cb = vi.fn()
      ;(entry?.validator as ValidatorFn)({}, value, cb)
      return cb
    }

    const positive = convertFormCreateDesignerValidateEntry({
      positive: true,
      message: 'pos',
      trigger: 'blur',
      adapter: true,
    }, 'text')
    expect(run(positive, '2')).toHaveBeenCalledWith()
    expect(run(positive, '0').mock.calls[0][0]?.message).toBe('pos')

    const integer = convertFormCreateDesignerValidateEntry({
      integer: true,
      message: 'int',
      trigger: 'blur',
      adapter: true,
    }, 'text')
    expect(run(integer, '3.0')).toHaveBeenCalledWith()
    expect(run(integer, '3.1').mock.calls[0][0]?.message).toBe('int')
  })

  it('convertFormCreateDesignerValidateEntry maps positive/negative/integer/number modes', () => {
    type ValidatorFn = (rule: unknown, value: unknown, cb: (err?: Error) => void) => void
    const run = (entry: Record<string, unknown> | null, value: unknown) => {
      const cb = vi.fn()
      ;(entry?.validator as ValidatorFn)({}, value, cb)
      return cb
    }

    const positive = convertFormCreateDesignerValidateEntry({
      mode: 'positive',
      message: 'pos err',
      trigger: 'blur',
    }, 'text')
    expect(run(positive, '5')).toHaveBeenCalledWith()
    expect(run(positive, '0').mock.calls[0][0]?.message).toBe('pos err')
    expect(run(positive, '-1').mock.calls[0][0]?.message).toBe('pos err')

    const negative = convertFormCreateDesignerValidateEntry({
      mode: 'negative',
      message: 'neg err',
      trigger: 'blur',
    }, 'text')
    expect(run(negative, '-3')).toHaveBeenCalledWith()
    expect(run(negative, '0').mock.calls[0][0]?.message).toBe('neg err')
    expect(run(negative, '2').mock.calls[0][0]?.message).toBe('neg err')

    const integer = convertFormCreateDesignerValidateEntry({
      mode: 'integer',
      message: 'int err',
      trigger: 'blur',
    }, 'text')
    expect(run(integer, '3')).toHaveBeenCalledWith()
    expect(run(integer, '3.5').mock.calls[0][0]?.message).toBe('int err')

    const number = convertFormCreateDesignerValidateEntry({
      mode: 'number',
      message: 'num err',
      trigger: 'blur',
    }, 'text')
    expect(run(number, '3.14')).toHaveBeenCalledWith()
    expect(run(number, 'abc').mock.calls[0][0]?.message).toBe('num err')
  })

  it('applyFormCreateValidationToFormField wires positive negative integer number rules', () => {
    const field: FormField = { key: 'N', label: 'N', type: 'text' }
    applyFormCreateValidationToFormField(field, {
      validate: [
        { mode: 'positive', message: 'p', trigger: 'blur' },
        { mode: 'negative', message: 'n', trigger: 'blur' },
        { mode: 'integer', message: 'i', trigger: 'blur' },
        { mode: 'number', message: 'num', trigger: 'blur' },
      ],
    })
    expect(field.rules).toHaveLength(4)
    expect(field.rules?.every((r) => typeof r.validator === 'function')).toBe(true)
  })

  it('convertFormCreateDesignerValidateEntry maps ip mode to custom validator', () => {
    const entry = convertFormCreateDesignerValidateEntry({
      mode: 'ip',
      message: 'xxxx',
      trigger: 'blur',
    }, 'text')
    expect(entry).toMatchObject({ message: 'xxxx', trigger: 'blur' })
    expect(typeof entry?.validator).toBe('function')

    const pass = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '192.168.1.1',
      pass,
    )
    expect(pass).toHaveBeenCalledWith()

    const fail = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '999.999.999.999',
      fail,
    )
    expect(fail).toHaveBeenCalledTimes(1)
    expect(fail.mock.calls[0][0]?.message).toBe('xxxx')
  })

  it('convertFormCreateDesignerValidateEntry maps phone mode to custom validator', () => {
    const entry = convertFormCreateDesignerValidateEntry({
      mode: 'phone',
      message: 'bad phone',
      trigger: 'blur',
    }, 'text')
    expect(entry).toMatchObject({ message: 'bad phone', trigger: 'blur' })
    expect(typeof entry?.validator).toBe('function')

    const pass = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '13800138000',
      pass,
    )
    expect(pass).toHaveBeenCalledWith()

    const passWithPrefix = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '+8613800138000',
      passWithPrefix,
    )
    expect(passWithPrefix).toHaveBeenCalledWith()

    const fail = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      '12345',
      fail,
    )
    expect(fail).toHaveBeenCalledTimes(1)
    expect(fail.mock.calls[0][0]?.message).toBe('bad phone')
  })

  it('applyFormCreateValidationToFormField wires ip and phone validate from saved rule', () => {
    const ipField: FormField = { key: 'ipField', label: 'IP', type: 'text' }
    applyFormCreateValidationToFormField(ipField, {
      field: 'ipField',
      type: 'input',
      validate: [{ mode: 'ip', message: 'xxxx', trigger: 'blur' }],
    })
    expect(ipField.rules).toHaveLength(1)
    expect(ipField.rules?.[0]).toMatchObject({ message: 'xxxx', trigger: 'blur' })

    const phoneField: FormField = { key: 'phoneField', label: 'Phone', type: 'text' }
    applyFormCreateValidationToFormField(phoneField, {
      field: 'phoneField',
      type: 'input',
      validate: [{ mode: 'phone', message: 'invalid', trigger: 'blur' }],
    })
    expect(phoneField.rules).toHaveLength(1)
    expect(phoneField.rules?.[0]).toMatchObject({ message: 'invalid', trigger: 'blur' })
  })

  it('convertFormCreateDesignerValidateEntry maps uppercase mode to custom validator', () => {
    const entry = convertFormCreateDesignerValidateEntry({
      mode: 'uppercase',
      message: 'XXX',
      trigger: 'blur',
    }, 'text')
    expect(entry).toMatchObject({ message: 'XXX', trigger: 'blur' })
    expect(typeof entry?.validator).toBe('function')

    const callback = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      'ABC',
      callback,
    )
    expect(callback).toHaveBeenCalledWith()

    const fail = vi.fn()
    ;(entry?.validator as (rule: unknown, value: unknown, cb: (err?: Error) => void) => void)(
      {},
      'abc',
      fail,
    )
    expect(fail).toHaveBeenCalledTimes(1)
    expect(fail.mock.calls[0][0]).toBeInstanceOf(Error)
    expect(fail.mock.calls[0][0]?.message).toBe('XXX')
  })



  it('applyFormCreateValidationToFormField wires uppercase validate from saved rule', () => {
    const field: FormField = { key: 'T', label: 'T', type: 'text' }
    applyFormCreateValidationToFormField(field, {
      field: 'T',
      type: 'input',
      validate: [{ mode: 'uppercase', message: 'XXX', trigger: 'blur' }],
    })
    expect(field.rules).toHaveLength(1)
    expect(field.rules?.[0]).toMatchObject({ message: 'XXX', trigger: 'blur' })
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


