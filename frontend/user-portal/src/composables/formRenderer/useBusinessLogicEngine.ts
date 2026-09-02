import { ref } from 'vue'
import { BusinessLogicEngine } from '../../components/businessLogicEngine'
import type { FormBusinessLogicConfig } from '../../components/formRendererHelpers'

// ---------------------------------------------------------------------------
// Task 7.2: BusinessLogicEngine integration
// ---------------------------------------------------------------------------

/** Writable box for formData — satisfied by a Vue Ref or a getter/setter wrapper (TDZ break). */
type FormDataBox = { value: Record<string, any> }

interface EngineDeps {
  config: () => FormBusinessLogicConfig | undefined
  formData: FormDataBox
}

export function useBusinessLogicEngine(deps: EngineDeps) {
  const engine = new BusinessLogicEngine()
  const engineVisibility = ref(new Map<string, boolean>())
  const engineOptions = ref(new Map<string, Array<{ label: string; value: any }>>())
  const engineFieldStates = ref(new Map<string, { disabled?: boolean; required?: boolean }>())
  const engineCalculatedValues = ref(new Map<string, number>())

  function initEngine() {
    const config = deps.config()
    if (!config) return
    engine.init(config)
    const data = deps.formData.value
    for (const [key, value] of Object.entries(data)) {
      if (value == null || value === '') continue
      applyEngineResult(engine.onFieldChange(key, value, data))
    }
  }

  function applyEngineResult(result: {
    visibilityChanges: Map<string, boolean>
    calculatedValues: Map<string, number>
    optionChanges: Map<string, Array<{ label: string; value: any }>>
    stateChanges: Map<string, { disabled?: boolean; required?: boolean }>
  }) {
    // Merge visibility changes
    for (const [k, v] of result.visibilityChanges) {
      engineVisibility.value.set(k, v)
    }
    // Merge calculated values and update formData
    for (const [k, v] of result.calculatedValues) {
      engineCalculatedValues.value.set(k, v)
      deps.formData.value[k] = v
    }
    // Merge option changes
    for (const [k, v] of result.optionChanges) {
      engineOptions.value.set(k, v)
    }
    // Merge state changes
    for (const [k, v] of result.stateChanges) {
      engineFieldStates.value.set(k, v)
    }
    // Trigger reactivity
    engineVisibility.value = new Map(engineVisibility.value)
    engineOptions.value = new Map(engineOptions.value)
    engineFieldStates.value = new Map(engineFieldStates.value)
    engineCalculatedValues.value = new Map(engineCalculatedValues.value)
  }

  return {
    engine,
    engineVisibility,
    engineOptions,
    engineFieldStates,
    engineCalculatedValues,
    initEngine,
    applyEngineResult,
  }
}
