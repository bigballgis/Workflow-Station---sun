import { describe, expect, it } from 'vitest'
import { flattenNestedSubTableRowsIntoPayload, flattenSliceMapsFromBindings } from '../miLinkChildScrub'
import type { FlattenParentLinkMaps } from '../flattenParentLink'

const TX_TABLE_ID = 391
const CORR_TABLE_ID = 392
const CASE_TABLE_ID = 100

const TX_PARENT_LINK: FlattenParentLinkMaps = {
  tableIdBySliceKey: {
    'dw:atm_transaction': TX_TABLE_ID,
    'dw:atm_correspondence': CORR_TABLE_ID,
  },
  fieldDefinitionsBySliceKey: {
    'dw:atm_correspondence': [
      { fieldName: 'related_transaction_id', isForeignKey: true, refTableId: TX_TABLE_ID },
    ],
  },
}

const CASE_PARENT_LINK: FlattenParentLinkMaps = {
  tableIdBySliceKey: {
    'dw:atm_case': CASE_TABLE_ID,
    'dw:atm_correspondence': CORR_TABLE_ID,
  },
  fieldDefinitionsBySliceKey: {
    'dw:atm_correspondence': [
      { fieldName: 'related_case_id', isForeignKey: true, refTableId: CASE_TABLE_ID },
    ],
  },
}

/**
 * Link Form 弹窗里把某个父行的嵌套子表**删到空**，该父行的行必须从顶层切片消失。
 *
 * <p><b>实测故障（2026-09-04，task a736e30f / ATM Transaction → ATM Correspondence）。</b>
 * 用户在 TRANS-000007 的 Details 里删掉 `Corr-000039`，删除**确实**写回了父行
 * （`tx[0].__subTables__['dw:atm_correspondence'] === []`），但顶层切片里那一行还在，
 * 刷新后又显示出来。
 *
 * <p>根因：`flattenNestedSubTableRowsIntoPayload` 遍历嵌套切片时
 * `if (!Array.isArray(childVal) || childVal.length === 0) continue` ——
 * **空数组被整个跳过**，于是「这个父行已经没有子行了」这条信息传不到顶层，
 * 顶层保留的旧行成了唯一真相。删到只剩一行时也不对：非空走并集会把顶层残留行填回去
 * （见下方 partial nested delete）。删到空才最先暴露 —— 那是「最后一行删不掉」的那一步。
 */
