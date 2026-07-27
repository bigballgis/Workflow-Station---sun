import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

vi.mock('@/api/functionUnit', () => ({
  functionUnitApi: {
    repairFormConfig: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}))

import { functionUnitApi } from '@/api/functionUnit'
import { ElMessage } from 'element-plus'
import { useFormConfigPaste } from '../useFormConfigPaste'

describe('useFormConfigPaste', () => {
  it('repairs pasted JSON and rehydrates the selected form', async () => {
    const selectedForm = ref({
      id: 9,
      formName: 'Process',
      formType: 'PROCESS',
      configJson: { rule: [] },
    } as any)
    const handleSelectForm = vi.fn().mockResolvedValue(undefined)
    ;(functionUnitApi.repairFormConfig as any).mockResolvedValue({
      data: {
        configJson: { rule: [{ type: 'subTable', _bindingId: 50064 }], subForms: { '50064': { rule: [] } } },
        bindingIdMapping: { '302': '50064' },
        relationTableIdMapping: {},
        warnings: [],
        mixedSource: false,
        applied: false,
      },
    })

    const { pasteConfigText, handleConfirmPasteConfig, showPasteConfigDialog } = useFormConfigPaste({
      functionUnitId: 1,
      selectedForm,
      getMainDesignerRule: () => [],
      getKnownBindingIds: () => [50064],
      handleSelectForm,
      t: (k, p) => (p ? `${k}:${JSON.stringify(p)}` : k),
    })
    showPasteConfigDialog.value = true
    pasteConfigText.value = JSON.stringify({
      rule: [{ type: 'subTable', _bindingId: 302 }],
      subForms: { '302': { rule: [{ field: 'a', type: 'input' }] } },
    })

    await handleConfirmPasteConfig()

    expect(functionUnitApi.repairFormConfig).toHaveBeenCalledWith(1, 9, {
      configJson: expect.objectContaining({ rule: expect.any(Array) }),
      apply: false,
      createMissingTables: false,
    })
    expect(handleSelectForm).toHaveBeenCalledWith(expect.objectContaining({
      id: 9,
      configJson: expect.objectContaining({
        rule: [{ type: 'subTable', _bindingId: 50064 }],
      }),
    }))
    expect(showPasteConfigDialog.value).toBe(false)
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('repairs stale ids from the live designer rule (left JSON paste path)', async () => {
    const selectedForm = ref({
      id: 9,
      formName: 'Process',
      formType: 'PROCESS',
      configJson: { rule: [], subForms: {} },
      tableBindings: [{ id: 50064, bindingType: 'SUB' }],
    } as any)
    const handleSelectForm = vi.fn().mockResolvedValue(undefined)
    ;(functionUnitApi.repairFormConfig as any).mockResolvedValue({
      data: {
        configJson: { rule: [{ type: 'subTable', _bindingId: 50064 }] },
        bindingIdMapping: { '273': '50064' },
        relationTableIdMapping: {},
        warnings: [],
        mixedSource: false,
        applied: false,
      },
    })

    const { repairCurrentDesignerBindings } = useFormConfigPaste({
      functionUnitId: 1,
      selectedForm,
      getMainDesignerRule: () => [{ type: 'subTable', _bindingId: 273, props: {} }],
      getKnownBindingIds: () => [50064],
      handleSelectForm,
      t: (k) => k,
    })

    const ok = await repairCurrentDesignerBindings(true)
    expect(ok).toBe(true)
    expect(functionUnitApi.repairFormConfig).toHaveBeenCalledWith(1, 9, {
      configJson: expect.objectContaining({
        rule: [{ type: 'subTable', _bindingId: 273, props: {} }],
      }),
      apply: false,
      createMissingTables: false,
    })
    expect(handleSelectForm).toHaveBeenCalled()
  })

  it('willProvisionOnSave is true when rule has stale binding ids', () => {
    const selectedForm = ref({
      id: 9,
      formName: 'Process',
      formType: 'PROCESS',
      configJson: { rule: [] },
    } as any)
    const { willProvisionOnSave } = useFormConfigPaste({
      functionUnitId: 1,
      selectedForm,
      getMainDesignerRule: () => [],
      getKnownBindingIds: () => [50064],
      handleSelectForm: vi.fn(),
      t: (k) => k,
    })
    expect(willProvisionOnSave({
      rule: [{ type: 'subTable', _bindingId: 273 }],
    })).toBe(true)
    expect(willProvisionOnSave({
      rule: [{ type: 'subTable', _bindingId: 50064 }],
    })).toBe(false)
  })
})
