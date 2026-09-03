import { describe, expect, it } from 'vitest'
import {
  buildViewDetailSubTableBindings,
  resolveLookupDisplayValues,
  toViewDetailFields,
} from '../viewDetailForm'

describe('toViewDetailFields', () => {
  it('keeps a nested subTable instead of dropping it', () => {
    const fields = toViewDetailFields(
      [
        { type: 'input', field: 'merchant', title: 'Merchant' },
        { type: 'subTable', _bindingId: 200, title: 'Credit Card Correspondence' },
      ],
      {},
    )
    expect(fields.map(f => f.key)).toEqual(['merchant', '__subTable_200'])
    expect(fields[1]._bindingId).toBe(200)
  })

  it('keeps remaining fields when lookupConfig JSON is malformed', () => {
    const fields = toViewDetailFields(
      [
        { type: 'lookup', field: 'owner', title: 'Owner', props: { lookupConfig: '{not-json' } },
        { type: 'input', field: 'merchant', title: 'Merchant' },
      ],
      {},
    )
    expect(fields.map(f => f.key)).toEqual(['owner', 'merchant'])
    expect(fields[0].type).toBe('lookup')
  })
})

describe('resolveLookupDisplayValues', () => {
  const lookupField = (extra: Record<string, unknown> = {}) => ({
    key: 'correspondence_type',
    label: 'Correspondence type',
    type: 'lookup',
    ...extra,
  }) as any

  it('renders the display column instead of the whole referenced row', () => {
    const out = resolveLookupDisplayValues(
      [lookupField({ _lookupSelectedDisplayField: 'type_name' })],
      {
        correspondence_type: {
          id: 'hmdc-corr-type-int',
          type_name: 'Internal',
          created_by: 'system',
        },
      },
    )
    // 原来整行对象被 String() 成 [object Object] 摆在只读表单上。
    expect(out.correspondence_type).toBe('Internal')
  })

  it('resolves a field the designer typed as a plain input, not as a lookup', () => {
    // 实测：FU atm 的 DETAIL 表单里 correspondence_type 是 `input`，值却是关联表整行。
    // 判据必须是「值是对象」，否则这一条永远走不到。
    const out = resolveLookupDisplayValues(
      [{
        key: 'correspondence_type',
        label: 'Correspondence type',
        type: 'input',
        _lookupSelectedDisplayField: 'standardizations',
      } as any],
      {
        correspondence_type: {
          id: 'hmdc-corr-type-int',
          created_by: 'system',
          standardizations: 'Internal Request',
        },
      },
    )
    expect(out.correspondence_type).toBe('Internal Request')
  })

  it('blanks the cell when no display column is configured, rather than guessing one', () => {
    // 共享解析链在「设计器没配显示列」时故意返回 '-'（不猜列名）。明细页据此留空：
    // 猜 name/code/label 这类白名单正是本仓库明令禁止的启发式。
    const out = resolveLookupDisplayValues(
      [lookupField()],
      { correspondence_type: { id: 'x1', standardizations: 'Email' } },
    )
    expect(out.correspondence_type).toBe('')
    expect(String(out.correspondence_type)).not.toContain('[object Object]')
  })

  it('joins a multi-select lookup rather than showing objects', () => {
    const out = resolveLookupDisplayValues(
      [lookupField({ _lookupDisplayField: 'name' })],
      { correspondence_type: [{ name: 'A' }, { name: 'B' }] },
    )
    expect(out.correspondence_type).toBe('A, B')
  })

  it('never emits [object Object] when no column is displayable', () => {
    const out = resolveLookupDisplayValues(
      [lookupField()],
      { correspondence_type: { nested: { deep: 1 } } },
    )
    expect(String(out.correspondence_type)).not.toContain('[object Object]')
  })

  it('leaves scalar values exactly as they are', () => {
    const out = resolveLookupDisplayValues(
      [lookupField(), { key: 'plain', label: 'Plain', type: 'input' } as any],
      { correspondence_type: 'already-text', plain: 42, blank: null },
    )
    expect(out.correspondence_type).toBe('already-text')
    expect(out.plain).toBe(42)
    expect(out.blank).toBeNull()
  })

  it('keeps a scalar array (not a row list) readable', () => {
    const out = resolveLookupDisplayValues(
      [lookupField()],
      { correspondence_type: ['A', 'B'] },
    )
    // 标量数组不是「整行对象」列表，原样交给渲染器。
    expect(out.correspondence_type).toEqual(['A', 'B'])
  })
})

