import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import InlineSubFormPlaceholderWidget from '../InlineSubFormPlaceholderWidget.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (k: string) => k }) }
})

/**
 * Mounts the real component (rather than testing extracted pure logic) because the wiring is
 * the risky part: the widget is `input: false`, so form-create forwards no rule.props — the
 * only channels that work are the `_bindingId` prop the drag rule copies in via loadRule, and
 * the `designerSubBindings` inject, which is a FUNCTION, not a ref.
 */

const BINDINGS = [
  { id: 66, tableName: 'Attachment', tableDisplayName: 'Attachment', tableDescription: 'files', bindingType: 'SUB' },
  { id: 77, tableName: 'Participants', tableDescription: '', bindingType: 'SUB' },
]

function mountWidget(props: Record<string, unknown>, bindings = BINDINGS) {
  return mount(InlineSubFormPlaceholderWidget, {
    props,
    global: {
      provide: { designerSubBindings: () => bindings },
      stubs: {
        'el-icon': { template: '<i><slot /></i>' },
        'el-tag': { template: '<span class="el-tag"><slot /></span>' },
        'el-button': { template: '<button><slot /></button>' },
        Document: true,
        ArrowRight: true,
      },
      mocks: { $t: (k: string) => k },
    },
  })
}

describe('InlineSubFormPlaceholderWidget', () => {
  it('is unconfigured when no binding is selected', () => {
    const w = mountWidget({ _bindingId: null })
    expect(w.classes()).toContain('is-unconfigured')
  })

  it('is valid and names the bound table when the binding resolves', () => {
    const w = mountWidget({ _bindingId: 66 })
    expect(w.classes()).toContain('is-valid')
    expect(w.text()).toContain('Attachment')
    // Description is appended in parentheses, matching SubTablePlaceholderWidget.
    expect(w.text()).toContain('files')
  })

  it('is stale when the saved binding no longer exists', () => {
    const w = mountWidget({ _bindingId: 4242 })
    expect(w.classes()).toContain('is-stale')
  })

  it('accepts the legacy bindingId prop name', () => {
    const w = mountWidget({ bindingId: 66 })
    expect(w.classes()).toContain('is-valid')
  })

  it('prefers an explicit subBindings prop over the injected list', () => {
    const w = mountWidget(
      { _bindingId: 5, subBindings: [{ id: 5, tableName: 'Local', tableDescription: '', bindingType: 'SUB' }] },
      BINDINGS,
    )
    expect(w.classes()).toContain('is-valid')
    expect(w.text()).toContain('Local')
  })

  it('falls back to tableName when no display name is set', () => {
    const w = mountWidget({ _bindingId: 77 })
    expect(w.text()).toContain('Participants')
  })

  it('exposes the resolved binding id for designer tooling', () => {
    const w = mountWidget({ _bindingId: 66 })
    expect(w.attributes('data-fc-designer-binding-id')).toBe('66')
  })

  it('is unconfigured (not crashing) when nothing is injected or passed', () => {
    const w = mount(InlineSubFormPlaceholderWidget, {
      props: {},
      global: {
        stubs: {
          'el-icon': { template: '<i><slot /></i>' },
          'el-tag': { template: '<span><slot /></span>' },
          'el-button': { template: '<button><slot /></button>' },
          Document: true,
          ArrowRight: true,
        },
        mocks: { $t: (k: string) => k },
      },
    })
    expect(w.classes()).toContain('is-unconfigured')
  })
})
