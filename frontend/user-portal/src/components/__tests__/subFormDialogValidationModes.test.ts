/**
 * Fixture coverage for every Form Design Validation+ mode on the sub-form
 * Add/Edit Record dialog path:
 *   designer validate[] → mapSubFormRuleToDialogColumns → buildRules
 *
 * Modes (designer dropdown): length / max length / min length / pattern /
 * uppercase / lowercase / email / url / IP / phone / min / max / positive /
 * negative / integer / number / validator.
 */
import { describe, expect, it } from 'vitest'
import Schema from 'async-validator'
import {
  buildRules,
  mapSubFormRuleToDialogColumns,
} from '../subTableAddDialogHelpers'
import {
  FORM_CREATE_VALIDATOR_SOURCE_KEY,
  materializeFormCreateValidationRules,
} from '@/utils/formCreateValidateRules'

type DesignerValidate = Record<string, unknown>

type ModeCase = {
  mode: string
  /** Designer validate[] entry as saved by fc-designer */
  designer: DesignerValidate
  /** Values that must pass */
  pass: unknown[]
  /** Values that must fail */
  fail: unknown[]
  /** When true, rule uses a custom validator function (not async-validator type/len/…). */
  customValidator?: boolean
  /** Custom validator scripts are deferred until materialize. */
  deferredValidator?: boolean
}

const CUSTOM_VALIDATOR_SRC =
  "[[FORM-CREATE-PREFIX-function validator(rule, value, callback){\n  if (value === '' || value == null) { callback(); return; }\n  if (String(value) === 'ok') { callback(); return; }\n  callback('must be ok');\n}-FORM-CREATE-SUFFIX]]"

/** Representative designer Validation+ fixtures + pass/fail samples. */
export const VALIDATION_PLUS_MODE_CASES: ModeCase[] = [
  {
    mode: 'len',
    designer: { mode: 'len', len: 1, adapter: true, trigger: 'blur', message: 'len err' },
    pass: ['A'],
    // empty string is skipped by async-validator when not required (same as form-create)
    fail: ['11', 'AB'],
  },
  {
    mode: 'maxLen',
    designer: { mode: 'maxLen', maxLen: 2, adapter: true, trigger: 'blur', message: 'maxLen err' },
    pass: ['ab', 'a'],
    fail: ['abc'],
  },
  {
    mode: 'minLen',
    designer: { mode: 'minLen', minLen: 3, adapter: true, trigger: 'blur', message: 'minLen err' },
    pass: ['abc', 'abcd'],
    fail: ['ab'],
  },
  {
    mode: 'pattern',
    designer: { mode: 'pattern', pattern: '^\\d+$', adapter: true, trigger: 'blur', message: 'pattern err' },
    pass: ['123'],
    fail: ['12a'],
  },
  {
    mode: 'uppercase',
    designer: { mode: 'uppercase', uppercase: true, adapter: true, trigger: 'blur', message: 'upper err' },
    pass: ['ABC'],
    fail: ['Abc'],
    customValidator: true,
  },
  {
    mode: 'lowercase',
    designer: { mode: 'lowercase', lowercase: true, adapter: true, trigger: 'blur', message: 'lower err' },
    pass: ['abc'],
    fail: ['Abc'],
    customValidator: true,
  },
  {
    mode: 'email',
    designer: { mode: 'email', email: true, adapter: true, trigger: 'blur', message: 'email err' },
    pass: ['a@b.com'],
    fail: ['not-an-email'],
  },
  {
    mode: 'url',
    designer: { mode: 'url', url: true, adapter: true, trigger: 'blur', message: 'url err' },
    pass: ['https://example.com'],
    fail: ['notaurl'],
  },
  {
    mode: 'ip',
    designer: { mode: 'ip', ip: true, adapter: true, trigger: 'blur', message: 'ip err' },
    pass: ['192.168.1.1'],
    fail: ['999.1.1.1'],
    customValidator: true,
  },
  {
    mode: 'phone',
    designer: { mode: 'phone', phone: true, adapter: true, trigger: 'blur', message: 'phone err' },
    pass: ['13800138000'],
    fail: ['12345'],
    customValidator: true,
  },
  {
    mode: 'min',
    designer: { mode: 'min', min: 5, adapter: true, trigger: 'blur', message: 'min err' },
    pass: [5, '6'],
    fail: [4, '3'],
    customValidator: true,
  },
  {
    mode: 'max',
    designer: { mode: 'max', max: 10, adapter: true, trigger: 'blur', message: 'max err' },
    pass: [10, '9'],
    fail: [11, '12'],
    customValidator: true,
  },
  {
    mode: 'positive',
    designer: { mode: 'positive', positive: true, adapter: true, trigger: 'blur', message: 'pos err' },
    pass: [1, '2'],
    fail: [0, '-1'],
    customValidator: true,
  },
  {
    mode: 'negative',
    designer: { mode: 'negative', negative: true, adapter: true, trigger: 'blur', message: 'neg err' },
    pass: [-1, '-2'],
    fail: [0, '1'],
    customValidator: true,
  },
  {
    mode: 'integer',
    designer: { mode: 'integer', integer: true, adapter: true, trigger: 'blur', message: 'int err' },
    pass: [3, '3'],
    fail: [3.5, '3.1'],
    customValidator: true,
  },
  {
    mode: 'number',
    designer: { mode: 'number', number: true, adapter: true, trigger: 'blur', message: 'num err' },
    pass: [1.5, '2'],
    fail: ['abc'],
    customValidator: true,
  },
  {
    mode: 'validator',
    designer: {
      mode: 'validator',
      adapter: true,
      trigger: 'blur',
      message: 'validator err',
      validator: CUSTOM_VALIDATOR_SRC,
    },
    pass: ['ok'],
    fail: ['nope'],
    deferredValidator: true,
  },
]