describe('buildViewDetailSubTableBindings', () => {
  it('hydrates nested rows from the view row __subTables__ slice', () => {
    const bindings = buildViewDetailSubTableBindings(
      [
        {
          bindingId: 200,
          bindingType: 'SUB',
          tableName: 'ccc',
          tableDisplayName: 'Credit Card Correspondence',
          tableType: 'SUB',
          fieldDefinitions: [{ fieldName: 'correspondence_type', displayName: 'Type' }],
        },
      ],
      {},
      { __subTables__: { 'dw:ccc': [{ correspondence_type: 'LETTER' }] } },
    )
    expect(bindings).toHaveLength(1)
    expect(bindings[0].bindingId).toBe(200)
    expect(bindings[0].data).toEqual([{ correspondence_type: 'LETTER' }])
    expect(bindings[0].columns.map(c => c.field)).toEqual(['correspondence_type'])
  })

  it('does not read the slice by binding id, which no writer produces any more', () => {
    const bindings = buildViewDetailSubTableBindings(
      [
        {
          bindingId: 200,
          bindingType: 'SUB',
          tableName: 'ccc',
          tableDisplayName: 'Credit Card Correspondence',
          tableType: 'SUB',
          fieldDefinitions: [{ fieldName: 'correspondence_type', displayName: 'Type' }],
        },
      ],
      {},
      // Legacy shape: binding id and display name. Reading either back would resurrect the
      // stale-key path that made the Views detail sub-table silently empty.
      {
        __subTables__: {
          200: [{ correspondence_type: 'LETTER' }],
          'Credit Card Correspondence': [{ correspondence_type: 'EMAIL' }],
        },
      },
    )
    expect(bindings[0].data).toEqual([])
  })

  it('reads a relation-table binding from the rt: namespace, not dw:', () => {
    const bindings = buildViewDetailSubTableBindings(
      [
        {
          bindingId: 201,
          bindingType: 'SUB',
          tableName: 'shared_ref',
          tableDisplayName: 'Shared Reference',
          tableType: 'SUB',
          // 后端 enrichRelationTableIdentity 会盖上这两个字段，命名空间据此判定。
          relationTableId: 900,
          relationTableName: 'shared_ref',
          fieldDefinitions: [{ fieldName: 'code', displayName: 'Code' }],
        },
      ],
      {},
      {
        __subTables__: {
          'dw:shared_ref': [{ code: 'WRONG-NAMESPACE' }],
          'rt:shared_ref': [{ code: 'RT-1' }],
        },
      },
    )
    expect(bindings[0].data).toEqual([{ code: 'RT-1' }])
  })

  it('normalises table-name case so a mixed-case designer table still resolves', () => {
    const bindings = buildViewDetailSubTableBindings(
      [
        {
          bindingId: 202,
          bindingType: 'SUB',
          // 真实数据里存在 ATM_Transaction 这类大小写混排的表名。
          tableName: 'ATM_Transaction',
          tableDisplayName: 'ATM Transaction',
          tableType: 'SUB',
          fieldDefinitions: [{ fieldName: 'amount', displayName: 'Amount' }],
        },
      ],
      {},
      { __subTables__: { 'dw:atm_transaction': [{ amount: 10 }] } },
    )
    expect(bindings[0].data).toEqual([{ amount: 10 }])
  })
})
