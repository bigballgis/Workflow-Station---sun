import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/functionUnit', () => ({ functionUnitApi: {} }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))

import { useProcessActions } from '../useProcessActions'

describe('useProcessActions — dirty state', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('coalesces rapid edit events while marking the canvas dirty immediately', async () => {
    vi.useFakeTimers()
    let canvasXml = '<definitions><process name="Original" /></definitions>'
    const modeler = {
      saveXML: vi.fn(() => Promise.resolve({ xml: canvasXml })),
      get: () => ({ getAll: () => [] }),
    }
    const actions = useProcessActions({
      functionUnitId: 1,
      getModeler: () => modeler,
      store: { process: null, saveProcess: vi.fn() },
      showImportDialog: ref(false),
      importXml: ref(''),
      t: (key: string) => key,
    })

    await actions.initializeSavedState()
    modeler.saveXML.mockClear()
    canvasXml = '<definitions><process name="Edited" /></definitions>'
    actions.markDirty()
    actions.markDirty()
    actions.markDirty()

    expect(actions.isDirty.value).toBe(true)
    expect(modeler.saveXML).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(250)
    expect(modeler.saveXML).toHaveBeenCalledTimes(1)
  })

  it('clears dirty state when the canvas is restored to its saved BPMN', async () => {
    let canvasXml = '<definitions><process name="Original" /></definitions>'
    const modeler = {
      saveXML: vi.fn(() => Promise.resolve({ xml: canvasXml })),
      get: () => ({ getAll: () => [] }),
    }
    const actions = useProcessActions({
      functionUnitId: 1,
      getModeler: () => modeler,
      store: {
        process: { bpmnXml: '<raw persisted xml is not the canonical modeler output />' },
        saveProcess: vi.fn(),
      },
      showImportDialog: ref(false),
      importXml: ref(''),
      t: (key: string) => key,
    })

    await actions.initializeSavedState()
    canvasXml = '<definitions><process name="Edited" /></definitions>'
    actions.markDirty()
    await actions.hasUnsavedChanges()
    expect(actions.isDirty.value).toBe(true)

    canvasXml = '<definitions><process name="Original" /></definitions>'
    actions.markDirty()

    expect(await actions.hasUnsavedChanges()).toBe(false)
    expect(actions.isDirty.value).toBe(false)
  })
})
