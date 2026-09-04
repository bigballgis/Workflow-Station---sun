/**
 * 设计器主键策略为 `prefixedSequence`（Corr-000004 / ATM-DC-PW-TRANS-000004）的子表，
 * 同一参与者名下的多行**不得**被 collapse 当成"同一行的碎片"合并。
 *
 * <p>回归背景：`linkChildRowAllocatedPk` 曾用 UUID 正则判断"这行有没有已分配主键"，于是
 * 所有非 uuid 策略的主键都被判成"未分配"，collapse 的防误伤守卫失效 —— ACQ/ATM 的
 * ATM Correspondence 加第二条时被合并进第一条，用户表现为"加不进去"。
 * 而 uuid 主键的 People 表恰好正常，故只在部分 FU 复现。
 */
import { describe, it, expect } from 'vitest'
import { collapseMiLinkChildRowsToOnePerParticipant } from '../miLinkChildRows'
import { miChildFkConfigOfBinding } from '../miLinkChildIdentity'

/** FU 24 现场形状：ATM Correspondence(392) 的 FK 指向 MI collection ATM_Transaction(391)。 */
const CORRESPONDENCE_BINDING = {
  bindingId: 1133,
  tableId: 392,
  tableName: 'atm_correspondence',
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'related_transaction_id',
  primaryKeyFields: ['correspondence_id'],
  fieldDefinitions: [
    { fieldName: 'correspondence_id', isPrimaryKey: true },
    { fieldName: 'related_transaction_id', isForeignKey: true, refTableId: 391 },
  ],
}

/** People 式：主键是 uuid 策略——修复前后都必须保持正确。 */
const PEOPLE_BINDING = {
  bindingId: 50030,
  tableId: 50333,
  tableName: 'people',
  bindingLinkMode: 'structuralFk',
  primaryKeyFields: ['idqcxma'],
  fieldDefinitions: [
    { fieldName: 'idqcxma', isPrimaryKey: true },
    { fieldName: 'sub_task_id', isForeignKey: true, refTableId: 50331 },
  ],
}

const MY_TX = 'ATM-DC-PW-TRANS-000004'

describe('collapse 不得吃掉 prefixedSequence 主键的兄弟行', () => {
  it('同一 Transaction 下的两条 Correspondence 都保留', () => {
    const cfg = miChildFkConfigOfBinding(CORRESPONDENCE_BINDING as never, 391)
    const rows = [
      { correspondence_id: 'Corr-000004', related_transaction_id: MY_TX, correspondence_type: 'A' },
      { correspondence_id: 'Corr-000005', related_transaction_id: MY_TX, correspondence_type: 'B' },
    ]

    const out = collapseMiLinkChildRowsToOnePerParticipant(rows, cfg)

    expect(out).toHaveLength(2)
    expect(out.map(r => r.correspondence_id).sort()).toEqual(['Corr-000004', 'Corr-000005'])
    // 且字段不得互相污染（合并会把两行的字段缝进同一行）
    const byId = new Map(out.map(r => [r.correspondence_id, r]))
    expect(byId.get('Corr-000004')?.correspondence_type).toBe('A')
    expect(byId.get('Corr-000005')?.correspondence_type).toBe('B')
  })

  it('uuid 主键（People）的兄弟行同样保留 —— 既有行为不回归', () => {
    const cfg = miChildFkConfigOfBinding(PEOPLE_BINDING as never, 50331)
    const rows = [
      { idqcxma: '7692f137-0e2c-41ad-972e-9f842778d67c', sub_task_id: 'Test-000017', age: '1' },
      { idqcxma: 'c3f1a2b4-5d6e-4f70-8912-3a4b5c6d7e8f', sub_task_id: 'Test-000017', age: '2' },
    ]

    const out = collapseMiLinkChildRowsToOnePerParticipant(rows, cfg)

    expect(out).toHaveLength(2)
  })

  it('真正的碎片（同参与者、主键为空）仍然合并 —— 折叠本来的用途不被破坏', () => {
    const cfg = miChildFkConfigOfBinding(CORRESPONDENCE_BINDING as never, 391)
    const rows = [
      { related_transaction_id: MY_TX, correspondence_type: 'A' },
      { related_transaction_id: MY_TX, correspondence_mode: 'M' },
    ]

    const out = collapseMiLinkChildRowsToOnePerParticipant(rows, cfg)

    expect(out).toHaveLength(1)
    expect(out[0].correspondence_type).toBe('A')
    expect(out[0].correspondence_mode).toBe('M')
  })

  it('不同参与者的行互不影响', () => {
    const cfg = miChildFkConfigOfBinding(CORRESPONDENCE_BINDING as never, 391)
    const rows = [
      { correspondence_id: 'Corr-000003', related_transaction_id: 'ATM-DC-PW-TRANS-000003' },
      { correspondence_id: 'Corr-000004', related_transaction_id: MY_TX },
    ]

    const out = collapseMiLinkChildRowsToOnePerParticipant(rows, cfg)

    expect(out).toHaveLength(2)
  })
})
