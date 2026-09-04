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
   * 宿主主键列**只来自设计器配置**，不再兜底字面量 `'row_id'`。
   *
   * <p>这条用例原先断言的是相反的行为（「设计器没声明主键时退到 row_id」），前提是
   * "现场 `ATM_Transaction` 没有声明主键"。该前提经查库证伪：
   * `dw_field_definitions` 里 391/`row_id` 的 `is_primary_key = true` ——
   * `row_id` 本来就是它的**设计器主键**，配置读得出来，字面量是多余的。
   *
   * <p>而真正没有主键的表不该在这里被悄悄兜过去：MI 子任务表缺主键是**配置错误**，
   * 平台既定行为是抛 `MiConfigMissingError`（`useMiConfig.requireSubTablePrimaryKeyFields`）
   * 让用户回设计器补上。兜底只会让它一路"能用"到某个主键不叫 row_id 的 FU 上再静默失效。
   *
   * <p>所以：解析不出宿主标识 → 放弃过滤、返回全量（保守侧，不误删别人的行），
   * 而不是猜一个列名。
   */
  it('falls back to no filtering (not a guessed column) when the host declares no PK', () => {
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
    // 解析不出宿主标识：不过滤，全量返回 —— 不猜 `row_id`
    expect(rows.map(r => String(r.correspondence_id)).sort())
      .toEqual(['Corr-000039', 'Corr-000041'])
    w.unmount()
  })

  /** 宿主声明了主键时，按配置精确过滤（与上一条互为对照）。 */
  it('filters by the designer PK when the host declares one', () => {
    const w = mountForParent('TRANS-000008')
    expect(rowsFor(w)).toEqual(['Corr-000041'])
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
        // 宿主行按**设计器主键**（transactionBinding.primaryKeyFields = transaction_number）
        // 携带标识。此前写成 row_id，只是因为解析器额外兜底了一个 `'row_id'` 字面量。
        model: { transaction_number: 'TRANS-000007' },
        parentRow: { transaction_number: 'TRANS-000007' },
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
        // parentRow = 真正的数据行，按设计器主键携带标识
        parentRow: { transaction_number: 'TRANS-000008' },
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
