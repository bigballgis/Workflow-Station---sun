import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { ElSelect } from 'element-plus'
import {
  commitDesignerPanelEditsBeforePreview,
  flushDesignerValidatePanelToActiveRule,
  flushDesignerPropsPanelToActiveRule,
  installFcDesignerPreviewCapture,
  mergePreviewValidateFormOption,
  prepareDesignerPreviewValidation,
  wrapFcDesignerOpenPreview,
} from '../formDesignerPreviewValidation'

/**
 * Build a focused control inside the fc-designer right property panel (`._fc-r`).
 * `expanded` mirrors Element Plus marking an open Select/Cascader/DatePicker trigger
 * with aria-expanded="true" on the focused combobox input.
 */
function mountPanelControl(expanded: boolean): HTMLInputElement {
  const panel = document.createElement('div')
  panel.className = '_fc-r'
  const input = document.createElement('input')
  if (expanded) input.setAttribute('aria-expanded', 'true')
  panel.appendChild(input)
  document.body.appendChild(panel)
  input.focus()
  return input
}

describe('commitDesignerPanelEditsBeforePreview', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('blurs a plain focused control in the property panel so pending text edits commit', () => {
    const input = mountPanelControl(false)
    const blur = vi.spyOn(input, 'blur')
    commitDesignerPanelEditsBeforePreview()
    expect(blur).toHaveBeenCalled()
  })

  // Regression: the auto-save poll calls this on every tick. Blurring a control whose popper
  // is open makes Element Plus set expanded=false, so every property-panel dropdown (Sub Table
  // Binding, Lookup, Link Form ...) shut itself within one poll interval of opening and could
  // not be used. Such controls commit on `change`, not on blur — skipping loses no edit.
  it('leaves a control with an open popper focused so its dropdown stays open', () => {
    const input = mountPanelControl(true)
    const blur = vi.spyOn(input, 'blur')
    commitDesignerPanelEditsBeforePreview()
    expect(blur).not.toHaveBeenCalled()
  })

  it('ignores focus outside the designer panels', () => {
    const outside = document.createElement('input')
    document.body.appendChild(outside)
    outside.focus()
    const blur = vi.spyOn(outside, 'blur')
    commitDesignerPanelEditsBeforePreview()
    expect(blur).not.toHaveBeenCalled()
  })

  // The tests above assert against a hand-built aria-expanded input, i.e. against our own
  // reading of the markup. This one drives a REAL ElSelect so the guard is pinned to Element
  // Plus's actual DOM: if an upgrade stops marking the open trigger with aria-expanded, the
  // guard silently stops working and only this test catches it.
  it('leaves a real open ElSelect in the panel untouched', async () => {
    const panel = document.createElement('div')
    panel.className = '_fc-r'
    document.body.appendChild(panel)

    const select = mount(ElSelect, { attachTo: panel, props: { modelValue: null } })
    await select.find('.el-select__wrapper').trigger('click')
    await nextTick()

    const inner = panel.querySelector('input') as HTMLInputElement
    expect(inner).toBeTruthy()
    inner.focus()
    // Sanity: Element Plus really does mark the open trigger this way.
    expect((document.activeElement as HTMLElement).closest('[aria-expanded="true"]')).toBeTruthy()

    const blur = vi.spyOn(inner, 'blur')
    commitDesignerPanelEditsBeforePreview()
    expect(blur).not.toHaveBeenCalled()

    select.unmount()
  })
})

