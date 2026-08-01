/**
 * BPMN 往返保真回归。
 *
 * bpmn-js 的 `saveXML()` 就是 `moddle.toXML(definitions)`，而 moddle 只写回它声明过的
 * 类型：任何未声明的扩展元素在 import 时就被判为 unparsable content 丢弃，saveXML 后
 * 静默消失。设计器每 2s 自动保存一次，丢的东西会直接落库，因此这些形态必须钉死。
 *
 * bpmn-moddle 随 bpmn-js 一同安装，这里直接用它以避开 bpmn-js 在 jsdom 下无法渲染的问题。
 */
import { describe, expect, it } from 'vitest'
// @ts-ignore - 无 TS 类型声明
import BpmnModdle from 'bpmn-moddle'
import {
  bpmnIoCustomModdleDescriptor,
  workflowPlatformModdleDescriptor,
  flowableModdleDescriptor
} from '@/utils/customModdle'

function createModdle() {
  return new BpmnModdle({
    custom: workflowPlatformModdleDescriptor,
    custom_1: bpmnIoCustomModdleDescriptor,
    flowable: flowableModdleDescriptor
  })
}

async function roundTrip(xml: string): Promise<{ out: string; warnings: string[] }> {
  const moddle = createModdle()
  const { rootElement, warnings } = await moddle.fromXML(xml)
  const { xml: out } = await moddle.toXML(rootElement, { format: true })
  return {
    out,
    warnings: (warnings || []).map((w: { message: string }) => String(w.message).split('\n')[0])
  }
}

/** name=value 形式的扩展属性清单；实体写法差异（&quot; / &#34;）归一后再比。 */
function extensionProps(xml: string): string[] {
  return [...xml.matchAll(/<custom(?:_1)?:(?:property|values)\s+name="([^"]+)"\s+value="([^"]*)"/g)]
    .map((m) => `${m[1]}=${m[2].replace(/&#34;/g, '&quot;')}`)
    .sort()
}

const DEFINITIONS_OPEN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:custom="http://workflow.platform/schema/custom"
  xmlns:custom_1="http://custom.bpmn.io/schema"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">`

const USER_TASK_XML = `${DEFINITIONS_OPEN}
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:userTask id="Task_1" name="Approve">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="ROLE" />
          <custom:property name="roleId" value="42" />
          <custom:property name="formId" value="1001" />
          <custom:property name="formName" value="Approval Form" />
          <custom:property name="formReadOnly" value="false" />
          <custom:property name="actionIds" value="[7,8]" />
          <custom:property name="actionNames" value="[&quot;Approve&quot;,&quot;Reject&quot;]" />
        </custom:properties>
        <custom_1:properties>
          <custom_1:values name="formId" value="1001" />
          <custom_1:values name="formName" value="Approval Form" />
        </custom_1:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`

/** 历史 BPMN 里 custom 命名空间也用过 <custom:values> 承载子项。 */
const LEGACY_VALUES_XML = `${DEFINITIONS_OPEN}
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:userTask id="Task_1" name="Approve">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:values name="assigneeType" value="VIRTUAL_GROUP" />
          <custom:values name="assigneeValue" value="CREDIT_OFFICERS" />
        </custom:properties>
      </bpmn:extensionElements>
    </bpmn:userTask>
  </bpmn:process>
</bpmn:definitions>`

/** 属性写法：设计器 SubProcessProperties 与 init-scripts 16-* 用的形态。 */
const MI_ATTRIBUTE_XML = `${DEFINITIONS_OPEN}
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:subProcess id="MultiInstance_SubTable_9">
      <bpmn:multiInstanceLoopCharacteristics isSequential="true"
        flowable:collection="multiInstance_participants_collection"
        flowable:elementVariable="currentItem" />
    </bpmn:subProcess>
  </bpmn:process>
</bpmn:definitions>`

/** 元素写法：后端 BpmnXmlGenerator#generateMultiInstanceSubProcess 生成的形态。 */
const MI_ELEMENT_XML = `${DEFINITIONS_OPEN}
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:subProcess id="MultiInstance_SubTable_9">
      <bpmn:multiInstanceLoopCharacteristics isSequential="true">
        <bpmn:extensionElements>
          <flowable:collection>multiInstance_participants_collection</flowable:collection>
          <flowable:elementVariable>currentItem</flowable:elementVariable>
        </bpmn:extensionElements>
      </bpmn:multiInstanceLoopCharacteristics>
    </bpmn:subProcess>
  </bpmn:process>
</bpmn:definitions>`

describe('customModdle — BPMN 往返不丢扩展', () => {
  it('保留 assigneeType / 表单绑定 / actionIds / actionNames', async () => {
    const { out, warnings } = await roundTrip(USER_TASK_XML)

    expect(warnings).toEqual([])
    expect(extensionProps(out)).toEqual(extensionProps(USER_TASK_XML))
  })

  it('保留 custom 命名空间下的 <custom:values> 子项', async () => {
    const { out, warnings } = await roundTrip(LEGACY_VALUES_XML)

    expect(warnings).toEqual([])
    expect(extensionProps(out)).toEqual([
      'assigneeType=VIRTUAL_GROUP',
      'assigneeValue=CREDIT_OFFICERS'
    ])
  })

  it('保留多实例的 flowable 属性写法', async () => {
    const { out, warnings } = await roundTrip(MI_ATTRIBUTE_XML)

    expect(warnings).toEqual([])
    expect(out).toContain('flowable:collection="multiInstance_participants_collection"')
    expect(out).toContain('flowable:elementVariable="currentItem"')
  })

  it('保留多实例的 flowable 元素写法（含 xmlns:flowable 声明）', async () => {
    const { out, warnings } = await roundTrip(MI_ELEMENT_XML)

    expect(warnings).toEqual([])
    expect(out).toContain(
      '<flowable:collection>multiInstance_participants_collection</flowable:collection>'
    )
    expect(out).toContain('<flowable:elementVariable>currentItem</flowable:elementVariable>')
    expect(out).toContain('xmlns:flowable="http://flowable.org/bpmn"')
  })
})
