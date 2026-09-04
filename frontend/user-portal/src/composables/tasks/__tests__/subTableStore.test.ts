import { describe, expect, it } from 'vitest'
import {
  collectCanonicalKeys,
  isCanonicalStoreKey,
  normalizeStoreTableName,
  readSubTableRows,
  subTableStoreKey,
  writeSubTableRows,
} from '../subTableStore'

/**
 * key 规则以 FU 50005「Multi-Instance Subtask Demo」的实测绑定为样本：
 *   dw 表 : main / subtable(显示 Participants) / attachment / people / meeting_remark
 *   rt 表 : test、sys_users(虚拟, id -1000000001)
 */

describe('subTableStoreKey — 命名空间与表名', () => {
  it('DW binding 用设计器表名，不用展示名', () => {
    // 实测 API：binding 50627 的 tableName=subtable、tableDisplayName=Participants。
    // 展示名可能跨 FU 重复，且历史上正是 "Participants"/"participants" 这类别名
    // 造成了同一行的多份副本，所以绝不能进 key。
    expect(subTableStoreKey({ tableName: 'subtable', tableDisplayName: 'Participants' }))
      .toBe('dw:subtable')
  })

  it('designerTableName 优先于 tableName（部分接口只给前者）', () => {
    expect(subTableStoreKey({ designerTableName: 'subtable', tableName: 'Participants' }))
      .toBe('dw:subtable')
  })

  it('RT binding 落到 rt: 命名空间', () => {
    expect(subTableStoreKey({ relationTableId: 1, relationTableName: 'test' })).toBe('rt:test')
  })

  it('平台虚拟表 sys_users 无需特例', () => {
    // 该表不在 rt_table_definitions 里，但 binding 上带 tableName=sys_users。
    expect(subTableStoreKey({ relationTableId: -1000000001, tableName: 'sys_users' }))
      .toBe('rt:sys_users')
  })

  it('DW 与 RT 同名时靠前缀隔离', () => {
    // 两张定义表各自唯一，跨表无联合约束（实测 id 已撞过 2 个），故前缀是必需的。
    expect(subTableStoreKey({ tableName: 'test' })).toBe('dw:test')
    expect(subTableStoreKey({ relationTableId: 1, relationTableName: 'test' })).toBe('rt:test')
  })

  it('大小写/空白归一，与 DB 的 lower() 唯一索引对齐', () => {
    expect(subTableStoreKey({ tableName: '  SubTable ' })).toBe('dw:subtable')
    expect(normalizeStoreTableName('  Participants ')).toBe('participants')
  })

  it('解析不出表名时返回 null，不猜', () => {
    expect(subTableStoreKey({})).toBeNull()
    expect(subTableStoreKey({ tableName: '   ' })).toBeNull()
    expect(subTableStoreKey(null)).toBeNull()
  })

  it('key 一律不含 binding id —— binding 不是数据身份', () => {
    const key = subTableStoreKey({ tableName: 'subtable', tableDisplayName: 'Participants' })!
    expect(key).not.toMatch(/\d/)
  })

  it('同一张表的不同 binding 解析到同一个 key', () => {
    // 实测：subtable 被 6 个 binding 绑定（50539/50544/50612/50617/50625/50627），
    // 历史结构下它们各存一份、可以分叉；新规则下必须收敛到同一个 key。
    const bindings = [
      { bindingId: 50539, tableName: 'subtable', tableDisplayName: 'Participants' },
      { bindingId: 50544, tableName: 'subtable', tableDisplayName: 'Participants' },
      { bindingId: 50627, tableName: 'subtable', tableDisplayName: 'Participants' },
      { bindingId: 50612, tableName: 'subtable' },
      { bindingId: 50617, tableName: 'subtable' },
      { bindingId: 50625, tableName: 'subtable' },
    ]
    expect(new Set(bindings.map(b => subTableStoreKey(b)))).toEqual(new Set(['dw:subtable']))
  })
})

