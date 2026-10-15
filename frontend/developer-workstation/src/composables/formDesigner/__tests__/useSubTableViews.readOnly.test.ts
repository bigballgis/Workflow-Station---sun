import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { subTableViewApi } from '@/api/subTableView'
import type { FormDefinition } from '@/api/functionUnit'
import { useSubTableViews } from '../useSubTableViews'

vi.mock('@/api/subTableView', () => ({ subTableViewApi: {
  getViewConfig: vi.fn().mockResolvedValue({ data: { id: null, bindingId: 19, tableId: 3, viewFields: [] } }),
  getOrCreateView: vi.fn().mockResolvedValue({ data: { id: 1, bindingId: 19, tableId: 3, viewFields: [] } }),
  getAvailableFields: vi.fn().mockResolvedValue({ data: [{ fieldName: 'amount', dataType: 'DECIMAL' }] }),
} }))
vi.mock('@/utils/formCreateDefaultEvents', () => ({
  prepareFormCreateRulesForPersist: vi.fn(), serializeFormCreateOptionsForPersist: vi.fn(),
}))
vi.mock('@/utils/formDesigner', () => ({ withSubTableBindingIdInProps: vi.fn() }))

describe('sub-table view inspection', () => {
  it('reads unset views and available fields without get-or-create or design writes', async () => {
    const updateForm = vi.fn()
    const selectedForm = ref<FormDefinition>({
      id: 11, formName: 'Read-only form', formType: 'PROCESS', configJson: {},
    })
    const views = useSubTableViews({
      functionUnitId: 7, store: { tables: [], updateForm }, selectedForm,
      designerSubBindings: computed(() => []), subDesignerRefs: ref([]),
      linkFormComponents: ref([]), defaultFormOption: computed(() => ({})), t: key => key,
    })
    await views.loadSubTableViewConfig(19)
    expect(subTableViewApi.getViewConfig).toHaveBeenCalledWith(11, 19)
    expect(subTableViewApi.getOrCreateView).not.toHaveBeenCalled()
    expect(views.subTableViewState.value[19].allFields).toHaveLength(1)
    expect(views.subTableViewState.value[19].viewFields).toEqual([])
    expect(updateForm).not.toHaveBeenCalled()
  })
})
