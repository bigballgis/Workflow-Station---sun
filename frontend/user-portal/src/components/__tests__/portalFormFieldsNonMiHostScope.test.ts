import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'

/**
 * 普通父子表（**非 MI**）的 Link Form / 嵌套表单，只能看到属于**当前父行**的子表行。
 *
 * <p><b>实测故障（2026-09-03，task a736e30f / ATM Transaction → ATM Correspondence）。</b>
 * `binding.data` 是**跨父行**的池子（两个 transaction 的 correspondence 都在里面）。
 * 归属过滤此前只认 MI collection 的主键（`collectionPrimaryKeyFields`），
 * 而 ATM Transaction 不是 MI collection —— 宿主标识永远解析不出，
 * 于是直接返回全量池子：
 * <ul>
 *   <li>TRANS-000007 的 Details 弹窗里出现了 TRANS-000008 的 `Corr-000041`；</li>
 *   <li>删 `Corr-000039` 被那一行盖回来，看起来"删不掉还多一条"；</li>
 *   <li>新增后弹窗里只剩新行，看起来"其它两条不见了"。</li>
 * </ul>
 *
 * <p>修法：宿主标识的候选主键除 MI collection 外，也读**宿主 binding 自己的
 * `primaryKeyFields`**（由 `hostTableId` 指认），再按行上的结构外键过滤。
 */