describe('isCanonicalStoreKey', () => {
  it('区分新旧 key（过渡期需要）', () => {
    expect(isCanonicalStoreKey('dw:subtable')).toBe(true)
    expect(isCanonicalStoreKey('rt:test')).toBe(true)
    // 旧结构的三类 key
    expect(isCanonicalStoreKey('50539')).toBe(false)         // binding id
    expect(isCanonicalStoreKey('subtable')).toBe(false)      // 裸表名
    expect(isCanonicalStoreKey('Participants')).toBe(false)  // 展示名别名
  })
})

describe('writeSubTableRows / readSubTableRows', () => {
  const binding = { tableName: 'subtable', tableDisplayName: 'Participants' }

  it('只写一个规范 key，不再扇出别名', () => {
    const store: Record<string, unknown> = {}
    const rows = [{ id_idwvvbz: 'Test-000005' }, { id_idwvvbz: 'Test-000006' }]

    expect(writeSubTableRows(store, binding, rows)).toBe(true)

    expect(Object.keys(store)).toEqual(['dw:subtable'])
    // 历史实现会同时写这些 key，正是分叉的来源
    expect(store).not.toHaveProperty('50627')
    expect(store).not.toHaveProperty('subtable')
    expect(store).not.toHaveProperty('Participants')
  })

  it('不同 binding 写同一张表 = 覆盖同一个 key，不会产生第二份', () => {
    const store: Record<string, unknown> = {}
    writeSubTableRows(store, { tableName: 'subtable' }, [{ id_idwvvbz: 'A' }])
    writeSubTableRows(store, { tableName: 'subtable', tableDisplayName: 'Participants' },
      [{ id_idwvvbz: 'A' }, { id_idwvvbz: 'B' }])

    expect(Object.keys(store)).toHaveLength(1)
    expect((store['dw:subtable'] as unknown[])).toHaveLength(2)
  })

  it('解析不出 key 时不写入并返回 false', () => {
    const store: Record<string, unknown> = {}
    expect(writeSubTableRows(store, {}, [{ a: 1 }])).toBe(false)
    expect(Object.keys(store)).toHaveLength(0)
  })

  it('读取只认规范 key，不做名字兜底', () => {
    const rows = [{ id_idwvvbz: 'Test-000006' }]
    expect(readSubTableRows({ 'dw:subtable': rows }, binding)).toBe(rows)
    // 旧 key 不认 —— 兜底正是旧结构分叉的原因之一，回退由调用方在过渡期显式处理
    expect(readSubTableRows({ subtable: rows }, binding)).toBeUndefined()
    expect(readSubTableRows({ '50627': rows }, binding)).toBeUndefined()
    expect(readSubTableRows({ 'dw:subtable': 'not-an-array' }, binding)).toBeUndefined()
    expect(readSubTableRows(null, binding)).toBeUndefined()
  })
})

describe('collectCanonicalKeys — 含行内嵌套', () => {
  it('递归收集顶层与行内嵌套的规范 key', () => {
    // 行内嵌套与顶层同构：participant 行里挂着 people 子表
    const store = {
      'dw:subtable': [
        { id_idwvvbz: 'Test-000005', __subTables__: { 'dw:people': [{ id: 'p-1' }] } },
        { id_idwvvbz: 'Test-000006', __subTables__: { 'dw:people': [] } },
      ],
      'dw:attachment': [],
      'rt:sys_users': [],
      '50539': [],        // 旧 key 不计入
      Participants: [],   // 旧别名不计入
    }

    expect(collectCanonicalKeys(store))
      .toEqual(new Set(['dw:subtable', 'dw:attachment', 'rt:sys_users', 'dw:people']))
  })

  it('输入非对象时安全返回空集', () => {
    expect(collectCanonicalKeys(null).size).toBe(0)
    expect(collectCanonicalKeys([]).size).toBe(0)
  })
})
