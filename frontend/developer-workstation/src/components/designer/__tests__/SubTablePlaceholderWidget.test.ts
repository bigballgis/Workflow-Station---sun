import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SubTablePlaceholderWidget from '../SubTablePlaceholderWidget.vue'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

const BINDINGS = [
  {
    id: 66,
    tableName: 'p0_dual_file',
    tableDisplayName: 'P0 Dual File',
    tableDescription: '',
    bindingType: 'SUB',
    foreignKeyField: 'case_id3',
  },
]

function mountWidget(props: Record<string, unknown>) {
  return mount(SubTablePlaceholderWidget, {
    props,
    global: {
      provide: { designerSubBindings: () => BINDINGS },
      stubs: {
        'el-icon': { template: '<i><slot /></i>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-button': { template: '<button><slot /></button>' },
        Grid: true,
        ArrowRight: true,
      },
    },
  })
}

describe('SubTablePlaceholderWidget', () => {
  it('shows the configured display title and keeps the binding identity visible', () => {
    const wrapper = mountWidget({ _bindingId: 66, _displayTitle: 'Case files' })

    expect(wrapper.find('.display-title').text()).toBe('Case files')
    expect(wrapper.find('.binding-name').text()).toContain('P0 Dual File')
    expect(wrapper.find('.binding-name').text()).toContain('case_id3')
  })

  it('falls back to the binding label when the display title is blank', () => {
    const wrapper = mountWidget({ _bindingId: 66, _displayTitle: '   ' })

    expect(wrapper.find('.display-title').exists()).toBe(false)
    expect(wrapper.find('.binding-name').text()).toContain('P0 Dual File')
  })
})