describe('non-MI host: nested rows are scoped to the current parent row', () => {
  const HOST_TABLE_ID = 900

  const correspondenceBinding = {
    bindingId: 1133,
    tableId: 901,
    tableName: 'ATM Correspondence',
    designerTableName: 'atm_correspondence',
    columns: [{ field: 'correspondence_id', label: 'ID' }],
    primaryKeyFields: ['correspondence_id'],
    // 结构外键：指回父表 ATM Transaction
    fieldDefinitions: [
      { fieldName: 'correspondence_id', isPrimaryKey: true },
      { fieldName: 'related_transaction_id', isForeignKey: true, refTableId: HOST_TABLE_ID },
    ],
    foreignKeyField: 'related_transaction_id',
    // 跨父行的池子：两个 transaction 的行都在
    data: [
      { correspondence_id: 'Corr-000039', related_transaction_id: 'TRANS-000007' },
      { correspondence_id: 'Corr-000041', related_transaction_id: 'TRANS-000008' },
    ],
  }

  /** 宿主 binding（ATM Transaction）——它的主键让宿主行能被识别出来。 */
  const transactionBinding = {
    bindingId: 1127,
    tableId: HOST_TABLE_ID,
    tableName: 'ATM Transaction',
    designerTableName: 'atm_transaction',
    columns: [{ field: 'transaction_number', label: 'No.' }],
    primaryKeyFields: ['transaction_number'],
    data: [],
  }

  function mountForParent(transactionNumber: string) {
    return mount(PortalFormFields, {
      props: {
        fields: [
          { key: 'corr', label: 'ATM Correspondence', type: 'subTable', _bindingId: 1133 } as never,
        ],
        // 宿主行：正在编辑的那个 transaction
        model: { transaction_number: transactionNumber },
        parentRow: { transaction_number: transactionNumber },
        editable: true,
        hostTableId: HOST_TABLE_ID,
        subTableBindings: [correspondenceBinding, transactionBinding] as never,
        linkedSubTableBindings: [correspondenceBinding, transactionBinding] as never,
      },
      global: { stubs: { SubTableField: true, FieldRenderer: true, SubTableInlineForm: true } },
    })
  }

  function rowsFor(wrapper: ReturnType<typeof mountForParent>): string[] {
    const rows = (wrapper.vm as never as {
      resolveSubTableRows: (b: unknown) => Array<Record<string, unknown>>
    }).resolveSubTableRows(correspondenceBinding as never)
    return rows.map(r => String(r.correspondence_id))
  }

  it('TRANS-000007 sees only its own correspondence', () => {
    const w = mountForParent('TRANS-000007')
    expect(rowsFor(w)).toEqual(['Corr-000039'])
    w.unmount()
  })

  it("TRANS-000008 does not see the other parent's row", () => {
    const w = mountForParent('TRANS-000008')
    expect(rowsFor(w)).toEqual(['Corr-000041'])
    w.unmount()
  })

  /**
   * 现场真实形状：`ATM_Transaction` 在设计器里**没有声明主键**，父行的身份是平台行标识
   * `row_id`（`ATM-DC-PW-TRANS-000007`），而子表的结构外键存的正是这个值。
   * 只按「宿主 binding 的 primaryKeyFields」找会落空 —— 必须能退到 `row_id`。
   */
  it('host identified by row_id when the designer declares no PK', () => {
    const noPkHost = { ...transactionBinding, primaryKeyFields: [] }
    const w = mount(PortalFormFields, {
      props: {
        fields: [{ key: 'corr', label: 'C', type: 'subTable', _bindingId: 1133 } as never],
        model: { row_id: 'TRANS-000008' },
        parentRow: { row_id: 'TRANS-000008' },
        editable: true,
        hostTableId: HOST_TABLE_ID,
        subTableBindings: [correspondenceBinding, noPkHost] as never,
        linkedSubTableBindings: [correspondenceBinding, noPkHost] as never,
      },
      global: { stubs: { SubTableField: true, FieldRenderer: true, SubTableInlineForm: true } },
    })
    const rows = (w.vm as never as {
      resolveSubTableRows: (b: unknown) => Array<Record<string, unknown>>
    }).resolveSubTableRows(correspondenceBinding as never)
    expect(rows.map(r => String(r.correspondence_id))).toEqual(['Corr-000041'])
    w.unmount()
  })

  /**
   * 刚 Add、外键还没 seed 的新行必须**留在表格里**。
   * 过滤掉它就会重现「新增一行后它立刻消失」——本次要修的症状之一。
   */
  it('keeps a freshly added row whose FK is not seeded yet', () => {
    const withFresh = {
      ...correspondenceBinding,
      bindingId: 1150,
      data: [
        { correspondence_id: 'Corr-000039', related_transaction_id: 'TRANS-000007' },
        { correspondence_id: 'Corr-000041', related_transaction_id: 'TRANS-000008' },
        { correspondence_id: 'Corr-000043' }, // 新行，还没 seed FK
      ],
    }
    const w = mount(PortalFormFields, {
      props: {
        fields: [{ key: 'c', label: 'C', type: 'subTable', _bindingId: 1150 } as never],
        model: { row_id: 'TRANS-000007' },
        parentRow: { row_id: 'TRANS-000007' },
        editable: true,
        hostTableId: HOST_TABLE_ID,
        subTableBindings: [withFresh, transactionBinding] as never,
        linkedSubTableBindings: [withFresh, transactionBinding] as never,
      },
      global: { stubs: { SubTableField: true, FieldRenderer: true, SubTableInlineForm: true } },
    })
    const rows = (w.vm as never as {
      resolveSubTableRows: (b: unknown) => Array<Record<string, unknown>>
    }).resolveSubTableRows(withFresh as never)
    expect(rows.map(r => String(r.correspondence_id)).sort())
      .toEqual(['Corr-000039', 'Corr-000043'])
    w.unmount()
  })

  /**
   * 宿主标识必须在 `model` 和 `parentRow` **两者**上找。
   *
   * <p>实测（task a736e30f）：`model` 是表单模型（`merchant_credit` 之类），身上没有 `row_id`；
   * 带行标识的是 `parentRow`。`hostRow` 取的是 `model ?? parentRow`，于是只看它会恒为 null，
   * 归属过滤失效 → 返回跨父行的全量池子 →
   * TRANS-000008 的弹窗里显示出了属于 TRANS-000012 的 `Corr-000045`。
   *
   * <p>同一次渲染里探针可证：`pullNested(parents=[TRANS-000008]) → []`（走 parentRow，正确），
   * 但兜底却把别人的行放了进来。
   */
  it('resolves the host key from parentRow when model is a bare form model', () => {
    const w = mount(PortalFormFields, {
      props: {
        fields: [{ key: 'c', label: 'C', type: 'subTable', _bindingId: 1133 } as never],
        // model = 表单模型，没有任何行标识
        model: { merchant_credit: 'N', temporary_refund: '' },
        // parentRow = 真正的数据行
        parentRow: { row_id: 'TRANS-000008' },
        editable: true,
        hostTableId: HOST_TABLE_ID,
        subTableBindings: [correspondenceBinding, transactionBinding] as never,
        linkedSubTableBindings: [correspondenceBinding, transactionBinding] as never,
      },
      global: { stubs: { SubTableField: true, FieldRenderer: true, SubTableInlineForm: true } },
    })
    const rows = (w.vm as never as {
      resolveSubTableRows: (b: unknown) => Array<Record<string, unknown>>
    }).resolveSubTableRows(correspondenceBinding as never)
    // 只能看到自己的；TRANS-000007 的 Corr-000039 不能出现
    expect(rows.map(r => String(r.correspondence_id))).toEqual(['Corr-000041'])
    w.unmount()
  })

  it('a table with no structural FK is not filtered (process-level shared table)', () => {
    const sharedBinding = {
      ...correspondenceBinding,
      bindingId: 1199,
      fieldDefinitions: [{ fieldName: 'correspondence_id', isPrimaryKey: true }],
      foreignKeyField: null,
      data: [{ correspondence_id: 'A' }, { correspondence_id: 'B' }],
    }
    const w = mount(PortalFormFields, {
      props: {
        fields: [{ key: 's', label: 'Shared', type: 'subTable', _bindingId: 1199 } as never],
        model: { transaction_number: 'TRANS-000007' },
        parentRow: { transaction_number: 'TRANS-000007' },
        editable: true,
        hostTableId: HOST_TABLE_ID,
        subTableBindings: [sharedBinding, transactionBinding] as never,
        linkedSubTableBindings: [sharedBinding, transactionBinding] as never,
      },
      global: { stubs: { SubTableField: true, FieldRenderer: true, SubTableInlineForm: true } },
    })
    const rows = (w.vm as never as {
      resolveSubTableRows: (b: unknown) => Array<Record<string, unknown>>
    }).resolveSubTableRows(sharedBinding as never)
    expect(rows.map(r => String(r.correspondence_id))).toEqual(['A', 'B'])
    w.unmount()
  })
})
