import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SubTableAddDialog from '../SubTableAddDialog.vue'

/**
 * The sub-table Add/Edit dialog opens from more than one depth: straight from a task form,
 * and also from inside the Link Form modal — a hand-rolled overlay at z-index 5000.
 *
 * Regression: the dialog hard-coded `:z-index="2010"`, so opening a nested sub-table's "Add"
 * from inside the Link Form put it BEHIND that overlay. The dialog was rendered and focusable
 * in the DOM but completely covered, so the button looked like it did nothing.
 *
 * The dialog now measures the highest overlay on screen when it opens and goes above it.
 */

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

vi.mock('@/api/user', () => ({ userApi: { searchUsers: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/admin', () => ({
  getBusinessUnitTree: vi.fn().mockResolvedValue([]),
  getRolesByBusinessUnit: vi.fn().mockResolvedValue([]),
}))

const COLUMNS = [{ field: 'name', label: 'Name', type: 'text' }]

/**
 * The dialog calls `formRef.value?.clearValidate()` on every (re)open. The default stub for
 * `el-form` renders a bare element whose ref exposes no such method, so a reopen raised an
 * unhandled rejection that had nothing to do with what is under test. Stub the two Form
 * methods the dialog actually reaches for.
 */
const ElFormStub = {
  name: 'ElFormStub',
  template: '<form><slot /></form>',
  methods: {
    clearValidate() {},
    validate: () => Promise.resolve(true),
    resetFields() {},
  },
}

/**
 * The dialog's layer, read off the rendered DOM (script-setup bindings are not exposed on
 * the vm). Its own backdrop sits exactly one below, which is the invariant that matters.
 */
function renderedZ(): { dialog: number; backdrop: number } {
  const backdrop = document.querySelector<HTMLElement>('.sub-table-backdrop')
  const backdropZ = Number(backdrop?.style.zIndex ?? NaN)
  return { dialog: backdropZ + 1, backdrop: backdropZ }
}

function mountDialog() {
  return mount(SubTableAddDialog, {
    props: { visible: true, mode: 'add', columns: COLUMNS } as never,
    attachTo: document.body,
    global: { stubs: { teleport: true, 'el-form': ElFormStub } },
  })
}

/** Stand in for whatever is already stacked on screen when the dialog opens. */
function addOverlay(zIndex: number, className = 'link-form-modal-overlay'): HTMLElement {
  const el = document.createElement('div')
  el.className = className
  el.style.position = 'fixed'
  el.style.zIndex = String(zIndex)
  document.body.appendChild(el)
  return el
}

describe('SubTableAddDialog — stacking order', () => {
  afterEach(() => {
    document.querySelectorAll('.link-form-modal-overlay, .el-overlay').forEach(el => el.remove())
    document.documentElement.style.removeProperty('--sub-table-dialog-z')
  })

  it('keeps the historical base layer when nothing is stacked above the page', () => {
    const wrapper = mountDialog()
    expect(renderedZ().dialog).toBe(2010)
    wrapper.unmount()
  })

  it('rises above the Link Form overlay when opened from inside it', () => {
    addOverlay(5000)
    const wrapper = mountDialog()
    // Strictly above the overlay, leaving the slot below for its own backdrop.
    expect(renderedZ().dialog).toBeGreaterThan(5000)
    expect(renderedZ().backdrop).toBeGreaterThan(5000)
    wrapper.unmount()
  })

  /**
   * Pickers are teleported to body, so they get the dialog's layer through an injected rule
   * rather than the DOM. It must be PER INSTANCE: a nested sub-table's dialog opens while its
   * parent is still open, so one shared value would leave the parent's pickers resolving
   * against the nested dialog's (higher) layer after it closed.
   */
  it('injects a picker layer rule scoped to this dialog instance', () => {
    addOverlay(5000)
    const wrapper = mountDialog()

    const styleEl = document.querySelector<HTMLStyleElement>('style[data-sub-table-dialog-popper-layer]')
    expect(styleEl).not.toBeNull()
    const z = Number(styleEl!.textContent?.match(/z-index:\s*(\d+)/)?.[1])
    expect(z).toBeGreaterThan(renderedZ().dialog)

    wrapper.unmount()
    // The rule must not outlive the dialog that owns it.
    expect(document.querySelector('style[data-sub-table-dialog-popper-layer]')).toBeNull()
  })

  it('gives two live dialogs their own picker layers', async () => {
    const parent = mountDialog()
    const parentRule = document.querySelector<HTMLStyleElement>('style[data-sub-table-dialog-popper-layer]')?.textContent
    addOverlay(5000)
    const nested = mountDialog()

    const rules = [...document.querySelectorAll<HTMLStyleElement>('style[data-sub-table-dialog-popper-layer]')]
    expect(rules).toHaveLength(2)
    // The parent's own rule is untouched by the nested dialog opening above it.
    expect(rules.some(r => r.textContent === parentRule)).toBe(true)
    expect(new Set(rules.map(r => r.textContent)).size).toBe(2)

    nested.unmount(); parent.unmount()
  })

  it('recomputes on each open — the same instance can be reopened at another depth', async () => {
    const wrapper = mount(SubTableAddDialog, {
      props: { visible: false, mode: 'add', columns: COLUMNS } as never,
      attachTo: document.body,
      global: { stubs: { teleport: true, 'el-form': ElFormStub } },
    })

    await wrapper.setProps({ visible: true } as never)
    const plain = renderedZ().dialog

    await wrapper.setProps({ visible: false } as never)
    const overlay = addOverlay(5000)
    await wrapper.setProps({ visible: true } as never)

    expect(renderedZ().dialog).toBeGreaterThan(plain)
    expect(renderedZ().dialog).toBeGreaterThan(5000)
    overlay.remove()
    wrapper.unmount()
  })
})
