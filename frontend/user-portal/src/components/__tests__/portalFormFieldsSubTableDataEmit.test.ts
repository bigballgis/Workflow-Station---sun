import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'

/**
 * `PortalFormFields` 必须在子表行集变化时 emit `update:sub-table-data`。
 *
 * <p><b>这是「inline 表格里删不掉行」的根因。</b>此前该组件只把行集写进**宿主行的
 * `__subTables__`**，从不通知外层，于是 `binding.data` 永远不同步；而保存时 payload 读的是
 * `binding.data` —— 删除因此从未进入请求体。真机实测（task 9c46d613）：删除写进了
 * Participants row 0，`syncMainSubTableRows` 触发次数恒为 **0**，提交体里 `kk` 始终还在。
 *
 * <p>曾经的补偿方案是比较「顶层切片 vs 嵌套副本」哪边行数少来猜权威。那个启发式**方向是反的**：
 * 删除时少的一方新，**新增时多的一方才新** —— 照此规则新增的行会被当成已删除而静默丢弃。
 * 补上这个 emit 后两份数据由同一次事件同时更新，不再需要任何权威判定。
 */
describe('PortalFormFields emits sub-table row changes upward', () => {
  const binding = {
    bindingId: 50547,
    tableName: 'people',
    physicalTableName: 'people',
    tableId: 50333,
    columns: [{ field: 'age', label: 'AGE' }],
    data: [] as unknown[],
    primaryKeyFields: ['idqcxma'],
  }

  function mountWith(model: Record<string, unknown>) {
    return mount(PortalFormFields, {
      props: {
        fields: [
          { key: 'people', label: 'People', type: 'subTable', _bindingId: 50547 } as never,
        ],
        model,
        editable: true,
        subTableBindings: [binding] as never,
        linkedSubTableBindings: [binding] as never,
      },
      global: { stubs: { SubTableField: true, FieldRenderer: true, SubTableInlineForm: true } },
    })
  }

  it('declares update:sub-table-data in its emit contract', () => {
    const wrapper = mountWith({})
    // 组件必须声明这个 emit —— 缺了它，宿主无从得知行集变化
    expect(wrapper.vm.$options.emits ?? []).toContain('update:sub-table-data')
    wrapper.unmount()
  })

  it('a DELETE (fewer rows) reaches the host', async () => {
    const wrapper = mountWith({})
    const rowsAfterDelete = [{ idqcxma: 'ee396ddb', age: 'u' }]
    ;(wrapper.vm as never as { onNestedSubTableRowsUpdate: (f: unknown, r: unknown[]) => void })
      .onNestedSubTableRowsUpdate?.({ _bindingId: 50547 }, rowsAfterDelete)
    await wrapper.vm.$nextTick()

    const emitted = wrapper.emitted('update:sub-table-data')
    expect(emitted, 'delete must be emitted upward').toBeTruthy()
    expect(emitted![0][1]).toEqual(rowsAfterDelete)
    wrapper.unmount()
  })

  it('an ADD (more rows) reaches the host too — the old "fewest rows wins" rule dropped these', async () => {
    const wrapper = mountWith({})
    const rowsAfterAdd = [
      { idqcxma: 'ee396ddb', age: 'u' },
      { idqcxma: 'edaefde2', age: 'kk' },
      { idqcxma: 'NEW', age: 'zz' },
    ]
    ;(wrapper.vm as never as { onNestedSubTableRowsUpdate: (f: unknown, r: unknown[]) => void })
      .onNestedSubTableRowsUpdate?.({ _bindingId: 50547 }, rowsAfterAdd)
    await wrapper.vm.$nextTick()

    const emitted = wrapper.emitted('update:sub-table-data')
    expect(emitted, 'add must be emitted upward').toBeTruthy()
    expect((emitted![0][1] as unknown[]).map(r => (r as Record<string, unknown>).idqcxma))
      .toContain('NEW')
    wrapper.unmount()
  })
})
