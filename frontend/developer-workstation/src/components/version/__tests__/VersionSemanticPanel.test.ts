import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import VersionSemanticPanel from '../VersionSemanticPanel.vue'
import type { VersionCompareModule } from '@/types/versionCompare'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key, te: (key: string) => key.endsWith('.ruleOrder') })
}))

const stubs = ['el-alert', 'el-empty', 'el-tag']

function module(overrides: Partial<VersionCompareModule> = {}): VersionCompareModule {
  return {
    key: 'TABLES', status: 'COMPARED',
    counts: { added: 0, modified: 1, removed: 0 },
    truncated: false, changes: [],
    semantic: {
      status: 'COMPARED', scope: 'FULL',
      counts: { added: 0, modified: 2, removed: 0 },
      truncated: false,
      items: [
        { objectType: 'FIELD', objectKey: 'field/amount', label: 'Amount',
          type: 'MODIFIED', scope: 'DESIGN',
          fields: [{ field: 'dataType', oldValue: 'VARCHAR', newValue: 'DECIMAL' }] },
        { objectType: 'FIELD', objectKey: 'field/status', label: 'Status',
          type: 'MODIFIED', scope: 'DESIGN',
          fields: [{ field: 'nullable', oldValue: 'true', newValue: 'false' }] }
      ]
    },
    ...overrides
  }
}

describe('VersionSemanticPanel', () => {
  it('explains legacy Basic metadata gaps without inventing changes', () => {
    const wrapper = mount(VersionSemanticPanel, { props: { module: module({ key: 'BASIC',
      semantic: { ...module().semantic!, scope: 'PARTIAL', items: [] } }) }, global: { stubs } })
    expect(wrapper.find('el-alert-stub').attributes('title')).toBe('version.compareV2.semantic.basicHistoricalScope')
  })
  it('shows document excerpt scope and safely renders changed text as text', () => {
    const unsafe = '<script>not executable</script>'
    const semantic = { ...module().semantic!, items: [{ objectType: 'DOCUMENT', objectKey: 'document/DESIGN', label: 'DESIGN',
      type: 'MODIFIED' as const, scope: 'DESIGN' as const, fields: [{ field: 'content', oldValue: '…TAIL alpha', newValue: unsafe,
        textContext: { oldStart: 200, newStart: 200, oldLength: 5000, newLength: 5000, omittedChanges: true } }] }] }
    const wrapper = mount(VersionSemanticPanel, { props: { module: module({ key: 'DOCUMENTS', semantic }) }, global: { stubs } })
    expect(wrapper.text()).toContain('version.compareV2.semantic.documentExcerpt')
    expect(wrapper.text()).toContain('version.compareV2.semantic.documentOmittedChanges')
    expect(wrapper.text()).toContain(unsafe); expect(wrapper.find('script').exists()).toBe(false)
  })
  it('explains incomplete Form identities and orphan bindings without hiding changes', () => {
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module({ key: 'FORMS', semantic: { ...module().semantic!, scope: 'PARTIAL' } }) },
      global: { stubs }
    })
    expect(wrapper.find('el-alert-stub').attributes('title')).toBe('version.compareV2.semantic.formHistoricalScope')
    expect(wrapper.findAll('.compare-panel__object')).toHaveLength(2)
  })
  it('explains historical View identity limits without hiding any diff objects', () => {
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module({ key: 'VIEWS', semantic: { ...module().semantic!, scope: 'PARTIAL' } }) },
      global: { stubs }
    })
    expect(wrapper.find('el-alert-stub').exists()).toBe(true)
    expect(wrapper.find('el-alert-stub').attributes('title')).toBe('version.compareV2.semantic.viewHistoricalScope')
    expect(wrapper.findAll('.compare-panel__object')).toHaveLength(2)
  })
  it('translates array field names without treating the index as an i18n path', () => {
    const semantic = { ...module().semantic!, items: [{ ...module().semantic!.items[0],
      fields: [{ field: 'ruleOrder[0]', oldValue: 'r1', newValue: 'r2' }] }] }
    const wrapper = mount(VersionSemanticPanel, { props: { module: module({ semantic }) }, global: { stubs } })
    expect(wrapper.find('.compare-panel__detail').text()).toContain('version.compareV2.semantic.fields.ruleOrder[0]')
  })
  it('makes incomplete historical Decision coverage explicit', () => {
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module({ key: 'DECISIONS', semantic: {
        status: 'COMPARED', scope: 'PARTIAL', counts: { added: 0, modified: 0, removed: 0 },
        truncated: false, items: []
      } }) }, global: { stubs }
    })
    expect(wrapper.find('el-alert-stub').attributes('title')).toBe('version.compareV2.semantic.decisionHistoricalScope')
  })
  it('shows object-level fields and lets the reviewer select another object', async () => {
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module() }, global: { stubs }
    })
    expect(wrapper.find('.compare-panel__detail').text()).toContain('VARCHAR')
    await wrapper.findAll('.compare-panel__object')[1].trigger('click')
    expect(wrapper.find('.compare-panel__detail').text()).toContain('nullable')
    expect(wrapper.find('.compare-panel__detail').text()).not.toContain('VARCHAR')
  })

  it('uses safe text binding for design labels', () => {
    const unsafe = '<img src=x onerror=alert(1)>'
    const item = { ...module().semantic!.items[0], label: unsafe }
    const semantic = { ...module().semantic!, items: [item] }
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module({ semantic }) }, global: { stubs }
    })
    expect(wrapper.text()).toContain(unsafe)
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('keeps field-level fallback visible for malformed historical XML', () => {
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module({
        key: 'DECISIONS',
        semantic: { status: 'UNPARSEABLE', scope: 'FULL',
          counts: { added: 0, modified: 0, removed: 0 }, truncated: false, items: [] },
        changes: [{ path: 'DECISIONS/decision-a', type: 'MODIFIED',
          oldValue: 'XML (10 characters)', newValue: 'XML (11 characters)' }]
      }) }, global: { stubs }
    })
    expect(wrapper.find('.compare-panel__generic').exists()).toBe(true)
    expect(wrapper.text()).toContain('XML (10 characters)')
  })

  it('renders process extension fields with both saved values', () => {
    const semantic = { ...module().semantic!, items: [{
      objectType: 'BPMN_NODE', objectKey: 'process/review', label: 'Review',
      type: 'MODIFIED' as const, scope: 'DESIGN' as const,
      fields: [
        { field: 'config.assigneeType', oldValue: 'INITIATOR', newValue: 'MANUAL_ASSIGN' },
        { field: 'config.formName', oldValue: 'Old form', newValue: 'New form' },
        { field: 'config.actionIds', oldValue: '[10]', newValue: '[11]' }
      ]
    }] }
    const wrapper = mount(VersionSemanticPanel, {
      props: { module: module({ key: 'PROCESS', semantic }) }, global: { stubs }
    })
    const detail = wrapper.find('.compare-panel__detail').text()
    for (const value of ['INITIATOR', 'MANUAL_ASSIGN', 'Old form', 'New form', '[10]', '[11]']) {
      expect(detail).toContain(value)
    }
    expect(detail).not.toContain('[hidden configuration]')
  })
})
