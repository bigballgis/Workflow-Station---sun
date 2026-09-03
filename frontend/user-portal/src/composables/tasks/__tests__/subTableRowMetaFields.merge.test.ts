import { describe, expect, it } from 'vitest'
import {
  enrichChildBindingRowsFromParentsNestedSubTables,
  finalizeSharedProcessSubTableBindingRows,
  mergeSubTableSlicesForRelationTableId,
  collectSubTableSliceRowsForRelationTableId,
  miLinkChildRowBelongsToParticipant,
  mergeSubTableRowsByRowId,
  mergeAllSlicesForSharedProcessSubTableBinding,
  buildBindingIdToRelationTableIdMap,
  applySharedAttachmentFinalizeAndMaterialize,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  resolveSubTableRowsForBinding,
  miChildFkConfigOfBinding,
} from '../shared'

/** People 表的设计器 FK 配置：结构外键列名从这里解析（不再有列名清单兜底）。 */
const PEOPLE_FK_CFG = miChildFkConfigOfBinding({
  fieldDefinitions: [
    { fieldName: 'id', isPrimaryKey: true },
    { fieldName: 'sub_task_id', isForeignKey: true },
  ],
} as never)

describe('subTableRowMetaFields merge/materialize/hydrate', () => {
  it('mergeSubTableSlicesForRelationTableId merges only same tableId slices', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 103, tableId: 74 }, { bindingId: 66, tableId: 20 }] },
    ])
    const subTables = {
      '103': [{ id: 1, file: 'a.pdf' }],
      '66': [{ id: 343, name: '3', id_idw: 343, task_status: 'IN_PROGRESS' }],
      attachment: [{ id: 2, file: 'b.pdf' }],
    }
    const merged = mergeSubTableSlicesForRelationTableId(subTables, 74, rtMap, ['id'], 'attachment')
    expect(merged).toHaveLength(2)
    expect(merged).toEqual(expect.arrayContaining([
      { id: 1, file: 'a.pdf' },
      { id: 2, file: 'b.pdf' },
    ]))
  })

  it('mergeAllSlicesForSharedProcessSubTableBinding collects nested-only attachment rows and sibling binding 103', () => {
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 103, tableId: 74 }, { bindingId: 104, tableId: 74 }] },
    ])
    const attachmentBinding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      primaryKeyFields: ['id'],
    }
    const subTables = {
      '66': [
        {
          id: 343,
          name: '3',
          id_idw: 343,
          task_status: 'IN_PROGRESS',
          __subTables__: {
            '103': [{ id: 666, main_id: '', file: '/api/v1/upload/files/x.pdf?originalName=a.pdf' }],
            attachment: [{ id: 667, main_id: '', file: '/api/v1/upload/files/y.pdf?originalName=b.pdf' }],
          },
        },
      ],
    }
    const merged = mergeAllSlicesForSharedProcessSubTableBinding(subTables, attachmentBinding, rtMap)
    expect(merged).toHaveLength(2)
    expect(merged).toEqual(expect.arrayContaining([
      { id: 666, main_id: '', file: '/api/v1/upload/files/x.pdf?originalName=a.pdf' },
      { id: 667, main_id: '', file: '/api/v1/upload/files/y.pdf?originalName=b.pdf' },
    ]))
  })

  it('applySharedAttachmentFinalizeAndMaterialize does not project primary fileupload into attachment rows', () => {
    const binding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      foreignKeyField: 'main_id',
      // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'main_id', isForeignKey: true },
      ],
      columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
      primaryKeyFields: ['id'],
      data: [] as any[],
    }
    applySharedAttachmentFinalizeAndMaterialize(
      [binding],
      {
        id: 343,
        fileupload: '/api/v1/upload/files/99029219.pdf?originalName=invoice.pdf',
      },
    )
    expect(binding.data).toEqual([])
  })

  it('applySharedAttachmentFinalizeAndMaterialize keeps existing attachment rows when primary fileupload is set', () => {
    const binding = {
      bindingId: 104,
      tableId: 74,
      tableName: 'attachment',
      foreignKeyField: 'main_id',
      // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
      fieldDefinitions: [
        { fieldName: 'id', isPrimaryKey: true },
        { fieldName: 'main_id', isForeignKey: true },
      ],
      columns: [{ field: 'file' }],
      primaryKeyFields: ['id'],
      data: [{ id: 1, main_id: '19', file: '/api/v1/upload/files/a.pdf' }],
    }
    applySharedAttachmentFinalizeAndMaterialize(
      [binding],
      { fileupload: '/api/v1/upload/files/b.pdf?originalName=other.pdf' },
    )
    expect(binding.data).toEqual([{ id: 1, main_id: '19', file: '/api/v1/upload/files/a.pdf' }])
  })

  it('applySharedAttachmentFinalizeAndMaterialize drops MI rows without projecting primary fileupload', () => {
    const bindings = [
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [{ id: 343, name: '3', id_idw: 343, task_status: 'IN_PROGRESS' }],
      },
    ]
    applySharedAttachmentFinalizeAndMaterialize(bindings, {
      id: 343,
      fileupload: '/api/v1/upload/files/x.pdf?originalName=a.pdf',
    })
    expect(bindings[0]!.data).toEqual([])
  })

  it('applySharedAttachmentFinalizeAndMaterialize replaces thin per-binding slice with full shared merge', () => {
    const rtMap = new Map<number, number | null>([
      [66, 20],
      [103, 74],
      [104, 74],
    ])
    const flat = {
      66: [{ id: 666, name: '66', task_status: 'COMPLETED' }],
      103: [
        { id: '77777', file: '/api/v1/upload/files/a.pdf', main_id: '' },
        { id: 666, file: '/api/v1/upload/files/b.pdf', main_id: '', name: '66' },
        { id: 633, file: '/api/v1/upload/files/c.pdf', main_id: '' },
      ],
      104: [
        { id: 666, file: '/api/v1/upload/files/b.pdf', main_id: '', name: '66' },
        { id: 666, file: '/api/v1/upload/files/d.pdf', main_id: '' },
      ],
    }
    const bindings = [
      {
        bindingId: 103,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [],
      },
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
        columns: [{ field: 'id' }, { field: 'main_id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [{ id: 666, file: '/api/v1/upload/files/d.pdf', main_id: '' }],
      },
    ]
    applySharedAttachmentFinalizeAndMaterialize(bindings, {}, {
      flattened: flat,
      bindingTableById: rtMap,
    })
    expect(bindings[1]!.data).toHaveLength(2)
    expect(bindings[1]!.data.map((r: { id: unknown }) => String(r.id)).sort()).toEqual(['633', '77777'])
  })

  it('enrichChildBindingRowsFromParentsNestedSubTables does not overwrite shared attachment binding rows', () => {
    const bindings = [
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        physicalTableName: 'attachment',
        foreignKeyField: 'main_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
        columns: [{ field: 'id' }, { field: 'file' }],
        primaryKeyFields: ['id'],
        data: [
          { id: '343', file: '/a.pdf', main_id: '' },
          { id: '633', file: '/b.pdf', main_id: '' },
        ],
      },
      {
        bindingId: 69,
        tableId: 20,
        tableName: 'subtable',
        columns: [{ field: 'name' }],
        data: [],
      },
    ]
    enrichChildBindingRowsFromParentsNestedSubTables(bindings)
    expect(bindings[0]!.data.map((r: { id: unknown }) => String(r.id)).sort()).toEqual(['343', '633'])
  })

  it('hydrateBindingsRowsFromVariablesBySharedRelationTableId does not merge sole unclaimed slice from another relation table (HMDC)', () => {
    const rtMap = new Map<number, number | null>([
      [271, 112],
      [273, 114],
    ])
    const saved = {
      271: [
        {
          row_id: '455656',
          card_number: '',
          arn: '',
        },
      ],
      273: [{ file: '/api/v1/upload/files/test.jpg' }],
    }
    const bindings = [
      {
        bindingId: 271,
        tableId: 112,
        tableName: 'HMDC Transaction',
        physicalTableName: 'HMDC_Transaction',
        columns: [{ field: 'row_id' }, { field: 'card_number' }],
        primaryKeyFields: ['row_id'],
        data: [...saved[271]!],
      },
      {
        bindingId: 273,
        tableId: 114,
        tableName: 'HMDC Attachment',
        physicalTableName: 'HMDC_Attachment',
        columns: [{ field: 'file' }],
        data: [...saved[273]!],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings, saved, rtMap)
    expect(bindings[0]!.data).toHaveLength(1)
    expect(bindings[0]!.data[0]!.row_id).toBe('455656')
    expect(bindings[0]!.data[0]!.file).toBeUndefined()
  })

  it('collectSubTableSliceRowsForRelationTableId returns RAW rows (no collapse-by-id) across same-tableId slices', () => {
    const rtMap = new Map<number, number | null>([
      [30, 20],
      [63, 20],
      [64, 99], // different relation table — must be excluded
    ])
    const saved = {
      // current form People slice — current participant Test-000058 row (age ii66)
      30: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' }],
      // previous form People slice — OTHER participant Test-000057 row that happens to SHARE id
      63: [{ id: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii' }],
      // Sub Task collection (different tableId) — must NOT be pulled into People
      64: [{ id: 'Test-000058', sub_task_id: 'Test-000057', name: '222' }],
    }
    const rows = collectSubTableSliceRowsForRelationTableId(saved, 20, rtMap, 'People', 'people')
    // Both colliding People rows preserved RAW (not collapsed by id), Sub Task slice excluded.
    expect(rows).toHaveLength(2)
    expect(rows.some(r => r.sub_task_id === 'Test-000058' && r.age === 'ii66')).toBe(true)
    expect(rows.some(r => r.sub_task_id === 'Test-000057' && r.age === 'ii')).toBe(true)
    expect(rows.some(r => r.name === '222')).toBe(false)
  })

  it('participant filter BEFORE merge preserves current participant People against PK-colliding other participant', () => {
    const myRowId = 'Test-000058'
    const rtMap = new Map<number, number | null>([
      [30, 20],
      [63, 20],
    ])
    const saved = {
      30: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66', sex: true }],
      63: [{ id: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii', sex: true }],
    }
    // RAW collect then filter to current participant, then merge — the correct order.
    const raw = collectSubTableSliceRowsForRelationTableId(saved, 20, rtMap, 'People')
    const scoped = raw.filter(r => miLinkChildRowBelongsToParticipant(r, myRowId, PEOPLE_FK_CFG))
    const merged = mergeSubTableRowsByRowId([], scoped, ['id'])
    expect(merged).toHaveLength(1)
    expect(merged[0]!.sub_task_id).toBe('Test-000058')
    expect(merged[0]!.age).toBe('ii66')

    // CONTRAST: collapsing by id BEFORE the filter loses the current participant's row (the bug).
    const collapsedFirst = mergeSubTableSlicesForRelationTableId(saved, 20, rtMap, ['id'], 'People')
    const wronglyScoped = collapsedFirst.filter(r => miLinkChildRowBelongsToParticipant(r, myRowId, PEOPLE_FK_CFG))
    expect(wronglyScoped).toHaveLength(0)
  })

  it('hydrateBindingsRowsFromVariablesBySharedRelationTableId excludeBinding skips participant-scoped People (no cross-participant re-merge)', () => {
    const rtMap = new Map<number, number | null>([
      [30, 20],
      [63, 20],
    ])
    const saved = {
      30: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' }],
      63: [{ id: 'Test-000058', sub_task_id: 'Test-000057', age: 'ii' }],
    }
    const bindings = [
      {
        bindingId: 30,
        tableId: 20,
        tableName: 'People',
        physicalTableName: 'people',
        foreignKeyField: 'sub_task_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'sub_task_id', isForeignKey: true },
        ],
        primaryKeyFields: ['id'],
        columns: [{ field: 'id' }, { field: 'sub_task_id' }, { field: 'age' }],
        data: [{ id: 'Test-000058', sub_task_id: 'Test-000058', age: 'ii66' }],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings as any, saved, rtMap, {
      excludeBinding: () => true,
    })
    // Excluded — data left untouched, NOT polluted by binding 63's other-participant row.
    expect(bindings[0]!.data).toHaveLength(1)
    expect((bindings[0]!.data[0] as any).sub_task_id).toBe('Test-000058')
    expect((bindings[0]!.data[0] as any).age).toBe('ii66')
  })

  it('hydrateBindingsRowsFromVariablesBySharedRelationTableId skips shared attachment (no per-binding split)', () => {
    const rtMap = new Map<number, number | null>([
      [103, 74],
      [104, 74],
    ])
    const saved = {
      103: [
        { id: '77777', file: '/api/v1/upload/files/a.pdf' },
        { id: 633, file: '/api/v1/upload/files/c.pdf' },
      ],
      104: [{ id: 666, file: '/api/v1/upload/files/b.pdf' }],
    }
    const bindings = [
      {
        bindingId: 103,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
        columns: [{ field: 'file' }],
        data: [],
      },
      {
        bindingId: 104,
        tableId: 74,
        tableName: 'attachment',
        foreignKeyField: 'main_id',
        // 真实 binding 都带 fieldDefinitions；FK 列名从这里解析，不再猜列名。
        fieldDefinitions: [
          { fieldName: 'id', isPrimaryKey: true },
          { fieldName: 'main_id', isForeignKey: true },
          { fieldName: 'file', dataType: 'FILE' },
        ],
        columns: [{ field: 'file' }],
        data: [],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings, saved, rtMap)
    expect(bindings[0]!.data).toHaveLength(0)
    expect(bindings[1]!.data).toHaveLength(0)
  })

  /**
   * Regression: My Request "Sub task"'s Participants Details dialog showed a stale `name` even
   * though the binding's own data (already correctly hydrated moments earlier by
   * hydrateChildSubTablesFromParentsNestedRows, from the clicked row's own nested __subTables__)
   * had the fresh value. Root cause: this function's "not multiPlacementSameTid" branch collects
   * every OTHER same-table_id binding's raw slice into `chunks` with no self-ownership awareness,
   * then merges `chunks` on top of `existing` (existing, chunks) — the peer's stale copy always won,
   * clobbering the already-correct existing data. A chunk row is only trustworthy over existing when
   * it carries a structural self-reference FK (sub_task_id === its own PK); a non-self-owned chunk
   * row must still lose to non-empty existing data (that's this function's real gap-filling case).
   */
  it('existing data already correct is preserved when peer chunks are not self-owned, but a self-owned peer chunk still wins', () => {
    const rtMap = new Map<number, number | null>([
      [50539, 50331],
      [50544, 50331],
      [50617, 50331],
    ])
    const saved = {
      50539: [{ id_idw: 'Test-000002', name: 'ss', main_id: 'Meeting-000001' }],
      50544: [{ id_idw: 'Test-000002', name: 'ss', main_id: 'Meeting-000001', sub_task_id: 'Test-000002' }],
    }
    // Binding 50617 (REQUEST scene) has no own __subTables__ key, but hydrateChildSubTablesFromParentsNestedRows
    // already correctly populated its `data` with the clicked row's own fresh nested value moments earlier.
    const bindings = [
      {
        bindingId: 50617,
        tableId: 50331,
        tableName: 'Participants',
        primaryKeyFields: ['id_idw'],
        // 自持有标记（sub_task_id === 自己的 PK）要能被识别，该列必须是设计器声明的外键。
        fieldDefinitions: [
          { fieldName: 'id_idw', isPrimaryKey: true },
          { fieldName: 'sub_task_id', isForeignKey: true },
        ],
        columns: [{ field: 'id_idw' }, { field: 'name' }, { field: 'main_id' }],
        data: [{ id_idw: 'Test-000002', name: 'ssa', main_id: 'Meeting-000001' }],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings as any, saved, rtMap)
    const row = (bindings[0]!.data as any[]).find(r => r.id_idw === 'Test-000002')
    // The self-owned peer (50544, stamped sub_task_id) still wins over existing when both diverge —
    // it reflects the row's true current value, which in this fixture also happens to read "ss".
    expect(row.name).toBe('ss')
  })

  it('existing data wins over a non-self-owned peer chunk when no peer is self-owned', () => {
    const rtMap = new Map<number, number | null>([
      [50539, 50331],
      [50617, 50331],
    ])
    const saved = {
      50539: [{ id_idw: 'Test-000002', name: 'ss', main_id: 'Meeting-000001' }],
    }
    const bindings = [
      {
        bindingId: 50617,
        tableId: 50331,
        tableName: 'Participants',
        primaryKeyFields: ['id_idw'],
        // 自持有标记（sub_task_id === 自己的 PK）要能被识别，该列必须是设计器声明的外键。
        fieldDefinitions: [
          { fieldName: 'id_idw', isPrimaryKey: true },
          { fieldName: 'sub_task_id', isForeignKey: true },
        ],
        columns: [{ field: 'id_idw' }, { field: 'name' }, { field: 'main_id' }],
        data: [{ id_idw: 'Test-000002', name: 'ssa', main_id: 'Meeting-000001' }],
      },
    ]
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings as any, saved, rtMap)
    const row = (bindings[0]!.data as any[]).find(r => r.id_idw === 'Test-000002')
    expect(row.name).toBe('ssa')
  })

  it('resolveSubTableRowsForBinding merges initiator slice 64 assignee into assignment binding 66', () => {
    const saved = {
      '64': [
        {
          name: '1111',
          id_idw: 'Test-000057',
          main_id: 'f0e6956a-6982-4448-ae1c-db7971a37990',
          assignee: { id: 'user-dev', display_name: 'Developer Tester' },
          assignee_display_name: 'Developer Tester',
        },
      ],
      '66': [
        {
          name: '1111',
          id_idw: 'Test-000057',
          main_id: 'f0e6956a-6982-4448-ae1c-db7971a37990',
        },
      ],
    }
    const rtMap = buildBindingIdToRelationTableIdMap([
      { tableBindings: [{ bindingId: 64, tableId: 20 }, { bindingId: 66, tableId: 20 }] },
    ])
    const binding = {
      bindingId: 66,
      tableId: 20,
      tableName: 'Sub Task',
      columns: [
        { field: 'main_id' },
        { field: 'id_idw' },
        { field: 'name' },
        { field: 'assignee' },
      ],
    }
    const rows = resolveSubTableRowsForBinding(saved, binding, {
      bindingTableById: rtMap,
      mergeSiblingSlices: true,
    })
    expect(rows).toHaveLength(1)
    expect(rows![0].assignee).toEqual({ id: 'user-dev', display_name: 'Developer Tester' })
    expect(rows![0].assignee_display_name).toBe('Developer Tester')
  })

  /**
   * Regression: To Do task detail's Participants "Name" field showed a stale value after the
   * owning MI sub-task saved an edit. Assign Task (50539) / Sub task (50544) / Main (50627) all
   * share the same Participants table_id; binding 50544's own slice (found first, by exact
   * bindingId key) already carries the fresh `name` and a self-owning `sub_task_id` FK, but
   * mergeSameTableIdNumericSlicesInto used to fold sibling slices (50539's stale copy) into it
   * unconditionally — the later argument always wins for non-empty fields — clobbering the fresh
   * value. The self-owned row must survive being merged with any number of stale siblings.
   */
  it('resolveSubTableRowsForBinding keeps the self-owned row\'s field over a stale sibling for the same PK', () => {
    const saved = {
      '50539': [{ id_idw: 'Test-000002', name: 'ss', main_id: 'Meeting-000001' }],
      '50544': [{ id_idw: 'Test-000002', name: 'ssa', main_id: 'Meeting-000001', sub_task_id: 'Test-000002' }],
      '50627': [{ id_idw: 'Test-000002', name: 'ss', main_id: 'Meeting-000001' }],
    }
    const rtMap = buildBindingIdToRelationTableIdMap([
      {
        tableBindings: [
          { bindingId: 50539, tableId: 50331 },
          { bindingId: 50544, tableId: 50331 },
          { bindingId: 50627, tableId: 50331 },
        ],
      },
    ])
    const binding = {
      bindingId: 50544,
      tableId: 50331,
      tableName: 'Participants',
      primaryKeyFields: ['id_idw'],
      // 自持有标记（sub_task_id === 自己的 PK）要能被识别，该列必须是设计器声明的外键。
      fieldDefinitions: [
        { fieldName: 'id_idw', isPrimaryKey: true },
        { fieldName: 'sub_task_id', isForeignKey: true },
      ],
      columns: [{ field: 'id_idw' }, { field: 'name' }, { field: 'main_id' }],
    }
    const rows = resolveSubTableRowsForBinding(saved, binding, {
      bindingTableById: rtMap,
      mergeSiblingSlices: true,
    })
    expect(rows).toHaveLength(1)
    expect(rows![0].name).toBe('ssa')
  })

  it('finalizeSharedProcessSubTableBindingRows preserves assignee on MI dashboard bindings', () => {
    const rows = [
      {
        id_idw: 'Test-000057',
        assignee: { id: 'user-dev', display_name: 'Developer Tester' },
        assignee_display_name: 'Developer Tester',
        task_status: 'PENDING',
      },
    ]
    const out = finalizeSharedProcessSubTableBindingRows(rows, {
      tableName: 'Sub Task',
      // MI collection 靠设计器 Link Mode 声明，不再靠 assignee/task_status 列名猜。
      bindingLinkMode: 'miParticipantRow',
      columns: [{ field: 'assignee' }, { field: 'task_status' }],
    })
    expect(out[0].assignee).toEqual({ id: 'user-dev', display_name: 'Developer Tester' })
    expect(out[0].assignee_display_name).toBe('Developer Tester')
    expect(out[0].task_status).toBe('PENDING')
  })
})