describe('formDesignerPreviewValidation', () => {
  it('mergePreviewValidateFormOption hides the built-in submit button and enables showMessage', () => {
    // Spec change: preview is layout/field inspection only — form-create's built-in bottom
    // submit/validate button is hidden (it ran api.submit() silently, looking like it did
    // nothing); designer-placed Validate button components in the rules are unaffected.
    const merged = mergePreviewValidateFormOption({ submitBtn: { show: true } }, 'Validate')
    expect(merged.submitBtn).toBe(false)
    expect(merged.resetBtn).toMatchObject({ show: false })
    expect(merged.form).toMatchObject({ showMessage: true })
    expect(merged.validateOnSubmit).toBe(true)
  })

  it('mergePreviewValidateFormOption forces validateOnSubmit when saved options disable it', () => {
    const merged = mergePreviewValidateFormOption(
      { validateOnSubmit: false, form: { showMessage: false } },
      'Validate',
    )
    expect(merged.validateOnSubmit).toBe(true)
    expect(merged.form).toMatchObject({ showMessage: true })
  })

  it('flushDesignerValidatePanelToActiveRule falls back to baseForm field when activeRule is cleared', () => {
    const activeRule: Record<string, unknown> = { field: 'name', type: 'input' }
    const ref = {
      activeRule: null,
      getRule: () => [activeRule],
      baseForm: {
        api: {
          formData: () => ({ field: 'name' }),
        },
      },
      validateForm: {
        api: {
          formData: () => ({
            validate: [{ len: 2, message: 'two chars' }],
            $required: true,
          }),
        },
      },
    }
    const result = flushDesignerValidatePanelToActiveRule(ref)
    expect(result.flushed).toBe(true)
    expect(activeRule.validate).toEqual([{ len: 2, message: 'two chars' }])
  })

  it('flushDesignerPropsPanelToActiveRule copies readonly=false from props panel onto stale PK rule', () => {
    const activeRule: Record<string, unknown> = {
      field: 'case_number',
      type: 'input',
      readonly: true,
      disabled: true,
      props: { disabled: true },
    }
    const ref = {
      activeRule,
      propsForm: {
        api: {
          formData: () => ({ readonly: false }),
        },
      },
    }
    const result = flushDesignerPropsPanelToActiveRule(ref)
    expect(result.flushed).toBe(true)
    expect(result.readonly).toBe(false)
    expect(activeRule.readonly).toBe(false)
    expect((activeRule.props as Record<string, unknown>).readonly).toBe(false)
    expect(activeRule.disabled).toBeUndefined()
    expect((activeRule.props as Record<string, unknown>).disabled).toBeUndefined()
  })

  it('flushDesignerValidatePanelToActiveRule keeps Validation+ rows that omit Error', () => {
    const activeRule: Record<string, unknown> = { field: 'name', type: 'input' }
    const ref = {
      activeRule,
      validateForm: {
        api: {
          formData: () => ({
            validate: [{ mode: 'email', email: true, trigger: 'blur', adapter: true }],
            $required: false,
          }),
        },
      },
    }
    const result = flushDesignerValidatePanelToActiveRule(ref)
    expect(result.flushed).toBe(true)
    expect(activeRule.validate).toEqual([
      { mode: 'email', email: true, trigger: 'blur', adapter: true },
    ])
  })

  it('flushDesignerValidatePanelToActiveRule copies pending validate panel data onto activeRule', () => {
    const activeRule: Record<string, unknown> = { field: 'name', type: 'input' }
    const ref = {
      activeRule,
      validateForm: {
        api: {
          formData: () => ({
            validate: [{ len: 2, message: 'must be 2 chars' }],
            $required: true,
          }),
        },
      },
    }
    const result = flushDesignerValidatePanelToActiveRule(ref)
    expect(result.flushed).toBe(true)
    expect(result.source).toBe('formData')
    expect(activeRule.validate).toEqual([{ len: 2, message: 'must be 2 chars' }])
    expect(activeRule.$required).toBe(true)
  })

  it('prepareDesignerPreviewValidation writes normalized rules back via setRule', () => {
    const activeRule: Record<string, unknown> = {
      field: 'name',
      type: 'input',
      $required: true,
      validate: [{ len: 2, message: 'x' }],
    }
    const rules = [activeRule]
    const setRule = vi.fn()
    const getRule = vi.fn(() => rules)
    const getOption = vi.fn(() => ({}))
    const setOption = vi.fn()
    prepareDesignerPreviewValidation(
      {
        getRule,
        setRule,
        getOption,
        setOption,
        activeRule,
        validateForm: { api: { formData: () => ({ validate: activeRule.validate, $required: true }) } },
      },
      'Validate',
    )
    expect(setRule).toHaveBeenCalledTimes(1)
    expect(setRule.mock.calls[0][0][0].validate).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ required: true, trigger: 'blur' }),
        expect.objectContaining({ len: 2, trigger: 'blur' }),
      ]),
    )
    expect(setOption).toHaveBeenCalled()
  })

  it('flushDesignerValidatePanelToActiveRule clears validate when panel returns empty array', () => {
    const activeRule: Record<string, unknown> = {
      field: 'name',
      type: 'input',
      validate: [{ len: 2, message: 'x' }],
    }
    const ref = {
      activeRule,
      validateForm: {
        api: {
          formData: () => ({ validate: [] }),
        },
      },
    }
    const result = flushDesignerValidatePanelToActiveRule(ref)
    expect(result.flushed).toBe(true)
    expect(activeRule.validate).toBeUndefined()
  })

  it('installFcDesignerPreviewCapture runs on toolbar preview button mousedown', () => {
    const root = document.createElement('div')
    const btn = document.createElement('button')
    const icon = document.createElement('i')
    icon.className = 'fc-icon icon-preview'
    btn.appendChild(icon)
    root.appendChild(btn)
    const activeRule: Record<string, unknown> = { field: 'a', type: 'input' }
    const setRule = vi.fn()
    installFcDesignerPreviewCapture(
      root,
      () => ({
        getRule: () => [{ ...activeRule, $required: true }],
        setRule,
        getOption: () => ({}),
        setOption: vi.fn(),
        activeRule,
        validateForm: {
          api: { formData: () => ({ validate: [{ required: true }], $required: true }) },
        },
      }),
      'Validate',
    )
    btn.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(setRule).toHaveBeenCalled()
  })

  it('preview toolbar click bubble recompiles preview.rule after native openPreview', () => {
    const root = document.createElement('div')
    const btn = document.createElement('button')
    const icon = document.createElement('i')
    icon.className = 'fc-icon icon-preview'
    btn.appendChild(icon)
    root.appendChild(btn)

    const form: Record<string, unknown> = { scenario: '', start_date: '' }
    const effects: Array<[string, string, unknown]> = []
    const api = {
      setValue: (field: string, value: unknown) => { form[field] = value },
      getValue: (field: string) => form[field],
      form,
      hidden: () => {},
      display: () => {},
      hiddenStatus: () => false,
      displayStatus: () => true,
      setFieldError: () => {},
      clearFieldError: () => {},
      mergeRule: () => {},
      setEffect: (id: string, attr: string, value: unknown) => { effects.push([id, attr, value]) },
      sync: () => {},
    }
    const src =
      "$FNX:\nvar on = $inject.value === 'A'\n$inject.api.required(on, ['start_date'])"
    const nativeChange = Object.assign(
      function ($inject: { api: typeof api; value?: unknown }) {
        if ($inject.value === 'A') $inject.api.setValue('start_date', 'broken')
      },
      { __json: src, __inject: true },
    )
    const preview = {
      state: false,
      rule: [] as Array<Record<string, unknown>>,
      option: {} as Record<string, unknown>,
    }
    const ref = {
      preview,
      getRule: () => [{ type: 'select', field: 'scenario', _on: { change: src } }],
      setRule: vi.fn(),
      getOption: () => ({}),
      setOption: vi.fn(),
    }
    // fc-designer @click="openPreview" runs on the button before the event bubbles to .form-editor-view
    btn.addEventListener('click', () => {
      preview.state = true
      preview.rule = [
        { type: 'select', field: 'scenario', on: { change: nativeChange }, inject: true },
        { type: 'input', field: 'start_date' },
      ]
    })
    installFcDesignerPreviewCapture(root, () => ref, 'Validate')
    btn.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    const change = preview.rule[0].on?.change as (inject: unknown) => void
    expect(typeof change).toBe('function')
    change({ api, args: ['A'] })
    expect(form.start_date).not.toBe('broken')
    expect(effects).toContainEqual(['start_date', 'required', true])
  })

  it('wrapFcDesignerOpenPreview recompiles preview.rule so $inject.value works after getJson', () => {
    const form: Record<string, unknown> = { case_type: 'CNP', card_number: '' }
    const api = {
      setValue: (field: string, value: unknown) => { form[field] = value },
      getValue: (field: string) => form[field],
      form,
      hidden: () => {},
      display: () => {},
      hiddenStatus: () => false,
      displayStatus: () => true,
      setFieldError: () => {},
      clearFieldError: () => {},
    }
    // Mimic parseJson output: native $FNX compiled without `value` on inject
    const nativeChange = Object.assign(
      function ($inject: { api: typeof api; args?: unknown[] }) {
        // Stock EventConfig scripts use $inject.value — undefined in native inject.
        const v = ($inject as { value?: unknown }).value
        if (v === 'CNP') $inject.api.setValue('card_number', 'broken')
      },
      {
        __json:
          '$FNX:\nvar v = $inject.value\nif (v === \'CNP\') { $inject.api.setValue(\'card_number\', \'111\') }',
        __inject: true,
      },
    )
    const preview = {
      state: false,
      rule: [
        { type: 'select', field: 'case_type', on: { change: nativeChange }, inject: true },
        { type: 'input', field: 'card_number' },
      ] as Array<Record<string, unknown>>,
      option: {} as Record<string, unknown>,
    }
    const ref = {
      preview,
      getRule: () => [{ type: 'select', field: 'case_type', _on: { change: nativeChange.__json } }],
      setRule: vi.fn(),
      getOption: () => ({}),
      setOption: vi.fn(),
      openPreview() {
        preview.state = true
        // fc-designer assigns parseJson result here — leave native handler in place
      },
    }
    wrapFcDesignerOpenPreview(ref, 'Validate')
    ref.openPreview()
    const change = preview.rule[0].on?.change as (inject: unknown) => void
    expect(typeof change).toBe('function')
    // Native inject shape: value missing, selected option only in args[0]
    change({ api, args: ['CNP'] })
    expect(form.card_number).toBe('111')
    expect(preview.option.injectEvent).toBe(true)
  })
})
