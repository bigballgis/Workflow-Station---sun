/**
 * 嵌套切片是这一父行的成员名单。`onNestedSubTableRowsUpdate` 在 Add/Delete 的同一拍写入
 * `__subTables__`，所以表格必须画嵌套那份，而不是和可能过期的 `binding.data` 并集。
 *
 * <p>曾经「池子比嵌套多一行就并回去」是为了让 Add 立刻可见；同一规则把 Delete 刚拿掉的行
 * 又填回来（task c8aecf08 / Corr-000048）。Add 的行现在已经在嵌套切片里。
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
  it('a row written into the nested slice is visible even if the pool is still one behind', () => {
    const oldRow = { correspondence_id: 'Corr-000004', related_transaction_id: MY_TX }
    const newRow = { correspondence_id: 'Corr-000021', related_transaction_id: MY_TX }
    const wrapper = mountFields([oldRow], [oldRow, newRow])

    const ids = renderedRows(wrapper).map(r => r?.correspondence_id).sort()
    expect(ids).toEqual(['Corr-000004', 'Corr-000021'])
  })

  /**
   * 实测（2026-09-10，task c8aecf08 / Corr-000048）：Link Form 不听
   * `update:sub-table-data`，所以 `binding.data` 仍是删之前的池子。嵌套切片已经没有
   * 048，但 `owned.length > scoped.length` 把池子并回来，点 Delete 行立刻复活。
   * 嵌套切片是这一父行的成员名单；池子多出来的行是过期副本，不能并回去。
   */
  it('nested slice missing a pool row is a DELETE, not an add to union back', () => {
    const kept = { correspondence_id: 'Corr-000047', related_transaction_id: MY_TX }
    const deleted = { correspondence_id: 'Corr-000048', related_transaction_id: MY_TX }
    const wrapper = mountFields([kept, deleted], [kept])

    const ids = renderedRows(wrapper).map(r => r?.correspondence_id)
    expect(ids).toEqual(['Corr-000047'])
    expect(ids).not.toContain('Corr-000048')
  })

  it('别的参与者的行不会因为走了兜底而漏进来', () => {
    const mine = { correspondence_id: 'Corr-000004', related_transaction_id: MY_TX }
    const peer = { correspondence_id: 'Corr-000003', related_transaction_id: 'ATM-DC-PW-TRANS-000003' }
    const fresh = { correspondence_id: 'Corr-000021', related_transaction_id: MY_TX }
    const wrapper = mountFields([mine, peer, fresh], [mine, fresh])

    const ids = renderedRows(wrapper).map(r => r?.correspondence_id).sort()
    expect(ids).toEqual(['Corr-000004', 'Corr-000021'])
    expect(ids).not.toContain('Corr-000003')
  })
})
