/**
 * 新增的子表行必须**立刻可见**：宿主行的嵌套 `__subTables__` 是派生缓存，只在保存 / 重新加载
 * 时更新，而 `binding.data` 是刚发生的编辑的第一现场。
 *
 * <p>回归背景：`PortalFormFields.resolveSubTableRows` 命中嵌套切片就直接 return，于是用户点 Add
 * 新增的行（已进 `binding.data`、还没回写嵌套）在**渲染层**被丢掉 —— 实测 `binding.data` 已有
 * 2 行、表格却只画 1 行，表现为「ATM Correspondence 加不进第二条」。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import PortalFormFields from '../PortalFormFields.vue'

const MY_TX = 'ATM-DC-PW-TRANS-000004'

/** ATM Correspondence：FK 指向 MI collection ATM_Transaction(391)，主键是 prefixedSequence。 */
const CORR_BINDING = {
  bindingId: 1133,
  tableId: 392,
  tableName: 'ATM Correspondence',
  designerTableName: 'atm_correspondence',
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'related_transaction_id',
  bindingMode: 'EDITABLE',
  primaryKeyFields: ['correspondence_id'],
  columns: [{ field: 'correspondence_id', label: 'ID' }],
  fieldDefinitions: [
    { fieldName: 'correspondence_id', isPrimaryKey: true },
    { fieldName: 'related_transaction_id', isForeignKey: true, refTableId: 391 },
  ],
}

/** MI collection binding —— 让分类上下文解析得出 collection tableId = 391。 */
const COLLECTION_BINDING = {
  bindingId: 1127,
  tableId: 391,
  tableName: 'ATM Transaction',
  designerTableName: 'ATM_Transaction',
  bindingLinkMode: 'miParticipantRow',
  primaryKeyFields: ['row_id'],
  columns: [{ field: 'row_id', label: 'Row' }],
  data: [],
  fieldDefinitions: [{ fieldName: 'row_id', isPrimaryKey: true }],
}

function mountFields(bindingData: unknown[], nested: unknown[]) {
  // 宿主行 = 当前参与者的 collection 行，嵌套缓存里只有旧行
  const hostRow: Record<string, unknown> = {
    row_id: MY_TX,
    __subTables__: { 'dw:atm_correspondence': nested },
  }
  const binding = { ...CORR_BINDING, data: bindingData }
  return mount(
    defineComponent({
      components: { PortalFormFields },
      setup() {
        return () =>
          h(PortalFormFields, {
            fields: [{ key: 'corr', type: 'subTable', _bindingId: 1133 } as never],
            model: hostRow,
            parentRow: hostRow,
            editable: true,
            subTableBindings: [binding, COLLECTION_BINDING] as never,
            linkedSubTableBindings: [binding, COLLECTION_BINDING] as never,
            hostTableId: 391,
          })
      },
    }),
    {
      global: {
        stubs: {
          SubTableField: {
            name: 'SubTableField',
            props: ['modelValue'],
            template: '<div class="stub-sub-table" />',
          },
        },
      },
    },
  )
}

function renderedRows(wrapper: ReturnType<typeof mountFields>): any[] {
  const stub = wrapper.findComponent({ name: 'SubTableField' })
  return (stub.props('modelValue') as any[]) ?? []
}

describe('新增行必须立刻出现在表格里', () => {
  it('binding.data 比嵌套缓存多一行时，取并集（新增行可见）', () => {
    const oldRow = { correspondence_id: 'Corr-000004', related_transaction_id: MY_TX }
    const newRow = { correspondence_id: 'Corr-000021', related_transaction_id: MY_TX }
    const wrapper = mountFields([oldRow, newRow], [oldRow])

    const ids = renderedRows(wrapper).map(r => r?.correspondence_id).sort()
    expect(ids).toEqual(['Corr-000004', 'Corr-000021'])
  })

  it('别的参与者的行不会因为走了兜底而漏进来', () => {
    const mine = { correspondence_id: 'Corr-000004', related_transaction_id: MY_TX }
    const peer = { correspondence_id: 'Corr-000003', related_transaction_id: 'ATM-DC-PW-TRANS-000003' }
    const fresh = { correspondence_id: 'Corr-000021', related_transaction_id: MY_TX }
    const wrapper = mountFields([mine, peer, fresh], [mine])

    const ids = renderedRows(wrapper).map(r => r?.correspondence_id).sort()
    expect(ids).toEqual(['Corr-000004', 'Corr-000021'])
    expect(ids).not.toContain('Corr-000003')
  })
})
