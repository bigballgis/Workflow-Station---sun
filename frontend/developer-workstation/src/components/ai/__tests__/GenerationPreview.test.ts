import { describe, it, expect } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import GenerationPreview from '@/components/ai/GenerationPreview.vue'
import type { GenerationPreviewData, AiGeneratedData } from '@/types/aiGeneration'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': {
      ai: {
        preview: {
          title: '生成预览',
          tables: '数据表',
          forms: '表单',
          actions: '动作',
          process: '流程',
          actionTypes: '动作类型：',
          iconPreview: '图标预览：',
          apply: '确认应用',
          regenerate: '重新生成',
          tablesSummary: '{count} 个表，共 {fields} 个字段',
          formsSummary: '{count} 个表单',
          actionsSummary: '{count} 个动作',
          processSummary: '{nodes} 个节点，{gateways} 个网关'
        }
      }
    }
  }
})

// Stub Element Plus components
const globalStubs = {
  'el-card': { template: '<div class="el-card"><slot /><slot name="header" /></div>' },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border', 'size'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['size', 'type'] },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled" @click="!disabled && $emit(\'click\')"><slot /></button>',
    props: ['type', 'disabled'],
    emits: ['click']
  }
}

describe('GenerationPreview', () => {
  const defaultPreviewData: GenerationPreviewData = {
    tableCount: 3,
    totalFieldCount: 15,
    formCount: 2,
    actionCount: 4,
    actionTypes: ['APPROVE', 'REJECT', 'SAVE'],
    processNodeCount: 5,
    processGatewayCount: 2,
    decisionCount: 0,
    tableRelationCount: 0,
    iconSvg: undefined
  }

  const defaultGeneratedData: AiGeneratedData = {
    tableDefinitions: [],
    formDefinitions: [],
    actionDefinitions: []
  }

  function mountComponent(previewData = defaultPreviewData, generatedData = defaultGeneratedData) {
    return shallowMount(GenerationPreview, {
      props: { previewData, generatedData },
      global: { stubs: globalStubs, plugins: [i18n] }
    })
  }

  it('should render the preview title', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('生成预览')
  })

  it('should display table count and field count', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('15')
  })

  it('should display form count', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('2')
  })

  it('should display action count', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('4')
  })

  it('should display process node and gateway counts', () => {
    const wrapper = mountComponent()
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('2')
  })

  it('should render action type tags', () => {
    const wrapper = mountComponent()
    const tags = wrapper.findAll('.el-tag')
    expect(tags.length).toBe(3)
    expect(tags[0].text()).toBe('APPROVE')
    expect(tags[1].text()).toBe('REJECT')
    expect(tags[2].text()).toBe('SAVE')
  })

  it('should not render action type tags when empty', () => {
    const previewData = { ...defaultPreviewData, actionTypes: [] }
    const wrapper = mountComponent(previewData)
    expect(wrapper.findAll('.el-tag').length).toBe(0)
  })

  it('should render icon SVG preview when provided', () => {
    const previewData = {
      ...defaultPreviewData,
      iconSvg: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/></svg>'
    }
    const wrapper = mountComponent(previewData)
    expect(wrapper.html()).toContain('<svg')
  })

  it('should not render icon preview when iconSvg is undefined', () => {
    const wrapper = mountComponent()
    expect(wrapper.find('.generation-preview__icon').exists()).toBe(false)
  })

  it('should emit apply when confirm button is clicked', async () => {
    const wrapper = shallowMount(GenerationPreview, {
      props: { previewData: defaultPreviewData, generatedData: defaultGeneratedData, isGenerationComplete: true },
      global: { stubs: globalStubs, plugins: [i18n] }
    })
    const buttons = wrapper.findAll('.el-button')
    // First button is "确认应用"
    const applyBtn = buttons.find(b => b.text().includes('确认应用'))
    expect(applyBtn).toBeDefined()
    await applyBtn!.trigger('click')
    expect(wrapper.emitted('apply')).toBeTruthy()
  })

  it('should emit regenerate when regenerate button is clicked', async () => {
    const wrapper = mountComponent()
    const buttons = wrapper.findAll('.el-button')
    const regenBtn = buttons.find(b => b.text().includes('重新生成'))
    expect(regenBtn).toBeDefined()
    await regenBtn!.trigger('click')
    expect(wrapper.emitted('regenerate')).toBeTruthy()
  })
})
