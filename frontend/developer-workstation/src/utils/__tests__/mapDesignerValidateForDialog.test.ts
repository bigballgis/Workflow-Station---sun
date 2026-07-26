import { describe, expect, it } from 'vitest'
import { mapDesignerValidateForDialog } from '../mapDesignerValidateForDialog'

type ModeCase = {
  mode: string
  designer: Record<string, unknown>
  pass: unknown[]
  fail: unknown[]
  /** null = mode intentionally skipped (custom validator scripts) */
  expectMapped?: boolean
}

const MODE_CASES: ModeCase[] = [
  {
    mode: 'len',
    designer: { mode: 'len', len: 1, adapter: true, trigger: 'blur', message: 'len err' },
    pass: ['A'],
    fail: ['11'],
  },
  {
    mode: 'maxLen',
    designer: { mode: 'maxLen', maxLen: 2, adapter: true, trigger: 'blur', message: 'maxLen err' },
    pass: ['ab'],
    fail: ['abc'],
  },
  {
    mode: 'minLen',
    designer: { mode: 'minLen', minLen: 3, adapter: true, trigger: 'blur', message: 'minLen err' },
    pass: ['abc'],
    fail: ['ab'],
  },
  {
    mode: 'pattern',
    designer: { mode: 'pattern', pattern: '^\\d+$', adapter: true, trigger: 'blur', message: 'p' },
    pass: ['123'],
    fail: ['12a'],
  },
  {
    mode: 'uppercase',
    designer: { mode: 'uppercase', uppercase: true, adapter: true, trigger: 'blur', message: 'u' },
    pass: ['ABC'],
    fail: ['Abc'],
  },
  {
    mode: 'lowercase',
    designer: { mode: 'lowercase', lowercase: true, adapter: true, trigger: 'blur', message: 'l' },
    pass: ['abc'],
    fail: ['Abc'],
  },
  {
    mode: 'email',
    designer: { mode: 'email', email: true, adapter: true, trigger: 'blur', message: 'e' },
    pass: [],
    fail: [],
  },
  {
    mode: 'url',
    designer: { mode: 'url', url: true, adapter: true, trigger: 'blur', message: 'u' },
    pass: [],
    fail: [],
  },
  {
    mode: 'ip',
    designer: { mode: 'ip', ip: true, adapter: true, trigger: 'blur', message: 'ip' },
    pass: ['192.168.1.1'],
    fail: ['999.1.1.1'],
  },
  {
    mode: 'phone',
    designer: { mode: 'phone', phone: true, adapter: true, trigger: 'blur', message: 'ph' },
    pass: ['13800138000'],
    fail: ['12345'],
  },
  {
    mode: 'min',
    designer: { mode: 'min', min: 5, adapter: true, trigger: 'blur', message: 'min' },
    pass: [5],
    fail: [4],
  },
  {
    mode: 'max',
    designer: { mode: 'max', max: 10, adapter: true, trigger: 'blur', message: 'max' },
    pass: [10],
    fail: [11],
  },
  {
    mode: 'positive',
    designer: { mode: 'positive', positive: true, adapter: true, trigger: 'blur', message: 'pos' },
    pass: [1],
    fail: [0],
  },
  {
    mode: 'negative',
    designer: { mode: 'negative', negative: true, adapter: true, trigger: 'blur', message: 'neg' },
    pass: [-1],
    fail: [0],
  },
  {
    mode: 'integer',
    designer: { mode: 'integer', integer: true, adapter: true, trigger: 'blur', message: 'int' },
    pass: [3],
    fail: [3.5],
  },
  {
    mode: 'number',
    designer: { mode: 'number', number: true, adapter: true, trigger: 'blur', message: 'num' },
    pass: [1.5],
    fail: ['abc'],
  },
  {
    mode: 'validator',
    designer: {
      mode: 'validator',
      adapter: true,
      trigger: 'blur',
      validator: '[[FORM-CREATE-PREFIX-function validator(rule,value,callback){callback()}-FORM-CREATE-SUFFIX]]',
    },
    pass: [],
    fail: [],
    expectMapped: false,
  },
]

function runValidator(rule: Record<string, unknown>, value: unknown): Promise<boolean> {
  return new Promise((resolve) => {
    const fn = rule.validator as
      | ((r: unknown, v: unknown, cb: (err?: Error) => void) => void)
      | undefined
    if (typeof fn !== 'function') {
      resolve(true)
      return
    }
    fn({}, value, (err) => resolve(err == null))
  })
}

describe('mapDesignerValidateForDialog — all Validation+ modes', () => {
  it('covers every designer Validation+ dropdown entry', () => {
    const covered = MODE_CASES.map((c) => c.mode).sort()
    expect(covered).toEqual([
      'email',
      'integer',
      'ip',
      'len',
      'lowercase',
      'max',
      'maxLen',
      'min',
      'minLen',
      'negative',
      'number',
      'pattern',
      'phone',
      'positive',
      'uppercase',
      'url',
      'validator',
    ].sort())
  })

  it.each(MODE_CASES)(
    'mode=$mode maps (or skips validator) and validates samples',
    async ({ designer, pass, fail, expectMapped = true }) => {
      const rules = mapDesignerValidateForDialog({ validate: [designer] }, 'text')
      if (!expectMapped) {
        expect(rules).toEqual([])
        return
      }
      expect(rules.length).toBeGreaterThan(0)
      const rule = rules[0]

      if (typeof rule.validator === 'function') {
        for (const v of pass) expect(await runValidator(rule, v)).toBe(true)
        for (const v of fail) expect(await runValidator(rule, v)).toBe(false)
        return
      }

      // Shape checks for async-validator-native entries
      if (designer.mode === 'len') expect(rule).toMatchObject({ len: 1, trigger: 'blur' })
      if (designer.mode === 'maxLen') expect(rule).toMatchObject({ max: 2, type: 'string' })
      if (designer.mode === 'minLen') expect(rule).toMatchObject({ min: 3, type: 'string' })
      if (designer.mode === 'email') expect(rule).toMatchObject({ type: 'email' })
      if (designer.mode === 'url') expect(rule).toMatchObject({ type: 'url' })
      if (designer.mode === 'pattern') expect(rule.pattern).toBeTruthy()
    },
  )

  it('adds required from $required when validate has only len', () => {
    const rules = mapDesignerValidateForDialog({
      $required: true,
      validate: [{ mode: 'len', len: 2, trigger: 'blur' }],
    }, 'text')
    expect(rules).toHaveLength(2)
    expect(rules[0]).toMatchObject({ required: true, trigger: 'blur' })
    expect(rules[1]).toMatchObject({ len: 2, trigger: 'blur' })
  })
})