describe('Link Form: deleting a parent row nested slice down to empty', () => {
  function build() {
    return {
      'dw:atm_transaction': [
        { row_id: 'TRANS-000007', __subTables__: { 'dw:atm_correspondence': [] } },
        {
          row_id: 'TRANS-000008',
          __subTables__: {
            'dw:atm_correspondence': [
              { correspondence_id: 'Corr-000041', related_transaction_id: 'TRANS-000008' },
            ],
          },
        },
      ],
      // 顶层还留着已删除的 Corr-000039
      'dw:atm_correspondence': [
        { correspondence_id: 'Corr-000039', related_transaction_id: 'TRANS-000007' },
        { correspondence_id: 'Corr-000041', related_transaction_id: 'TRANS-000008' },
      ],
    } as Record<string, unknown>
  }

  /**
   * 父表 `ATM_Transaction` 的设计器主键**就是** `row_id`（实测 `is_primary_key=true`,
   * `prefixedSequence`, 前缀 `ATM-DC-PW-TRANS-`）。调用方必须把这份配置传进来 ——
   * 从前这里靠 `['row_id','id_idw','id']` 猜名字，对主键叫 `correspondence_id`、
   * `case_number` 的表一律解析不出父行标识，「删到空」直接跳过 = 最后一行删不掉。
   */
  const PK_BY_SLICE = { 'dw:atm_transaction': ['row_id'] }

  it("drops the emptied parent's rows from the top-level slice", () => {
    const subTables = build()
    flattenNestedSubTableRowsIntoPayload(subTables, 8, PK_BY_SLICE, TX_PARENT_LINK)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    // 被删掉的行不能再出现
    expect(ids).not.toContain('Corr-000039')
    // 另一个父行的行必须原样保留
    expect(ids).toContain('Corr-000041')
  })

  /**
   * 这才是「读配置」真正买到的东西：父表主键叫 `case_number`——不在原来那份
   * `['row_id','id_idw','id']` 名单里。猜名字的实现在这里解析不出父行标识，
   * 「删到空」分支 `continue`，被删的行留在顶层、刷新后复活。
   */
  it('works when the parent key is named nothing like id/row_id', () => {
    const subTables = {
      'dw:atm_case': [
        { case_number: 'CASE-000007', __subTables__: { 'dw:atm_correspondence': [] } },
      ],
      'dw:atm_correspondence': [
        { correspondence_id: 'Corr-000039', related_case_id: 'CASE-000007' },
        { correspondence_id: 'Corr-000041', related_case_id: 'CASE-000008' },
      ],
    } as Record<string, unknown>

    flattenNestedSubTableRowsIntoPayload(
      subTables, 8, { 'dw:atm_case': ['case_number'] }, CASE_PARENT_LINK,
    )

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).not.toContain('Corr-000039')
    expect(ids).toContain('Corr-000041')
  })

  it('a non-empty nested slice still merges up as before', () => {
    const subTables = build()
    ;(subTables['dw:atm_transaction'] as Array<Record<string, unknown>>)[0].__subTables__ = {
      'dw:atm_correspondence': [
        { correspondence_id: 'Corr-000043', related_transaction_id: 'TRANS-000007' },
      ],
    }
    flattenNestedSubTableRowsIntoPayload(subTables)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).toContain('Corr-000043')
    expect(ids).toContain('Corr-000041')
  })

  it('does not drop top-level rows when child FK config is missing', () => {
    const subTables = build()
    flattenNestedSubTableRowsIntoPayload(subTables, 8, PK_BY_SLICE)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).toContain('Corr-000039')
    expect(ids).toContain('Corr-000041')
  })

  it('drops emptied rows when FK maps are built from bindings', () => {
    const subTables = build()
    const { primaryKeyFieldsBySliceKey, parentLink } = flattenSliceMapsFromBindings([
      {
        designerTableName: 'atm_transaction',
        tableId: TX_TABLE_ID,
        primaryKeyFields: ['row_id'],
      },
      {
        designerTableName: 'atm_correspondence',
        tableId: CORR_TABLE_ID,
        fieldDefinitions: [
          { fieldName: 'related_transaction_id', isForeignKey: true, refTableId: TX_TABLE_ID },
        ],
      },
    ])
    flattenNestedSubTableRowsIntoPayload(subTables, 8, primaryKeyFieldsBySliceKey, parentLink)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).not.toContain('Corr-000039')
    expect(ids).toContain('Corr-000041')
  })
})

/**
 * 嵌套里还剩行时，顶层必须按「这个父行的嵌套切片」替换，不能并集。
 *
 * <p>实测（2026-09-10，process df871a10 / ATM Transaction → ATM Correspondence）：
 * Link Form 里删掉 Corr-000042 但嵌套仍有 041，提交走 {@code mergeSubTableRowsByRowId}
 * 把顶层残留的 042 填回去。Change History 看不到 DELETE，变量里 6 条 Corr 一条没少。
 * 一层子表（MI demo subtable/attachment）不受影响，因为没有第二份顶层切片可并回去。
 */
