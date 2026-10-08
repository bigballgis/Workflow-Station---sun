import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'

const getFunctionUnitContent = vi.fn()

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}))
vi.mock('@/api/process', () => ({
  processApi: { getFunctionUnitContent: (...args: unknown[]) => getFunctionUnitContent(...args) },
}))
vi.mock('@/api/processForm', () => ({
  submitActionFormPopup: vi.fn(),
}))
vi.mock('@platform-shared/upload/uploadSubmitGate', () => ({
  warnIfUploadsBlocking: () => true,
}))

import { createCustomActionFormPopup } from '../customActionFormPopup'

describe('customActionFormPopup catalog pin', () => {
  beforeEach(() => {
    getFunctionUnitContent.mockReset()
  })

  function popup(task: Record<string, unknown>) {
    return createCustomActionFormPopup({
      t: (key: string) => key,
      taskInfo: ref(task),
      submitting: ref(false),
      loadTaskDetail: vi.fn(async () => {}),
      formPopupVisible: ref(false),
      formPopupTitle: ref(''),
      formPopupFields: ref([]),
      formPopupTabs: ref([]),
      formPopupData: ref({}),
      formPopupWidth: ref('800px'),
      formPopupReadOnlyMode: ref(false),
      currentFormPopupAction: ref(null),
      formPopupSubTableBindings: ref([]),
      formPopupLinkedSubTableBindings: ref(null),
      formPopupNativeSubTableBindingIds: ref([]),
      formPopupFormConfig: ref({}),
      formPopupViewContext: ref('assigneeTodo'),
      preparePopupContext: () => ({
        fields: [],
        tabs: [],
        subTableBindings: [],
        nativeSubTableBindingIds: [],
        formConfig: {},
      }),
    })
  }

  it('loads the pinned catalog instead of the process key when the host has no resolver', async () => {
    const pin = '116e5204-de07-41d9-9344-e74c8d697cda'
    getFunctionUnitContent.mockResolvedValue({
      forms: [{ sourceId: '42', name: 'Remark', data: { rule: [] } }],
    })
    const { openFormPopup } = popup({
      taskId: 'task-1',
      processInstanceId: 'pi-old',
      processDefinitionKey: 'p0-dual-binding-test',
      functionUnitCatalogId: pin,
    })

    await openFormPopup(
      { actionId: 1, actionName: 'Remark', actionType: 'FORM_POPUP' } as never,
      { formId: '42' },
    )

    expect(getFunctionUnitContent).toHaveBeenCalledWith(pin, 'task-1', 'pi-old')
  })
})
