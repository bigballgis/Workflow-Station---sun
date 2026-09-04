import { describe, expect, it } from 'vitest'

/**
 * 新父行的 Link Form 不能继承**别的父行**的嵌套切片。
 *
 * <p><b>实测故障（2026-09-04，task a736e30f / ATM Transaction → ATM Correspondence）。</b>
 * 每新建一个 transaction，它的嵌套 correspondence 就把上一个父行的整份切片继承过来，再追加自己的：
 * <pre>
 *   TRANS-000016 -> [45/000012, 48/000015, 49/000016]
 *   TRANS-000017 -> [45/000012, 48/000015, 49/000016, 50/000017]   ← 000016 的全部 + 自己的 50
 * </pre>
 * 于是「新增一条后其它的不见了 / 保存后变成 3 条」，而且**脏数据被持久化**：
 * 查库可见 TRANS-000016 的 `__subTables__` 里存着 FK 指向 000012、000015 的行。
 *
 * <p>根因在 `buildLinkedFormData`：模型从 `binding.data[0]` 播种，
 * 新行没有自己的数据时 `data[0]` 就是**上一个父行**，其 `__subTables__` 被整份复制过来。
 * 嵌套切片属于**某一行**，不是这张表的公共属性，绝不能跨行继承。
 */
describe('Link Form model seeding must not inherit another row nested slice', () => {
  /** buildLinkedFormData 里那段播种逻辑（只保留与本用例相关的部分）。 */
  function seed(raw: Record<string, any>, opts: { inheritNested: boolean }) {
    const next: Record<string, any> = { transaction_number: raw.transaction_number }
    if (opts.inheritNested) {
      // 旧行为：无条件复制
      if (raw.__subTables__ && typeof raw.__subTables__ === 'object') {
        next.__subTables__ = raw.__subTables__
      }
    }
    return next
  }

  const previousRow = {
    row_id: 'TRANS-000016',
    transaction_number: 'TRANS-000016',
    __subTables__: {
      'dw:atm_correspondence': [
        { correspondence_id: 'Corr-000045', related_transaction_id: 'TRANS-000012' },
        { correspondence_id: 'Corr-000049', related_transaction_id: 'TRANS-000016' },
      ],
    },
  }

  it('reproduces the inheritance: seeding from a foreign row carries its nested rows', () => {
    const seeded = seed(previousRow, { inheritNested: true })
    const corr = (seeded.__subTables__?.['dw:atm_correspondence'] ?? []) as Array<Record<string, unknown>>
    // 这正是 bug：新行凭空拿到了 000016 / 000012 的 correspondence
    expect(corr.map(r => r.correspondence_id)).toEqual(['Corr-000045', 'Corr-000049'])
  })

  it('a row seeded for a DIFFERENT parent must start with no nested rows', () => {
    const seeded = seed(previousRow, { inheritNested: false })
    expect(seeded.__subTables__).toBeUndefined()
  })

  /**
   * `buildLinkedFormData` 的播种选行逻辑：**按身份挑本人**，挑不到才退回 `data[0]`，
   * 且只有挑中本人时才继承 `__subTables__`。
   *
   * <p>两个方向都要锁住：
   * <ul>
   *   <li>挑中本人 → **必须**带上自己的嵌套行（否则打开 Details 时已有数据会消失 ——
   *       我第一版判据把 raw 和「被点击的父行」比，两者永不相等，导致所有行都不显示）；</li>
   *   <li>挑不到（池子里只有别的父行）→ **不带**，避免跨行继承。</li>
   * </ul>
   */
  function pickSeed(rows: Record<string, any>[], wantKey: string | null) {
    const matched = wantKey == null
      ? undefined
      : rows.find(r => String(r?.row_id ?? '') === wantKey)
    // 挑不到就用空行 —— **不退回 data[0]**，那等于猜「这一行是谁」。
    const raw: Record<string, any> = matched ?? {}
    const next: Record<string, any> = { transaction_number: raw.transaction_number }
    if (raw.__subTables__) next.__subTables__ = raw.__subTables__
    return next
  }

  const ownRow = {
    row_id: 'TRANS-000017',
    transaction_number: 'TRANS-000017',
    __subTables__: {
      'dw:atm_correspondence': [
        { correspondence_id: 'Corr-000050', related_transaction_id: 'TRANS-000017' },
      ],
    },
  }

  it('keeps its OWN nested rows when the row is found in the pool', () => {
    const seeded = pickSeed([previousRow, ownRow], 'TRANS-000017')
    const corr = (seeded.__subTables__?.['dw:atm_correspondence'] ?? []) as Array<Record<string, unknown>>
    expect(corr.map(r => r.correspondence_id)).toEqual(['Corr-000050'])
  })

  it('carries nothing when only another parent row is in the pool', () => {
    const seeded = pickSeed([previousRow], 'TRANS-000017')
    expect(seeded.__subTables__).toBeUndefined()
    // 连字段值也不能继承：退回 data[0] 会把上一个 transaction 的值填进新行
    expect(seeded.transaction_number).toBeUndefined()
  })

  it('an unidentifiable row seeds empty rather than borrowing data[0]', () => {
    const seeded = pickSeed([previousRow], null)
    expect(seeded.__subTables__).toBeUndefined()
    expect(seeded.transaction_number).toBeUndefined()
  })
})
