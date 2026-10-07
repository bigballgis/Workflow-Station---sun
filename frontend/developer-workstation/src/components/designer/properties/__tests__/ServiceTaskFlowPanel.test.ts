import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ServiceTaskFlowPanel from '../ServiceTaskFlowPanel.vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/api/automation', () => ({
  checkAutomationFlowKey: vi.fn(),
  fetchServiceTaskSession: vi.fn(),
  listAutomationFlows: vi.fn(),
}))

vi.mock('@/utils/bpmnExtensions', () => ({
  getExtensionProperties: () => ({}),
  setExtensionProperty: vi.fn(),
  removeExtensionProperty: vi.fn(),
}))

/**
 * The engine exposes every step — trigger included — as `{ output, error }`, so the
 * contract hint must teach `trigger.output.body…`; `trigger.body…` silently resolves to "".
 */
describe('ServiceTaskFlowPanel envelope contract hint', () => {
  it('shows the input expression with the .output segment', () => {
    const wrapper = mount(ServiceTaskFlowPanel, {
      props: {
        modeler: {} as BpmnModeler,
        element: { id: 'Task_1', businessObject: {} } as unknown as BpmnElement,
      },
      global: { stubs: ['el-form-item', 'el-select', 'el-option', 'el-alert'] },
    })

    const codes = wrapper.findAll('.contract-tip code').map((c) => c.text())
    expect(codes).toEqual([
      '{{trigger.output.body.variables.<name>}}',
      '{ "variables": { ... } }',
    ])
  })
})