describe('Link Form: partial nested delete must drop the row from the top-level slice', () => {
  const PK_BY_SLICE = { 'dw:atm_transaction': ['row_id'] }

  it('drops a row deleted from a still-non-empty nested slice', () => {
    const subTables = {
      'dw:atm_transaction': [
        {
          row_id: 'ATM-DC-PW-TRANS-000022',
          __subTables__: {
            'dw:atm_correspondence': [
              {
                correspondence_id: 'Corr-000041',
                related_transaction_id: 'ATM-DC-PW-TRANS-000022',
              },
              {
                correspondence_id: 'Corr-000043',
                related_transaction_id: 'ATM-DC-PW-TRANS-000022',
              },
            ],
          },
        },
      ],
      'dw:atm_correspondence': [
        {
          correspondence_id: 'Corr-000041',
          related_transaction_id: 'ATM-DC-PW-TRANS-000022',
        },
        {
          correspondence_id: 'Corr-000042',
          related_transaction_id: 'ATM-DC-PW-TRANS-000022',
        },
        {
          correspondence_id: 'Corr-000043',
          related_transaction_id: 'ATM-DC-PW-TRANS-000022',
        },
        {
          correspondence_id: 'Corr-000044',
          related_transaction_id: 'ATM-DC-PW-TRANS-000099',
        },
      ],
    } as Record<string, unknown>

    flattenNestedSubTableRowsIntoPayload(subTables, 8, PK_BY_SLICE, TX_PARENT_LINK)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).not.toContain('Corr-000042')
    expect(ids).toContain('Corr-000041')
    expect(ids).toContain('Corr-000043')
    expect(ids).toContain('Corr-000044')
  })

  it('replacing a parent nested slice drops that parent\'s previous top-level rows', () => {
    const subTables = {
      'dw:atm_transaction': [
        {
          row_id: 'TRANS-000007',
          __subTables__: {
            'dw:atm_correspondence': [
              { correspondence_id: 'Corr-000043', related_transaction_id: 'TRANS-000007' },
            ],
          },
        },
        {
          row_id: 'TRANS-000008',
          __subTables__: {
            'dw:atm_correspondence': [
              { correspondence_id: 'Corr-000041', related_transaction_id: 'TRANS-000008' },
            ],
          },
        },
      ],
      'dw:atm_correspondence': [
        { correspondence_id: 'Corr-000039', related_transaction_id: 'TRANS-000007' },
        { correspondence_id: 'Corr-000041', related_transaction_id: 'TRANS-000008' },
      ],
    } as Record<string, unknown>

    flattenNestedSubTableRowsIntoPayload(subTables, 8, PK_BY_SLICE, TX_PARENT_LINK)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).not.toContain('Corr-000039')
    expect(ids).toContain('Corr-000043')
    expect(ids).toContain('Corr-000041')
  })

  /**
   * 同一父行嵌套里同时有规范 key 和展示名 key 时，只能提升规范那份。
   * 否则 flatten 把 `dw:atm correspondence` 里多出来的 048 再写回顶层。
   */
  it('does not hoist a display-name nested alias that still holds a deleted row', () => {
    const subTables = {
      'dw:atm_transaction': [
        {
          row_id: 'ATM-DC-PW-TRANS-000023',
          __subTables__: {
            'dw:atm_correspondence': [
              {
                correspondence_id: 'Corr-000047',
                related_transaction_id: 'ATM-DC-PW-TRANS-000023',
              },
            ],
            'dw:atm correspondence': [
              {
                correspondence_id: 'Corr-000047',
                related_transaction_id: 'ATM-DC-PW-TRANS-000023',
              },
              {
                correspondence_id: 'Corr-000048',
                related_transaction_id: 'ATM-DC-PW-TRANS-000023',
              },
            ],
          },
        },
      ],
      'dw:atm_correspondence': [
        {
          correspondence_id: 'Corr-000047',
          related_transaction_id: 'ATM-DC-PW-TRANS-000023',
        },
        {
          correspondence_id: 'Corr-000048',
          related_transaction_id: 'ATM-DC-PW-TRANS-000023',
        },
      ],
    } as Record<string, unknown>

    flattenNestedSubTableRowsIntoPayload(subTables, 8, PK_BY_SLICE, TX_PARENT_LINK)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).toEqual(['Corr-000047'])
    expect(ids).not.toContain('Corr-000048')
    const alias = subTables['dw:atm correspondence']
    if (Array.isArray(alias)) {
      expect(alias.map(r => String((r as Record<string, unknown>).correspondence_id)))
        .not.toContain('Corr-000048')
    }
  })

  /**
   * 顶层「这一行属于这个父行」只能看设计器结构外键，不能扫所有标量。
   * 另一父行的子行若 notes/memo 碰巧等于本父行主键，扫全字段会把它从顶层丢掉。
   */
  it('does not drop a sibling whose non-FK field equals this parent key', () => {
    const parentPk = 'ATM-DC-PW-TRANS-000022'
    const subTables = {
      'dw:atm_transaction': [
        {
          row_id: parentPk,
          __subTables__: {
            'dw:atm_correspondence': [
              {
                correspondence_id: 'Corr-000041',
                related_transaction_id: parentPk,
              },
            ],
          },
        },
      ],
      'dw:atm_correspondence': [
        {
          correspondence_id: 'Corr-000041',
          related_transaction_id: parentPk,
        },
        {
          correspondence_id: 'Corr-000042',
          related_transaction_id: parentPk,
        },
        {
          correspondence_id: 'Corr-000099',
          related_transaction_id: 'ATM-DC-PW-TRANS-000099',
          notes: parentPk,
        },
      ],
    } as Record<string, unknown>

    flattenNestedSubTableRowsIntoPayload(subTables, 8, PK_BY_SLICE, TX_PARENT_LINK)

    const ids = (subTables['dw:atm_correspondence'] as Array<Record<string, unknown>>)
      .map(r => String(r.correspondence_id))
    expect(ids).not.toContain('Corr-000042')
    expect(ids).toContain('Corr-000041')
    expect(ids).toContain('Corr-000099')
  })
})
