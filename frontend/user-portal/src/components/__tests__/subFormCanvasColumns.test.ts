import { describe, it, expect } from 'vitest'
import {
  enrichLookupColumnPropsFromSubFormRule,
  flattenSubFormRuleLayoutContainers,
  resolveSubFormDialogColumnsForBinding,
  resolveSubFormRuleForBinding,
} from '../subTableAddDialogHelpers'

describe('resolveSubFormDialogColumnsForBinding', () => {
  const ctx = { lookupDbConfigs: {}, relationViewConfigs: {} }

  it('returns canvas fields only — excludes list-view-only audit columns', () => {
    const binding = { bindingId: 42 }
    const subForms = {
      42: {
        rule: [
          { type: 'input', field: 'id', title: 'id' },
          { type: 'input', field: 'main_id', title: 'main_id' },
          { type: 'input', field: 'testinfo', title: 'testinfo' },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['id', 'main_id', 'testinfo'])
    expect(dialogCols.some(c => c.field === 'created_at' || c.field === 'updated_at')).toBe(false)
  })

  it('prefers subFormConfig.rule on binding when present', () => {
    const binding = {
      bindingId: 7,
      subFormConfig: {
        rule: [{ type: 'input', field: 'only_canvas', title: 'Only Canvas' }],
      },
    }
    const subForms = {
      7: { rule: [{ type: 'input', field: 'from_config', title: 'From Config' }] },
    }
    expect(resolveSubFormRuleForBinding(binding, subForms)?.[0]).toMatchObject({ field: 'only_canvas' })
  })

  it('returns empty when no sub-form rule exists', () => {
    expect(resolveSubFormDialogColumnsForBinding({ bindingId: 1 }, {}, ctx)).toEqual([])
  })

  it('skips nested subTable / linkForm placeholders and fieldless layout rules', () => {
    const binding = { bindingId: 50113 }
    const subForms = {
      50113: {
        rule: [
          { type: 'input', field: 'shipment_name', title: 'Shipment Name' },
          { type: 'input', field: 'carrier', title: 'Carrier' },
          // Nested sub-table widget — no `field`, title used to leak into the dialog as a text input
          { type: 'subTable', title: 'Sub-Table', _bindingId: 50114, props: {} },
          { type: 'linkForm', title: 'Link Form', props: {} },
          { type: 'elCard', props: {}, children: [] },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['shipment_name', 'carrier'])
    expect(dialogCols.some(c => c.label === 'Sub-Table')).toBe(false)
  })

  it('keeps unknown field-bearing types (SubTableAddDialog passthrough contract)', () => {
    const subForms = {
      9: { rule: [{ type: 'someCustomWidget', field: 'custom_field', title: 'Custom' }] },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding({ bindingId: 9 }, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['custom_field'])
  })

  it('attaches sourceRule so dialog can run component events', () => {
    const subForms = {
      3: {
        rule: [
          {
            type: 'select',
            field: 'case_type',
            title: 'Case Type',
            options: [{ label: 'CNP', value: 1 }],
            _on: { blur: '$FNX:\n$inject.api.setValue("card_number", "111")' },
          },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding({ bindingId: 3 }, subForms, ctx)
    expect(dialogCols[0].sourceRule).toMatchObject({ field: 'case_type', type: 'select' })
    expect((dialogCols[0].sourceRule as Record<string, unknown>)._on).toBeDefined()
  })

  it('includes fields nested inside a Card layout container (FU 50013 regression)', () => {
    const binding = { bindingId: 50113 }
    const subForms = {
      50113: {
        rule: [
          { type: 'subTable', title: 'Sub-Table', _bindingId: 50114, props: {} },
          {
            type: 'elCard',
            props: { header: 'Shipment Info' },
            children: [
              { type: 'input', field: 'shipment_name', title: 'Shipment Name' },
              { type: 'input', field: 'carrier', title: 'Carrier' },
            ],
          },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols.map(c => c.field)).toEqual(['shipment_name', 'carrier'])
    expect(dialogCols.map(c => c.label)).toEqual(['Shipment Name', 'Carrier'])
  })

  it('maps Form Design len validate onto dialog column.rules (sub-form Add Record)', () => {
    const binding = { bindingId: 88 }
    const subForms = {
      88: {
        rule: [
          {
            type: 'input',
            field: 'card_number',
            title: 'Card Number',
            validate: [{ mode: 'len', len: 1, adapter: true, trigger: 'blur' }],
          },
        ],
      },
    }
    const dialogCols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(dialogCols).toHaveLength(1)
    expect(dialogCols[0].required).toBe(false)
    expect(dialogCols[0].rules).toEqual([
      { len: 1, trigger: 'blur' },
    ])
  })
})

describe('flattenSubFormRuleLayoutContainers', () => {
  it('expands card/row/col containers recursively, preserving document order', () => {
    const rule = [
      { type: 'input', field: 'a', title: 'A' },
      {
        type: 'el-row',
        children: [
          { type: 'el-col', children: [{ type: 'input', field: 'b', title: 'B' }] },
          {
            type: 'elCard',
            children: [{ type: 'select', field: 'c', title: 'C' }],
          },
        ],
      },
      { type: 'input', field: 'd', title: 'D' },
    ]
    const flat = flattenSubFormRuleLayoutContainers(rule) as Array<{ field?: string }>
    expect(flat.map(r => r.field)).toEqual(['a', 'b', 'c', 'd'])
  })

  it('reads children from props.children as well', () => {
    const rule = [
      { type: 'card', props: { children: [{ type: 'input', field: 'x', title: 'X' }] } },
    ]
    const flat = flattenSubFormRuleLayoutContainers(rule) as Array<{ field?: string }>
    expect(flat.map(r => r.field)).toEqual(['x'])
  })

  it('leaves placeholders and field-bearing rules untouched', () => {
    const subTable = { type: 'subTable', title: 'Sub-Table', props: {} }
    const fieldGroup = { type: 'group', field: 'g', title: 'Repeat Group' }
    const flat = flattenSubFormRuleLayoutContainers([subTable, fieldGroup])
    expect(flat[0]).toBe(subTable)
    expect(flat[1]).toBe(fieldGroup)
  })

  it('returns [] for non-array input', () => {
    expect(flattenSubFormRuleLayoutContainers(undefined)).toEqual([])
    expect(flattenSubFormRuleLayoutContainers(null)).toEqual([])
  })
})

/**
 * The Assignment Mode component holds the assignee / BU / role rules as CHILDREN.
 * It has no `field` of its own, so unless it is treated as a layout container the
 * flattener keeps the container and isDialogMappableSubFormRule() then drops it —
 * silently taking its three fields with it. That is exactly how the User Portal
 * Add/Edit dialog ended up showing every column except the assignment ones.
 */
describe('miAssignment container flattening', () => {
  const ctx = { lookupDbConfigs: {}, relationViewConfigs: {} }

  it('lifts assignment fields out of the container into dialog columns', () => {
    const binding = { bindingId: 99 }
    const subForms = {
      99: {
        rule: [
          { type: 'input', field: 'name', title: 'Name' },
          { type: 'input', field: 'main_id', title: 'main id' },
          {
            type: 'miAssignment',
            children: [
              { type: 'lookup', field: 'assignee', title: 'Assignee' },
              { type: 'select', field: 'bu_code', title: 'Business Unit' },
              { type: 'select', field: 'role_code', title: 'Role' },
            ],
          },
        ],
      },
    }
    const cols = resolveSubFormDialogColumnsForBinding(binding, subForms, ctx)
    expect(cols.map(c => c.field))
      .toEqual(['name', 'main_id', 'assignee', 'bu_code', 'role_code'])
  })

  it('flattens the container itself away (it is not a column)', () => {
    const flat = flattenSubFormRuleLayoutContainers([
      { type: 'miAssignment', children: [{ type: 'input', field: 'assignee' }] },
    ]) as Array<Record<string, unknown>>
    expect(flat.map(r => r.field)).toEqual(['assignee'])
    expect(flat.some(r => r.type === 'miAssignment')).toBe(false)
  })

  it('handles an empty container without dropping siblings', () => {
    const flat = flattenSubFormRuleLayoutContainers([
      { type: 'input', field: 'a' },
      { type: 'miAssignment', children: [] },
      { type: 'input', field: 'b' },
    ]) as Array<Record<string, unknown>>
    expect(flat.map(r => r.field)).toEqual(['a', 'b'])
  })

  /**
   * The designed backfill view (Relation Table View on the binding) is the only truth for which
   * relation-table columns the Add/Edit dialog shows under a selected lookup value. Regression:
   * the lookupConfig re-merge used to rebuild the lookup props without the resolved relation view
   * and spread them last, wiping viewFields to [] — LookupField then fell back to the relation
   * table's global view and rendered every column (ATM: 8 sys_users columns instead of the 2 designed).
   */
  describe('lookup backfill viewFields survive the lookupConfig re-merge', () => {
    const ATM_BINDING = 5001
    const designedView = [{ fieldName: 'username' }, { fieldName: 'full_name' }]
    const lookupConfig = JSON.stringify({
      tableId: 77,
      bindingId: ATM_BINDING,
      searchFields: ['username'],
      displayFields: ['username'],
      selectedDisplayField: 'username',
      showBackfillView: true,
    })
    const binding = { bindingId: ATM_BINDING }
    const subForms = {
      [ATM_BINDING]: {
        rule: [
          { type: 'input', field: 'transaction_number', title: 'Transaction Number' },
          { type: 'lookup', field: 'assignee', title: 'Assign To', props: { lookupConfig } },
        ],
      },
    }

    function assigneeColumn(lookupCtx: Parameters<typeof resolveSubFormDialogColumnsForBinding>[2]) {
      const cols = resolveSubFormDialogColumnsForBinding(binding, subForms, lookupCtx)
      return cols.find(c => c.field === 'assignee')
    }

    it('keeps the designed per-binding relation view instead of falling back to all columns', () => {
      const col = assigneeColumn({
        lookupDbConfigs: {},
        relationViewConfigs: { [ATM_BINDING]: { viewFields: designedView } },
      } as never)
      expect(col?.type).toBe('lookup')
      expect(col?.props?.viewFields).toEqual(designedView)
      expect(col?.props?.showBackfillView).toBe(true)
    })

    it('keeps the db-config view fields when no per-binding relation view is configured', () => {
      const dbView = [{ fieldName: 'user_id' }, { fieldName: 'email' }]
      const col = assigneeColumn({
        lookupDbConfigs: { assignee: { tableId: 77, viewFields: dbView } },
        relationViewConfigs: {},
      } as never)
      expect(col?.props?.viewFields).toEqual(dbView)
    })

    it('still honours showBackfillView:false by emitting no view fields', () => {
      const hiddenBackfill = JSON.stringify({
        tableId: 77,
        bindingId: ATM_BINDING,
        displayFields: ['username'],
        showBackfillView: false,
      })
      const cols = resolveSubFormDialogColumnsForBinding(
        binding,
        {
          [ATM_BINDING]: {
            rule: [{ type: 'lookup', field: 'assignee', title: 'Assign To', props: { lookupConfig: hiddenBackfill } }],
          },
        },
        {
          lookupDbConfigs: {},
          relationViewConfigs: { [ATM_BINDING]: { viewFields: designedView } },
        } as never,
      )
      const col = cols.find(c => c.field === 'assignee')
      expect(col?.props?.viewFields).toEqual([])
      expect(col?.props?.showBackfillView).toBe(false)
    })

    it('does not lose the resolved lookup table id to the re-merge', () => {
      const col = assigneeColumn({
        lookupDbConfigs: { assignee: { tableId: 77, viewFields: [] } },
        relationViewConfigs: { [ATM_BINDING]: { viewFields: designedView } },
      } as never)
      expect(col?.props?.tableId).toBe(77)
    })
  })

  /**
   * The "keep the caller-resolved value" rule must not resurrect values the design deliberately
   * cleared or repointed. These two cases guard the restore from over-reaching.
   */
  describe('lookupConfig re-merge does not resurrect stale lookup props', () => {
    it('honours showBackfillView:false even when the column already carried view fields', () => {
      const cols = enrichLookupColumnPropsFromSubFormRule(
        [{
          field: 'assignee',
          label: 'Assign To',
          type: 'lookup',
          props: { tableId: 5, viewFields: [{ fieldName: 'username' }], showBackfillView: true },
        }] as never,
        [{
          field: 'assignee',
          type: 'lookup',
          props: { lookupConfig: JSON.stringify({ tableId: 5, displayFields: ['x'], showBackfillView: false }) },
        }],
      )
      expect(cols[0].props?.showBackfillView).toBe(false)
      expect(cols[0].props?.viewFields).toEqual([])
    })

    it('does not inherit the previous table view fields when the rule repoints to another table', () => {
      const cols = enrichLookupColumnPropsFromSubFormRule(
        [{
          field: 'assignee',
          label: 'Assign To',
          type: 'lookup',
          props: {
            tableId: 77,
            searchFields: ['old_field'],
            displayField: 'old_field',
            viewFields: [{ fieldName: 'old_field' }],
          },
        }] as never,
        [{
          field: 'assignee',
          type: 'lookup',
          props: {
            lookupConfig: JSON.stringify({
              tableId: 99,
              searchFields: ['new_field'],
              displayFields: ['new_field'],
            }),
          },
        }],
      )
      expect(cols[0].props?.tableId).toBe(99)
      expect(cols[0].props?.viewFields).toEqual([])
      expect(cols[0].props?.searchFields).toEqual(['new_field'])
    })

    it('promotes a non-lookup list column to lookup when the rule carries lookupConfig', () => {
      const cols = enrichLookupColumnPropsFromSubFormRule(
        [{ field: 'correspondence_type', label: 'Type', type: 'text' }] as never,
        [{
          field: 'correspondence_type',
          type: 'input',
          props: {
            lookupConfig: JSON.stringify({
              selectedDisplayField: 'standardizations',
              displayFields: ['standardizations'],
            }),
          },
        }],
      )
      expect(cols[0].type).toBe('lookup')
      expect(cols[0].props?.selectedDisplayField).toBe('standardizations')
    })
  })
})
