import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useActionList } from '@/composables/actionDesigner/useActionList'
import type { ActionDefinition } from '@/api/functionUnit'

function setup() {
  const selectedAction = ref<ActionDefinition | null>({
    id: 24,
    actionName: 'Approve',
    actionType: 'APPROVE',
    buttonColor: '#1F5C4A',
  } as ActionDefinition)
  const store = {
    fetchActions: vi.fn(async () => undefined),
    fetchForms: vi.fn(async () => undefined),
    fetchProcess: vi.fn(async () => undefined),
    createAction: vi.fn(async () => undefined),
    updateAction: vi.fn(async () => undefined),
    deleteAction: vi.fn(async () => undefined),
  }
  const list = useActionList({
    functionUnitId: 2,
    selectedAction,
    actionConfig: {},
    store,
    t: key => key,
    parseActionBindingsFromBpmn: vi.fn(),
  })
  return { list, selectedAction, store }
}

describe('useActionList Action Type colour sync', () => {
  it('starts a new Action with the selected default type HEX', () => {
    const { list } = setup()
    expect(list.createForm.actionType).toBe('APPROVE')
    expect(list.createForm.buttonColor).toBe('#67C23A')
  })

  it('overwrites Color with the corresponding HEX in both create and edit paths', () => {
    const { list, selectedAction } = setup()

    list.handleCreateActionTypeChange('REJECT')
    expect(list.createForm.actionType).toBe('REJECT')
    expect(list.createForm.buttonColor).toBe('#F56C6C')

    list.handleSelectedActionTypeChange('URGE')
    expect(selectedAction.value?.actionType).toBe('URGE')
    expect(selectedAction.value?.buttonColor).toBe('#E6A23C')
  })

  it('fills the default HEX when an existing Action already has the same type but an empty Color', () => {
    const { list, selectedAction } = setup()

    list.handleSelectAction({
      id: 25,
      actionName: 'Reject',
      actionType: 'REJECT',
      buttonColor: '',
      configJson: {},
    })

    expect(selectedAction.value?.actionType).toBe('REJECT')
    expect(selectedAction.value?.buttonColor).toBe('#F56C6C')
  })

  it('keeps an existing named or custom Color when the Action is opened', () => {
    const { list, selectedAction } = setup()

    list.handleSelectAction({
      id: 25,
      actionName: 'Reject',
      actionType: 'REJECT',
      buttonColor: 'danger',
      configJson: {},
    })
    expect(selectedAction.value?.buttonColor).toBe('danger')

    list.handleSelectAction({
      id: 25,
      actionName: 'Reject',
      actionType: 'REJECT',
      buttonColor: '#1F5C4A',
      configJson: {},
    })
    expect(selectedAction.value?.buttonColor).toBe('#1F5C4A')
  })

  it('includes the synced HEX in create and update payloads', async () => {
    const { list, store } = setup()

    list.handleCreateActionTypeChange('REJECT')
    await list.handleCreateAction()
    expect(store.createAction).toHaveBeenCalledWith(2, expect.objectContaining({
      actionType: 'REJECT',
      buttonColor: '#F56C6C',
    }))

    list.handleSelectedActionTypeChange('APPROVE')
    await list.handleSaveAction()
    expect(store.updateAction).toHaveBeenCalledWith(2, 24, expect.objectContaining({
      actionType: 'APPROVE',
      buttonColor: '#67C23A',
    }))
  })
})
