import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import SubTableAddDialog from '../SubTableAddDialog.vue'

/**
 * ATM Transaction regression (prod): two designer-hidden date fields in a sub-form
 * rendered anyway on first open of the sub-table Add/Edit dialog.
 *
 * Asserted on the real rendered DOM, because the defect was a broken chain — the
 * column carried no Hide flag AND nothing seeded visibility on open — so a
 * composable-level test alone would not prove the field stays out of the form.
 *
 * The dialog initialises from a `visible` watcher (immediate: false), so every case
 * mounts closed and then opens, exactly like the real Add/Edit entry points.
 */

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

vi.mock('@/api/user', () => ({
  userApi: { searchUsers: vi.fn().mockResolvedValue([]) },
}))

vi.mock('@/api/admin', () => ({
  getBusinessUnitTree: vi.fn().mockResolvedValue([]),
  getRolesByBusinessUnit: vi.fn().mockResolvedValue([]),
}))

/**
 * Element Plus is intentionally NOT registered: its el-dialog teleports the body out of
 * the wrapper, which is what we need to assert on. Opening the dialog does call
 * formRef.clearValidate(), so el-form is stubbed with just that surface — otherwise the
 * unresolved element yields an unhandled rejection and a non-zero vitest exit code.
 */
const ElFormStub = defineComponent({
  name: 'ElForm',
  setup(_props, { slots, expose }) {
    expose({
      clearValidate: () => {},
      resetFields: () => {},
      validate: async () => true,
      validateField: async () => true,
    })
    return () => h('form', slots.default?.())
  },
})

const COLUMNS = [
  { field: 'amount', label: 'Amount', type: 'text' },
  { field: 'merchant_credit', label: 'Merchant Credit', type: 'date', hidden: true },
  { field: 'temporary_refund', label: 'Temporary Refund', type: 'date', hidden: true },
]

async function mountAndOpen(props: Record<string, unknown> = {}) {
  const wrapper = mount(SubTableAddDialog, {
    props: { visible: false, mode: 'add', columns: COLUMNS, ...props } as never,
    attachTo: document.body,
    global: { stubs: { teleport: true, 'el-form': ElFormStub } },
  })
  await wrapper.setProps({ visible: true } as never)
  await wrapper.vm.$nextTick()
  await wrapper.vm.$nextTick()
  return wrapper
}

describe('SubTableAddDialog — designer-hidden sub-form fields', () => {
  it('keeps designer-hidden fields out of the DOM on first open', async () => {
    const wrapper = await mountAndOpen()
    const html = wrapper.html()
    expect(html).toContain('Amount')
    expect(html).not.toContain('Merchant Credit')
    expect(html).not.toContain('Temporary Refund')
    wrapper.unmount()
  })

  it('hides them in edit mode too', async () => {
    const wrapper = await mountAndOpen({
      mode: 'edit',
      initialData: { amount: '100', merchant_credit: '2026-01-01', temporary_refund: '2026-02-02' },
    })
    const html = wrapper.html()
    expect(html).toContain('Amount')
    expect(html).not.toContain('Merchant Credit')
    expect(html).not.toContain('Temporary Refund')
    wrapper.unmount()
  })

  it('still hides them after close and reopen', async () => {
    const wrapper = await mountAndOpen()
    expect(wrapper.html()).not.toContain('Merchant Credit')
    await wrapper.setProps({ visible: false } as never)
    await wrapper.vm.$nextTick()
    await wrapper.setProps({ visible: true } as never)
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    expect(wrapper.html()).not.toContain('Merchant Credit')
    expect(wrapper.html()).toContain('Amount')
    wrapper.unmount()
  })

  it('lets a Form-tab onChange bootstrap hide a field that is not statically hidden', async () => {
    const wrapper = await mountAndOpen({
      columns: [
        { field: 'amount', label: 'Amount', type: 'text' },
        { field: 'note', label: 'Note', type: 'text' },
      ],
      formOptions: {
        onChange:
          '$FNX:\nif ($inject.field === "__bootstrap__") { $inject.api.hidden(true, "note") }',
      },
    })
    expect(wrapper.html()).toContain('Amount')
    expect(wrapper.html()).not.toContain('Note')
    wrapper.unmount()
  })

  it('lets onMounted reveal a statically hidden field', async () => {
    const wrapper = await mountAndOpen({
      formOptions: { onMounted: '$FNX:\napi.hidden(false, "merchant_credit")' },
    })
    expect(wrapper.html()).toContain('Merchant Credit')
    expect(wrapper.html()).not.toContain('Temporary Refund')
    wrapper.unmount()
  })

  it('leaves dialogs without any Hide flag untouched', async () => {
    const wrapper = await mountAndOpen({
      columns: [
        { field: 'amount', label: 'Amount', type: 'text' },
        { field: 'note', label: 'Note', type: 'text' },
      ],
    })
    expect(wrapper.html()).toContain('Amount')
    expect(wrapper.html()).toContain('Note')
    wrapper.unmount()
  })
})
