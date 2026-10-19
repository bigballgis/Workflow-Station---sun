import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { createTaskDetailMiPersist } from '../useTaskDetailMiPersist'
import type { TaskDetailCtx } from '../context'

describe('MI scalar persistence keeps configured participant identity', () => {
  it('never copies a child editor scalar onto participant PK/FK columns', async () => {
    const own = { participant_key: 'one', case_ref: 'case-one', caption: 'before' }
    const other = { participant_key: 'two', case_ref: 'case-one', caption: 'other' }
    const binding = { bindingId: 42, tableName: 'participants', bindingLinkMode: 'miParticipantRow',
      primaryKeyFields: ['participant_key'], fieldDefinitions: [
        { fieldName: 'participant_key', isPrimaryKey: true },
        { fieldName: 'case_ref', isForeignKey: true, refTableId: 10 },
      ], data: [own, other] }
    const ctx = { subTableBindings: ref([binding]), miSubProcessScope: ref({ subTableName: 'participants' }),
      miFullSubTablesSnapshotRef: ref(null), primaryTableFieldNames: ref(new Set(['case_key'])),
      currentMiRowId: ref('one'), taskForm: { formData: ref({ participant_key: 'forged-key', case_ref: 'child-parent', caption: 'edited' }),
        getCurrentFormFieldKeys: () => new Set(['participant_key', 'case_ref', 'caption']) },
      rowBelongsToCurrentMiScope: (row: Record<string, unknown>) => row.participant_key === 'one',
      miCollectionPrimaryKeyFields: () => ['participant_key'],
      seedMiParticipantScopedBindingForeignKeys: async () => {}, patchFormDataSubTablesFromCurrentBindings: () => {},
    } as unknown as TaskDetailCtx
    await createTaskDetailMiPersist(ctx).mergeMiParticipantScalarsFromForm()
    expect(own).toEqual({ participant_key: 'one', case_ref: 'case-one', caption: 'edited' })
    expect(other).toEqual({ participant_key: 'two', case_ref: 'case-one', caption: 'other' })
  })
})
