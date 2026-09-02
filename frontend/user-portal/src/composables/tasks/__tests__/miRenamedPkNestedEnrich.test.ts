import { describe, expect, it } from 'vitest'
import { enrichChildBindingRowsFromParentsNestedSubTables } from '../subTableNestedEnrich'

/**
 * 现场回归（fu-20260422-23tfag）：collection 主键改名成 `id_idwxwc`、link-child 外键改名成
 * `sub_task_idqc` 之后，`enrichChildBindingRowsFromParentsNestedSubTables` 完全空转。
 *
 * <p>根因是它调用 `miParentRowAlignsWithChildRow` 时**一个配置都没传**：
 * - 没有 FK 配置 → `resolveMiChildStructuralParentFk` 恒返回 null，走不到结构外键分支；
 * - 没有 collection 主键 → 宿主行的参与者标识只按历史列名表（id_idw / rowId / …）取，恒为空。
 *
 * 于是自己的行和别人的行**一律判 false**，宿主行 `__subTables__` 里的字段永远补不进子表行。
 * 方向上是"少补"而非"串数据"，所以既有单测全绿也发现不了。
 */
describe('嵌套 enrich：collection 主键 / 子表外键改名后仍能认出归属', () => {
  /** 设计器配置：collection 显式声明 miParticipantRow，PK 是改名后的 id_idwxwc。 */
  const collectionBinding = () => ({
    bindingId: 50544,
    tableName: 'subtable',
    tableId: 50331,
    bindingLinkMode: 'miParticipantRow',
    primaryKeyFields: ['id_idwxwc'],
    fieldDefinitions: [{ fieldName: 'id_idwxwc', isPrimaryKey: true }],
    data: [
      {
        id_idwxwc: 'Test-000004',
        name: 'zc',
        // 宿主行的嵌套切片里带着 People 行的完整字段
        __subTables__: {
          '50547': [{ idqc: 'u-1', sub_task_idqc: 'Test-000004', age: 'FROM-NESTED' }],
        },
      },
    ],
  })

  /** People：外键改名成 sub_task_idqc，指向 collection 的 tableId。 */
  const peopleBinding = () => ({
    bindingId: 50547,
    tableName: 'people',
    tableId: 50333,
    bindingLinkMode: 'structuralFk',
    primaryKeyFields: ['idqc'],
    fieldDefinitions: [
      { fieldName: 'idqc', isPrimaryKey: true },
      { fieldName: 'sub_task_idqc', isForeignKey: true, refTableId: 50331 },
    ],
    // 顶层这行只有身份字段，age 是空的 —— 正等着从宿主行的嵌套切片补齐
    data: [{ idqc: 'u-1', sub_task_idqc: 'Test-000004', age: '' }],
  })

  it('把宿主行嵌套切片里的字段补进 link-child 行', () => {
    const bindings = [collectionBinding(), peopleBinding()]
    enrichChildBindingRowsFromParentsNestedSubTables(bindings as never)
    // 改动前：归属判定恒 false → age 仍是 ''
    expect(bindings[1]!.data[0]!.age).toBe('FROM-NESTED')
  })

  it('不会把别的参与者的嵌套字段补到这一行上', () => {
    const coll = collectionBinding()
    // 宿主行换成另一个参与者，且其嵌套切片指向它自己
    coll.data[0]!.id_idwxwc = 'Test-000003'
    coll.data[0]!.__subTables__['50547'] = [
      { idqc: 'u-9', sub_task_idqc: 'Test-000003', age: 'SOMEONE-ELSE' },
    ]
    const bindings = [coll, peopleBinding()]
    enrichChildBindingRowsFromParentsNestedSubTables(bindings as never)
    // People 行属于 Test-000004，不能被 Test-000003 的值污染
    expect(bindings[1]!.data[0]!.age).toBe('')
  })
})
