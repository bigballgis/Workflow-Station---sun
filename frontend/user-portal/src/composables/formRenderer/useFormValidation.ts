import { nextTick, type Ref } from 'vue'
import type { FormInstance } from 'element-plus'
import type { BusinessLogicEngine } from '../../components/businessLogicEngine'
import type { FormBusinessLogicConfig } from '../../components/formRendererHelpers'

// ---------------------------------------------------------------------------
// Task 7.3: Form validation with engine integration
// ---------------------------------------------------------------------------

interface ValidationDeps {
  formRef: Ref<FormInstance | undefined>
  formData: Ref<Record<string, any>>
  config: () => FormBusinessLogicConfig | undefined
  engine: BusinessLogicEngine
  scriptFieldErrors: Ref<Record<string, string>>
}

export function useFormValidation(deps: ValidationDeps) {
  function findFormItemEl(fieldKey: string): HTMLElement | null {
    const root = deps.formRef.value?.$el as HTMLElement | undefined
    if (!root) return null
    return root.querySelector(
      `.el-form-item[data-field-key="${CSS.escape(fieldKey)}"]`,
    ) as HTMLElement | null
  }

  /**
   * Inject an engine validation error into an Element Plus form-item via DOM.
   * Adds the `is-error` class and appends an `.el-form-item__error` element.
   */
  function injectFieldError(fieldKey: string, message: string) {
    const itemEl = findFormItemEl(fieldKey)
    if (!itemEl) return
    itemEl.classList.add('is-error')
    const contentEl = itemEl.querySelector('.el-form-item__content')
    if (!contentEl) return
    contentEl.querySelectorAll('.engine-error').forEach(el => el.remove())
    const errorDiv = document.createElement('div')
    errorDiv.className = 'el-form-item__error engine-error'
    errorDiv.textContent = message
    contentEl.appendChild(errorDiv)
  }

  function clearInjectedFieldError(fieldKey: string) {
    const itemEl = findFormItemEl(fieldKey)
    if (!itemEl) return
    const contentEl = itemEl.querySelector('.el-form-item__content')
    contentEl?.querySelectorAll('.engine-error').forEach(el => el.remove())
    if (!contentEl?.querySelector('.el-form-item__error:not(.engine-error)')) {
      itemEl.classList.remove('is-error')
    }
  }

  /**
   * Clear all previously injected engine validation errors from the form.
   */
  function clearEngineErrors() {
    document.querySelectorAll('.engine-error').forEach(el => el.remove())
    // Note: we don't remove is-error class here because Element Plus may have its own errors
  }

  const validate = async (): Promise<boolean> => {
    if (!deps.formRef.value) return false

    // Clear previously injected engine errors before re-validating
    clearEngineErrors()

    let elPlusValid = true
    try {
      await deps.formRef.value.validate()
    } catch {
      elPlusValid = false
    }

    // Engine validation (cross-field + custom rules)
    const config = deps.config()
    if (config) {
      const engineResult = deps.engine.validateAll(deps.formData.value)
      const crossResult = deps.engine.validateCrossField(deps.formData.value)

      if (!engineResult.valid || !crossResult.valid) {
        // Inject engine field errors into Element Plus form-item error state via DOM
        for (const [fieldKey, errors] of engineResult.fieldErrors) {
          if (errors.length > 0) {
            injectFieldError(fieldKey, errors[0])
          }
        }
        // Inject cross-field errors into targetField form-items
        for (const err of crossResult.errors) {
          injectFieldError(err.targetField, err.message)
        }
        // Scroll to first error field
        nextTick(() => {
          const firstError = document.querySelector('.el-form-item.is-error')
          firstError?.scrollIntoView({ behavior: 'smooth', block: 'center' })
        })
        return false
      }
    }

    const scriptErrorKeys = Object.keys(deps.scriptFieldErrors.value)
    if (scriptErrorKeys.length > 0) {
      nextTick(() => {
        const root = deps.formRef.value?.$el as HTMLElement | undefined
        const firstKey = scriptErrorKeys[0]
        const firstError = firstKey && root
          ? root.querySelector(`.el-form-item[data-field-key="${CSS.escape(firstKey)}"]`)
          : document.querySelector('.el-form-item.is-error')
        firstError?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      return false
    }

    if (!elPlusValid) {
      // Scroll to first Element Plus error
      nextTick(() => {
        const firstError = document.querySelector('.el-form-item.is-error')
        if (firstError) {
          firstError.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      })
    }

    return elPlusValid
  }

  return {
    findFormItemEl,
    injectFieldError,
    clearInjectedFieldError,
    clearEngineErrors,
    validate,
  }
}
