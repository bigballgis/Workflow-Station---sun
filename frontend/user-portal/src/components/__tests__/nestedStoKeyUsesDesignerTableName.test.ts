import { describe, expect, it } from 'vitest'
import { mergeNestedSubTableRowsIntoSto } from '../formRendererHelpers'

/**
 * 嵌套写回必须用**设计器表名**（`designerTableName`）算 key，不能退化到展示名。
 *
 * <p><b>实测故障（2026-09-03，task fbc5ba93 / ATM Correspondence）。</b>
 * `dw_table_definitions` 里 `table_name = atm_correspondence`、`display_name = ATM Correspondence`。
 * `mergeNestedSubTableRowsIntoSto` 的形参只声明了 `{ bindingId, tableName }`，调用方（
 * `PortalFormFields` 的两处）便把 `designerTableName` 丢掉了 —— {@link subTableStoreKey} 的取值
 * 顺序是 `designerTableName ?? tableName`，于是算出带空格的 `dw:atm correspondence`。
 *
 * <p>库里因此出现**同一张表两个 key**：`dw:atm correspondence`(4 行) 和
 * `dw:atm_correspondence`(6 行)。读取端用下划线那份、写入端写空格那份 ——
 * 编辑和删除都落进没人读的切片，表现为「分派前建的行改不动、删不掉，
 * 分派后新增的行正常」（新增的行两份都有，所以看着没事）。
 */
describe('nested __subTables__ write uses the designer table name', () => {
  const rows = [{ correspondence_id: 'Corr-000032' }]

  it('keys by designerTableName, not the display name', () => {
    const sto = mergeNestedSubTableRowsIntoSto(
      [{}],
      {
        bindingId: 1133,
        tableName: 'ATM Correspondence',      // 展示名（带空格）
        designerTableName: 'atm_correspondence', // 设计器表名
      },
      rows,
    )

    expect(Object.keys(sto)).toEqual(['dw:atm_correspondence'])
    expect(sto['dw:atm correspondence'], 'display-name key must not be created').toBeUndefined()
  })

  it('a relation table still keys by rt:<relationTableName>', () => {
    const sto = mergeNestedSubTableRowsIntoSto(
      [{}],
      {
        bindingId: 1200,
        tableName: 'HMDC Dropdown',
        designerTableName: 'hmdc_dropdown',
        relationTableName: 'hmdc_dropdown',
        relationTableId: 77,
      },
      rows,
    )

    expect(Object.keys(sto)).toEqual(['rt:hmdc_dropdown'])
  })

  it('existing slices on the host row are preserved, not clobbered', () => {
    const host = { __subTables__: { 'dw:other_table': [{ id: 'keep-me' }] } }
    const sto = mergeNestedSubTableRowsIntoSto(
      [host],
      { bindingId: 1133, tableName: 'ATM Correspondence', designerTableName: 'atm_correspondence' },
      rows,
    )

    expect(sto['dw:other_table']).toEqual([{ id: 'keep-me' }])
    expect(sto['dw:atm_correspondence']).toEqual(rows)
  })
})
