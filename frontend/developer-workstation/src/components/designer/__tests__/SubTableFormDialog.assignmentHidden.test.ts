import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { h } from 'vue'
import SubTableFormDialog from '../SubTableFormDialog.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (k: string) => k }) }
})

const CONFIG = {
  allowUser: true, allowRole: true,
  assigneeField: 'assignee', roleField: 'role_code', buField: 'bu_code',
}

function assignmentRule(hidden: boolean) {
  return [
    { field: 'name', type: 'input', title: 'Name' },
    {
      type: 'miAssignment',
      title: 'Assignment Mode',
      hidden,
      children: [
        { field: 'assignee', type: 'lookup', title: 'Assignee' },
        { field: 'bu_code', type: 'select', title: 'Business Unit' },
        { field: 'role_code', type: 'select', title: 'Role' },
      ],
    },
  ]
}

// form-create is registered globally (main.ts plugin), not imported locally —
// stub it here and capture whatever `rule` prop it receives.
const FormCreateStub = {
  props: ['rule'],
  render() {
    return h('div', { class: 'fc-stub', 'data-rule': JSON.stringify(this.rule) })
  },
}

function mountDialog(hidden: boolean) {
  return mount(SubTableFormDialog, {
    props: {
      visible: true,
      mode: 'add' as const,
      rule: assignmentRule(hidden),
      assignmentConfig: CONFIG,
    },
    global: {
      stubs: {
        SubTableNestedModalShell: { template: '<div><slot /><slot name="footer" /></div>' },
        'form-create': FormCreateStub,
        'el-icon': true,
      },
    },
  })
}

function stubbedRule(wrapper: ReturnType<typeof mountDialog>): any[] {
  const raw = wrapper.find('.fc-stub').attributes('data-rule')
  return raw ? JSON.parse(raw) : []
}

describe('SubTableFormDialog — Assignment Mode respects the designer Hide toggle', () => {
  it('renders the miAssignment block and its owned fields when not hidden', async () => {
    const wrapper = mountDialog(false)
    await flushPromises()
    const rule = stubbedRule(wrapper)
    expect(rule.some((r) => r.type === 'miAssignment')).toBe(true)
    expect(rule.some((r) => r.type === 'miAssignment' && r.children?.some((c: any) => c.field === 'assignee'))).toBe(true)
  })

  it('drops the miAssignment block entirely when hidden, without leaking its fields loose', async () => {
    const wrapper = mountDialog(true)
    await flushPromises()
    const rule = stubbedRule(wrapper)
    expect(rule.some((r) => r.type === 'miAssignment')).toBe(false)
    expect(rule.some((r) => r.field === 'assignee')).toBe(false)
    expect(rule.some((r) => r.field === 'bu_code')).toBe(false)
    expect(rule.some((r) => r.field === 'role_code')).toBe(false)
    // Unrelated fields still render.
    expect(rule.some((r) => r.field === 'name')).toBe(true)
  })
})
