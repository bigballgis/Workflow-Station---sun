import { describe, expect, it, vi } from 'vitest'
import {
  flushDesignerValidatePanelToActiveRule,
  flushDesignerPropsPanelToActiveRule,
  installFcDesignerPreviewCapture,
  mergePreviewValidateFormOption,
  prepareDesignerPreviewValidation,
} from '../formDesignerPreviewValidation'

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
})
