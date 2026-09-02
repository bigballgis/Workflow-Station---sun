import { describe, expect, it } from 'vitest'
import {
  bindingDeclaresMiParticipantRow,
  resolveMiBindingKindFromConfig,
} from '../miBindingKindFromConfig'
import {
  isMiDashboardSubTableBinding,
  isMiParticipantScopedSubTableBinding,
} from '../subTableBindingKinds'

/**
 * Binding 分类的配置判据，用 FU 50005「Multi-Instance Subtask Demo」的**现场真实配置**锁定。
 *
 * <p>关键回归：attachment 与 people 的 bindingLinkMode **都是** structuralFk，
 * 唯一区分它们的是字段级 FK 的 refTableId 指向谁：
 * <pre>
 *   attachment  FK main_idv     -> 50332 main      => shared
 *   people      FK sub_task_idq -> 50331 subtable  => participant-child
 * </pre>
 * 把 shared 当成「其余情况」的 else 兜底会让 FK 元数据缺失的 child 静默降级为 shared，
 * 造成跨参与者串数据 —— 所以 shared 必须是正面判据，判不了要返回 null。
 */

const MAIN_TID = 50332
const COLLECTION_TID = 50331

const CTX = { miCollectionTableId: COLLECTION_TID, primaryTableId: MAIN_TID }

/** Participants —— 设计器 Link Mode = MI Participant Row。 */
const collectionBinding = {
  tableId: COLLECTION_TID,
  bindingLinkMode: 'miParticipantRow',
  foreignKeyField: 'id_idwxw',
  fieldDefinitions: [
    { fieldName: 'id_idwxw', isPrimaryKey: true },
    { fieldName: 'main_idaa', isForeignKey: true, refTableId: MAIN_TID },
  ],
}

/** Attachment —— structuralFk，FK 指向主表。 */
const attachmentBinding = {
  tableId: 50330,
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'main_idv',
  fieldDefinitions: [
    { fieldName: 'idf', isPrimaryKey: true },
    { fieldName: 'main_idv', isForeignKey: true, refTableId: MAIN_TID },
  ],
}

/** People —— structuralFk，但 FK 指向 collection。 */
const peopleBinding = {
  tableId: 50333,
  bindingLinkMode: 'structuralFk',
  foreignKeyField: 'idq',
  fieldDefinitions: [
    { fieldName: 'idq', isPrimaryKey: true },
    { fieldName: 'sub_task_idq', isForeignKey: true, refTableId: COLLECTION_TID },
  ],
}

