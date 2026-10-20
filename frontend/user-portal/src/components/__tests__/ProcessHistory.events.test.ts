import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ProcessHistory from '../ProcessHistory.vue'
import type { HistoryRecord } from '@/types/historyRecord'

const xml = `<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="p">
  <startEvent id="rootStart"/><subProcess id="parallel">
    <startEvent id="innerStart"/><userTask id="work"/><endEvent id="innerEnd"/>
  </subProcess><endEvent id="rootEnd"/>
</process></definitions>`
const row = (nodeId: string, activityType: string, nodeName = nodeId): HistoryRecord => ({
  id: nodeId, nodeId, nodeName, activityType, status: 'completed', action: 'approve',
  assigneeName: 'Demo operator', createdTime: '2026-10-20T14:37:22',
})
const records = [row('rootStart', 'startEvent'), row('innerStart', 'startEvent', 'Unknown Node'),
  row('work', 'userTask', 'Handle row'), row('innerEnd', 'endEvent', 'Unknown Node'), row('rootEnd', 'endEvent')]
function render(items = records, bpmnXml = xml, collapsible = false) {
  return mount(ProcessHistory, {
    props: { records: items, bpmnXml, collapsible, defaultVisibleCount: 1 },
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages: { en: {} }, missingWarn: false, fallbackWarn: false })],
      stubs: Object.fromEntries(['ElTimeline', 'ElTimelineItem', 'ElCard', 'ElTag', 'ElAvatar', 'ElLink', 'ElIcon', 'ElButton', 'ElEmpty']
        .map(name => [name, { template: '<div><slot /></div>' }])),
    },
  })
}
describe('business flow history event presentation', () => {
  it('hides nested boundary events, retains root events and real tasks without changing source history', () => {
    const wrapper = render()
    expect(wrapper.findAll('.node-name').map(el => el.text())).toEqual(['rootStart', 'Handle row', 'rootEnd'])
    expect(records).toHaveLength(5)
    expect(records[3].action).toBe('approve')
  })
  it('does not attribute approvals or operators to automatic boundary events', () => {
    const wrapper = render()
    expect(wrapper.findAll('.action-text')).toHaveLength(1)
    expect(wrapper.findAll('.avatar')).toHaveLength(1)
  })
  it('calculates the expansion count after filtering', async () => {
    const wrapper = render(records, xml, true)
    expect(wrapper.findAll('.node-name').map(el => el.text())).toEqual(['rootEnd'])
    await wrapper.find('.toggle-history > div').trigger('click')
    expect(wrapper.findAll('.node-name')).toHaveLength(3)
  })
  it('does not hide an unnamed human task or guess scope when BPMN is unavailable', () => {
    const wrapper = render([row('unknownTask', 'userTask', 'Unknown Node'), row('innerEnd', 'endEvent')], '')
    expect(wrapper.findAll('.node-name')).toHaveLength(2)
    expect(wrapper.findAll('.action-text')).toHaveLength(1)
  })
  it('uses BPMN scope rather than node names and supports namespace prefixes and transactions', () => {
    const prefixed = `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL"><b:process id="p">
      <b:startEvent id="outside"/><b:transaction id="tx"><b:endEvent id="inside"/></b:transaction>
    </b:process></b:definitions>`
    const wrapper = render([
      row('outside', 'b:startEvent', 'Unknown Node'), row('inside', 'b:endEvent', 'Named automatic event'),
      row('human', 'userTask', 'inside'),
    ], prefixed)
    expect(wrapper.findAll('.node-name').map(el => el.text())).toEqual(['Unknown Node', 'inside'])
    expect(wrapper.findAll('.action-text')).toHaveLength(1)
  })
})