const lookupCtx = { lookupDbConfigs: {}, relationViewConfigs: {} }

function mapFieldRules(designer: DesignerValidate) {
  const cols = mapSubFormRuleToDialogColumns(
    [{ type: 'input', field: 'f', title: 'F', validate: [designer] }],
    lookupCtx,
  )
  expect(cols).toHaveLength(1)
  const formRules = buildRules(cols)
  const rules = formRules.f as Array<Record<string, unknown>> | undefined
  expect(rules?.length).toBeGreaterThan(0)
  return rules!
}

async function assertAsyncValidator(
  rule: Record<string, unknown>,
  value: unknown,
  expectPass: boolean,
) {
  const descriptor = { f: [rule] }
  const validator = new Schema(descriptor as ConstructorParameters<typeof Schema>[0])
  let failed = false
  try {
    await validator.validate({ f: value })
  } catch {
    failed = true
  }
  if (expectPass) expect(failed).toBe(false)
  else expect(failed).toBe(true)
}

function runCustomValidator(
  rule: Record<string, unknown>,
  value: unknown,
): Promise<boolean> {
  return new Promise((resolve) => {
    const fn = rule.validator as
      | ((r: unknown, v: unknown, cb: (err?: Error | string) => void) => void)
      | undefined
    expect(typeof fn).toBe('function')
    fn!({}, value, (err) => resolve(err == null))
  })
}

describe('sub-form dialog Validation+ modes (all designer dropdown entries)', () => {
  it.each(VALIDATION_PLUS_MODE_CASES.filter((c) => !c.deferredValidator))(
    'maps mode=$mode onto dialog rules and rejects fail samples',
    async ({ designer, pass, fail, customValidator }) => {
      const rules = mapFieldRules(designer)
      const rule = rules[0]

      if (customValidator) {
        for (const v of pass) {
          expect(await runCustomValidator(rule, v)).toBe(true)
        }
        for (const v of fail) {
          expect(await runCustomValidator(rule, v)).toBe(false)
        }
        return
      }

      for (const v of pass) {
        // async-validator treats empty string specially for len — '' fails len:1 which is correct
        await assertAsyncValidator(rule, v, true)
      }
      for (const v of fail) {
        // Skip empty string for pattern/email/url — required-empty is optional skip in form-create
        if (v === '' && (designer.mode === 'pattern' || designer.mode === 'email' || designer.mode === 'url')) {
          continue
        }
        await assertAsyncValidator(rule, v, false)
      }
    },
  )

  it('maps deferred custom validator and materializes for dialog formData', async () => {
    const caseRow = VALIDATION_PLUS_MODE_CASES.find((c) => c.mode === 'validator')!
    const rules = mapFieldRules(caseRow.designer)
    expect(rules[0][FORM_CREATE_VALIDATOR_SOURCE_KEY]).toBe(CUSTOM_VALIDATOR_SRC)

    const formData = { f: 'ok' }
    const materialized = materializeFormCreateValidationRules(
      rules,
      () => formData,
      () => [{ key: 'f', label: 'F' }],
    )
    expect(typeof materialized[0].validator).toBe('function')

    expect(await runCustomValidator(materialized[0], 'ok')).toBe(true)
    expect(await runCustomValidator(materialized[0], 'nope')).toBe(false)
  })

  it('buildRules keeps every Validation+ mode when columns carry rules', () => {
    for (const c of VALIDATION_PLUS_MODE_CASES) {
      if (c.deferredValidator) continue
      const rules = mapFieldRules(c.designer)
      expect(rules.length).toBeGreaterThan(0)
    }
  })

  it('len=1 fails "11" (Card Number regression fixture)', async () => {
    const rules = mapFieldRules({
      mode: 'len',
      len: 1,
      adapter: true,
      trigger: 'blur',
      message: 'must be 1',
    })
    await assertAsyncValidator(rules[0], '11', false)
    await assertAsyncValidator(rules[0], '1', true)
  })
})

describe('Validation+ mode inventory matches designer dropdown', () => {
  it('covers all Validation+ entries from the designer menu', () => {
    const covered = new Set(VALIDATION_PLUS_MODE_CASES.map((c) => c.mode))
    const expected = [
      'len',
      'maxLen',
      'minLen',
      'pattern',
      'uppercase',
      'lowercase',
      'email',
      'url',
      'ip',
      'phone',
      'min',
      'max',
      'positive',
      'negative',
      'integer',
      'number',
      'validator',
    ]
    expect([...covered].sort()).toEqual([...expected].sort())
  })
})