describe('resolveMiBindingKindFromConfig —— FU 50005 现场配置', () => {
  it('Participants: bindingLinkMode=miParticipantRow => collection', () => {
    expect(resolveMiBindingKindFromConfig(collectionBinding, CTX)).toBe('collection')
    expect(bindingDeclaresMiParticipantRow(collectionBinding)).toBe(true)
  })

  it('Attachment: FK 指向主表 => shared（尽管 linkMode 与 people 相同）', () => {
    expect(resolveMiBindingKindFromConfig(attachmentBinding, CTX)).toBe('shared')
  })

  it('People: FK 指向 collection => participant-child（尽管 linkMode 与 attachment 相同）', () => {
    expect(resolveMiBindingKindFromConfig(peopleBinding, CTX)).toBe('participant-child')
  })

  it('改名不影响判定 —— 列名换成任意值仍按 refTableId 判', () => {
    const renamed = {
      ...peopleBinding,
      fieldDefinitions: [
        { fieldName: 'zzz_pk', isPrimaryKey: true },
        { fieldName: 'whatever_ref', isForeignKey: true, refTableId: COLLECTION_TID },
      ],
    }
    expect(resolveMiBindingKindFromConfig(renamed, CTX)).toBe('participant-child')
  })

  it('多个 FK 时只认指向 collection 的那个', () => {
    const twoFk = {
      tableId: 50333,
      bindingLinkMode: 'structuralFk',
      fieldDefinitions: [
        { fieldName: 'lookup_ref', isForeignKey: true, refTableId: 99999 },
        { fieldName: 'sub_task_idq', isForeignKey: true, refTableId: COLLECTION_TID },
      ],
    }
    expect(resolveMiBindingKindFromConfig(twoFk, CTX)).toBe('participant-child')
  })

  it('同一张 collection 表的另一个 binding 也判为 collection', () => {
    const peerOnCollectionTable = {
      tableId: COLLECTION_TID,
      bindingLinkMode: 'structuralFk',
      fieldDefinitions: [{ fieldName: 'id_idwxw', isPrimaryKey: true }],
    }
    expect(resolveMiBindingKindFromConfig(peerOnCollectionTable, CTX)).toBe('collection')
  })

  it('配置不足时返回 null —— 不猜', () => {
    expect(resolveMiBindingKindFromConfig({ tableId: 1 }, CTX)).toBeNull()
    expect(resolveMiBindingKindFromConfig({ tableId: 1, fieldDefinitions: [] }, CTX)).toBeNull()
    expect(resolveMiBindingKindFromConfig(null, CTX)).toBeNull()
  })

  it('无 collection 但 FK 指向主表 => 仍可正面判定为 shared', () => {
    expect(resolveMiBindingKindFromConfig(attachmentBinding, { primaryTableId: MAIN_TID })).toBe('shared')
  })

  /**
   * 关键安全不变量：FK 只标了 isForeignKey、没有 refTableId（存量 binding 常见）时，
   * 必须返回 null 而不是 'shared'。判成 shared 会让一个真 child 失去参与者隔离，
   * 跨子任务串数据 —— 比判不出来严重得多。
   */
  it('FK 无 refTableId 时返回 null，绝不静默降级成 shared', () => {
    const fkWithoutRef = {
      tableId: 50333,
      bindingLinkMode: 'structuralFk',
      foreignKeyField: 'id',
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'sub_task_id', isForeignKey: true },
      ],
    }
    expect(resolveMiBindingKindFromConfig(fkWithoutRef, CTX)).toBeNull()
    expect(resolveMiBindingKindFromConfig(fkWithoutRef, {})).toBeNull()
  })
})

describe('谓词接上配置判据后的行为', () => {
  it('Participants 被认成 MI dashboard（靠 linkMode，不靠列名/表名）', () => {
    expect(isMiDashboardSubTableBinding(collectionBinding)).toBe(true)
  })

  it('Attachment 不是 participant-scoped（FK 指向主表）', () => {
    expect(isMiParticipantScopedSubTableBinding(attachmentBinding, CTX)).toBe(false)
  })

  it('People 是 participant-scoped（FK 指向 collection）', () => {
    expect(isMiParticipantScopedSubTableBinding(peopleBinding, CTX)).toBe(true)
  })

  /**
   * 原始 bug：真 collection 的 foreignKeyField 是通用的 `id`，列名启发式据此判成
   * participant-scoped 且非 dashboard => link-child，于是去找不存在的参与者 FK。
   * 现在 linkMode 权威声明它是 collection，dashboard 判定直接为真。
   */
  it('回归：fk=id 的真 collection 不再被当成 link-child', () => {
    const trapped = {
      tableId: COLLECTION_TID,
      bindingLinkMode: 'miParticipantRow',
      foreignKeyField: 'id',
      columns: [{ field: 'amount' }, { field: 'card_number' }],
      fieldDefinitions: [{ fieldName: 'row_id', isPrimaryKey: true }],
    }
    expect(isMiDashboardSubTableBinding(trapped)).toBe(true)
    // scoped && !dashboard === false  =>  不会进 link-child 分支
    expect(isMiParticipantScopedSubTableBinding(trapped, CTX) && !isMiDashboardSubTableBinding(trapped)).toBe(false)
  })

  /** 旧启发式已删除：没有配置就判不出 participant-child，一律 false（安全侧）。 */
  it('无配置时不再猜列名，一律 false', () => {
    expect(isMiParticipantScopedSubTableBinding({ foreignKeyField: 'main_id' })).toBe(false)
    expect(isMiParticipantScopedSubTableBinding({ foreignKeyField: 'id_idw' })).toBe(false)
    expect(isMiParticipantScopedSubTableBinding({ foreignKeyField: 'id' })).toBe(false)
  })
})
