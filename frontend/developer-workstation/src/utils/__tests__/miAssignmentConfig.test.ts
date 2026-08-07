import { describe, expect, it } from 'vitest'
import {
  assignmentChildFieldOrder,
  fieldsHiddenByMode,
  fieldsOwnedByMode,
  isAssignModeSwitchable,
  isAssignmentConfigured,
  lockedAssignMode,
  nestAssignmentFieldsIntoContainer,
  parseMiAssignmentsFromBpmn,
  resolveAssignModeFromRow,
  validateMiAssignmentComponents,
} from '../miAssignmentConfig'

function bpmn(tasks: string): string {
  return `<?xml version="1.0"?>
    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
      xmlns:custom="http://workflow.platform/schema/custom">
      <process id="p">
        <subProcess id="mi">
          <multiInstanceLoopCharacteristics />
          ${tasks}
        </subProcess>
      </process>
    </definitions>`
}

function task(id: string, mode: string, table: string, fields: Record<string, string>): string {
  const properties = Object.entries({
    subTableName: table,
    assigneeMode: mode,
    ...fields,
  }).map(([name, value]) => `<custom:property name="${name}" value="${value}" />`).join('')
  return `<userTask id="${id}" name="${id}">
    <extensionElements><custom:Properties>${properties}</custom:Properties></extensionElements>
  </userTask>`
}

describe('miAssignmentConfig', () => {
  it('parses user, role and both modes without field-name defaults', () => {
    const parsed = parseMiAssignmentsFromBpmn(bpmn([
      task('userNode', 'user', 'people', { assigneeField: 'owner_user_id' }),
      task('roleNode', 'role', 'roles', { roleField: 'approver_role', buField: 'department_code' }),
      task('bothNode', 'both', 'mixed', { assigneeField: 'person_id', roleField: 'role_id' }),
    ].join('')))

    expect(parsed.configs.people).toEqual({
      allowUser: true,
      allowRole: false,
      assigneeField: 'owner_user_id',
      roleField: undefined,
      buField: undefined,
    })
    expect(parsed.configs.roles.allowRole).toBe(true)
    expect(parsed.configs.mixed).toMatchObject({ allowUser: true, allowRole: true })
  })

  it('reports conflicts but merges identical configs for one Sub Table', () => {
    const same = task('a', 'user', 'people', { assigneeField: 'owner' })
    const parsedSame = parseMiAssignmentsFromBpmn(bpmn(same + task('b', 'user', 'people', { assigneeField: 'owner' })))
    expect(parsedSame.diagnostics).toEqual([])
    expect(parsedSame.configs.people.assigneeField).toBe('owner')

    const conflict = parseMiAssignmentsFromBpmn(
      bpmn(same + task('b', 'role', 'people', { roleField: 'role' })),
    )
    expect(conflict.configs.people).toBeUndefined()
    expect(conflict.diagnostics[0]).toMatchObject({
      code: 'CONFLICTING_MI_ASSIGNMENT_CONFIG',
      subTableName: 'people',
      nodeIds: ['a', 'b'],
    })
  })

  it('derives visibility and initial mode from configured field names', () => {
    const config = {
      allowUser: true,
      allowRole: true,
      assigneeField: 'owner_user_id',
      roleField: 'approver_role',
      buField: 'department_code',
    }
    expect(resolveAssignModeFromRow({ approver_role: 'R1' }, config)).toBe('role')
    expect(fieldsHiddenByMode('person', config)).toEqual(new Set(['approver_role', 'department_code']))
    expect(fieldsHiddenByMode('role', config)).toEqual(new Set(['owner_user_id']))
    expect(isAssignmentConfigured({ ...config, roleField: '' })).toBe(false)
  })

  describe('isAssignModeSwitchable / lockedAssignMode', () => {
    const BOTH = { allowUser: true, allowRole: true, assigneeField: 'assignee', roleField: 'role_code', buField: 'bu_code' }
    const USER_ONLY = { allowUser: true, allowRole: false, assigneeField: 'assignee' }
    const ROLE_ONLY = { allowUser: false, allowRole: true, roleField: 'role_code', buField: 'bu_code' }

    it('is switchable only when both modes are configured', () => {
      expect(isAssignModeSwitchable(BOTH)).toBe(true)
      expect(isAssignModeSwitchable(USER_ONLY)).toBe(false)
      expect(isAssignModeSwitchable(ROLE_ONLY)).toBe(false)
      expect(isAssignModeSwitchable(undefined)).toBe(false)
    })

    it('locks to the single configured mode when not switchable', () => {
      expect(lockedAssignMode(USER_ONLY)).toBe('person')
      expect(lockedAssignMode(ROLE_ONLY)).toBe('role')
    })

    it('returns undefined when switchable (both modes) — nothing to lock to', () => {
      expect(lockedAssignMode(BOTH)).toBeUndefined()
    })

    it('returns undefined when not configured at all — nothing to show', () => {
      expect(lockedAssignMode(undefined)).toBeUndefined()
      expect(lockedAssignMode({ allowUser: false, allowRole: false })).toBeUndefined()
      // allowRole true but roleField missing — not actually configured.
      expect(lockedAssignMode({ allowUser: false, allowRole: true })).toBeUndefined()
    })
  })

  it('owns only the active mode fields, BU before Role', () => {
    const config = {
      allowUser: true,
      allowRole: true,
      assigneeField: 'assignee',
      roleField: 'role_code',
      buField: 'bu_code',
    }
    expect(fieldsOwnedByMode('person', config)).toEqual(['assignee'])
    // BU narrows the role list, so it must read first inside the block.
    expect(fieldsOwnedByMode('role', config)).toEqual(['bu_code', 'role_code'])
    // Optional BU: role-only contracts still own their one picker.
    expect(fieldsOwnedByMode('role', { ...config, buField: undefined })).toEqual(['role_code'])
    expect(fieldsOwnedByMode('person', { ...config, assigneeField: '  ' })).toEqual([])
  })

  /**
   * The Assignment Mode component OWNS the assignee / BU / role rules as children,
   * so dragging it moves the whole unit. Forms saved before the container existed
   * keep those fields as siblings — nesting is their migration on read.
   */
  describe('nestAssignmentFieldsIntoContainer', () => {
    const CONFIG = {
      allowUser: true,
      allowRole: true,
      assigneeField: 'assignee',
      roleField: 'role_code',
      buField: 'bu_code',
    }
    /** Shared shape so mixed field/container literals stay one array type. */
    type Rule = { type?: string; field?: string; children?: unknown[]; _miAdopted?: boolean }
    const f = (field: string): Rule => ({ field, type: 'input' })
    const container = (children: Rule[] = []): Rule => ({ type: 'miAssignment', children })
    const kidsOf = (list: Rule[]): string[] =>
      ((list.find(r => r.type === 'miAssignment')?.children ?? []) as Rule[])
        .map(k => k.field ?? '')

    it('folds legacy sibling fields into the container in contract order', () => {
      // FU 50005 shape: fields scattered after the container, BU/Role reversed.
      const out = nestAssignmentFieldsIntoContainer([
        f('main_id'), f('name'), container(), f('role_code'), f('assignee'), f('bu_code'),
      ], CONFIG)

      expect(out.map(r => r.field ?? r.type))
        .toEqual(['main_id', 'name', 'miAssignment'])
      // Fixed order: assignee, then BU before Role (BU narrows the role list).
      expect(kidsOf(out)).toEqual(['assignee', 'bu_code', 'role_code'])
    })

    it('is idempotent — already-nested children stay put', () => {
      const nested = [f('name'), container([f('assignee'), f('bu_code'), f('role_code')])]
      const once = nestAssignmentFieldsIntoContainer(nested, CONFIG)
      const twice = nestAssignmentFieldsIntoContainer(once, CONFIG)
      expect(kidsOf(once)).toEqual(['assignee', 'bu_code', 'role_code'])
      expect(kidsOf(twice)).toEqual(['assignee', 'bu_code', 'role_code'])
    })

    it('converges a partial migration (some nested, some still siblings)', () => {
      const out = nestAssignmentFieldsIntoContainer(
        [container([f('assignee')]), f('bu_code'), f('role_code')], CONFIG)
      expect(kidsOf(out)).toEqual(['assignee', 'bu_code', 'role_code'])
      expect(out).toHaveLength(1)
    })

    it('never invents a container and never drops a field', () => {
      // No container → untouched (first placement is the dialog's job).
      const noContainer = [f('assignee'), f('name')]
      expect(nestAssignmentFieldsIntoContainer(noContainer, CONFIG)).toBe(noContainer)
      // Unconfigured contract → untouched.
      expect(nestAssignmentFieldsIntoContainer(noContainer, undefined)).toBe(noContainer)

      // Every field survives, only its depth changes.
      const input = [f('a'), container(), f('assignee'), f('bu_code'), f('role_code'), f('z')]
      const out = nestAssignmentFieldsIntoContainer(input, CONFIG)
      const flat = (list: any[]): string[] => list.flatMap(r =>
        r.type === 'miAssignment' ? flat(r.children ?? []) : [r.field])
      expect(flat(out).sort()).toEqual(['a', 'assignee', 'bu_code', 'role_code', 'z'])
    })

    /**
     * The designer canvas passes createIfMissing: forms authored before the container
     * existed have the three fields but no container rule, and leaving them loose is
     * exactly the scattered layout the container is meant to fix.
     */
    describe('createIfMissing (designer canvas)', () => {
      it('creates the container where the first owned field sat', () => {
        // FU 50005 Participants shape: no container anywhere.
        const out = nestAssignmentFieldsIntoContainer(
          [f('main_id'), f('id_idw'), f('name'), f('assignee'), f('bu_code'), f('role_code')],
          CONFIG,
          { createIfMissing: true },
        )
        expect(out.map(r => r.field ?? r.type))
          .toEqual(['main_id', 'id_idw', 'name', 'miAssignment'])
        expect(kidsOf(out)).toEqual(['assignee', 'bu_code', 'role_code'])
      })

      it('keeps non-owned fields that followed the anchor', () => {
        const out = nestAssignmentFieldsIntoContainer(
          [f('a'), f('assignee'), f('z'), f('role_code')], CONFIG, { createIfMissing: true })
        expect(out.map(r => r.field ?? r.type)).toEqual(['a', 'miAssignment', 'z'])
        expect(kidsOf(out)).toEqual(['assignee', 'role_code'])
      })

      it('drops nothing — every field survives at some depth', () => {
        const input = [f('a'), f('assignee'), f('b'), f('bu_code'), f('role_code'), f('z')]
        const out = nestAssignmentFieldsIntoContainer(input, CONFIG, { createIfMissing: true })
        const flat = (list: any[]): string[] => list.flatMap(r =>
          r.type === 'miAssignment' ? flat((r.children ?? []) as any[]) : [r.field])
        expect(flat(out).sort()).toEqual(['a', 'assignee', 'b', 'bu_code', 'role_code', 'z'])
      })

      it('stays idempotent across repeated loads', () => {
        const once = nestAssignmentFieldsIntoContainer(
          [f('name'), f('assignee'), f('bu_code'), f('role_code')], CONFIG, { createIfMissing: true })
        const twice = nestAssignmentFieldsIntoContainer(once, CONFIG, { createIfMissing: true })
        expect(twice.filter(r => r.type === 'miAssignment')).toHaveLength(1)
        expect(kidsOf(twice)).toEqual(['assignee', 'bu_code', 'role_code'])
      })

      it('leaves a form that owns none of the fields alone', () => {
        const unrelated = [f('a'), f('b')]
        expect(nestAssignmentFieldsIntoContainer(unrelated, CONFIG, { createIfMissing: true }))
          .toBe(unrelated)
      })

      it('still creates nothing when the flag is off (default)', () => {
        const loose = [f('name'), f('assignee'), f('bu_code')]
        expect(nestAssignmentFieldsIntoContainer(loose, CONFIG)).toBe(loose)
      })
    })

    /**
     * The container is a normal drop container: after the one-time adoption the author
     * owns membership and order. These are the guarantees that make drag-out stick.
     */
    describe('author control after adoption', () => {
      const adopted = (children: Rule[] = []): Rule =>
        ({ type: 'miAssignment', children, _miAdopted: true } as Rule)

      it('never re-captures a field the author dragged out', () => {
        // Author moved bu_code back out to the top level; it must stay there.
        const authored = [adopted([f('assignee')]), f('bu_code'), f('name')]
        const out = nestAssignmentFieldsIntoContainer(authored, CONFIG)
        expect(out).toBe(authored)
        expect(kidsOf(out)).toEqual(['assignee'])
      })

      it('leaves an emptied container empty', () => {
        // Author dragged everything out — the container stays an empty drop area.
        const emptied = [adopted([]), f('assignee'), f('bu_code'), f('role_code')]
        const out = nestAssignmentFieldsIntoContainer(emptied, CONFIG)
        expect(out).toBe(emptied)
        expect(kidsOf(out)).toEqual([])
      })

      it('keeps the author order inside the container', () => {
        // Role before BU is the author's choice; adoption must not re-sort it.
        const authored = [adopted([f('role_code'), f('bu_code'), f('assignee')])]
        const out = nestAssignmentFieldsIntoContainer(authored, CONFIG)
        expect(kidsOf(out)).toEqual(['role_code', 'bu_code', 'assignee'])
      })

      it('marks a first-time adoption so it runs only once', () => {
        const legacy = [f('name'), container(), f('assignee'), f('bu_code')]
        const once = nestAssignmentFieldsIntoContainer(legacy, CONFIG)
        const box = once.find(r => r.type === 'miAssignment') as Rule & { _miAdopted?: boolean }
        expect(box._miAdopted).toBe(true)
        expect(kidsOf(once)).toEqual(['assignee', 'bu_code'])

        // Author then drags assignee out; a later load must respect that.
        const edited = once
          .filter(r => r.type === 'miAssignment')
          .map(r => ({ ...r, children: [] } as Rule))
          .concat([f('assignee')])
        expect(nestAssignmentFieldsIntoContainer(edited, CONFIG)).toBe(edited)
      })

      it('appends only still-loose fields, preserving what is already inside', () => {
        // Not yet adopted: assignee already nested, bu/role still loose.
        const partial = [container([f('assignee')]), f('bu_code'), f('role_code')]
        expect(kidsOf(nestAssignmentFieldsIntoContainer(partial, CONFIG)))
          .toEqual(['assignee', 'bu_code', 'role_code'])
      })
    })

    it('reaches a container nested inside a card', () => {
      const out = nestAssignmentFieldsIntoContainer(
        [{ type: 'card', children: [f('assignee'), container(), f('bu_code')] }], CONFIG)
      const card = out[0] as { children: any[] }
      const kids = card.children.find(r => r.type === 'miAssignment').children
      expect(kids.map((k: { field: string }) => k.field)).toEqual(['assignee', 'bu_code'])
      expect(card.children.filter(r => r.field)).toHaveLength(0)
    })

    it('orders children assignee, BU, role regardless of config key order', () => {
      expect(assignmentChildFieldOrder(CONFIG)).toEqual(['assignee', 'bu_code', 'role_code'])
      expect(assignmentChildFieldOrder({ ...CONFIG, buField: undefined }))
        .toEqual(['assignee', 'role_code'])
    })
  })

  it('does not block a sub-table with no Assignment Mode component anywhere — that is the developer\'s call', () => {
    const parsed = parseMiAssignmentsFromBpmn(
      bpmn(task('node', 'user', 'people', { assigneeField: 'owner' })),
    )
    const bindings = [
      { bindingId: 11, tableName: 'people' },
      { bindingId: 12, tableName: 'other' },
    ]
    const noComponent = validateMiAssignmentComponents(parsed, bindings, {
      subForms: { 11: { rule: [] }, 12: { rule: [{ type: 'miAssignment' }] } },
    })
    expect(noComponent.blocking).toEqual([])
    // The component on binding 12 ("other") is still flagged: BPMN's contract is
    // for "people", not "other" — that is a genuine stray placement, still warned.
    expect(noComponent.warnings).toEqual([{
      code: 'ORPHAN_MI_ASSIGNMENT_COMPONENT',
      bindingId: 12,
      subTableName: 'other',
    }])
  })

  it('still blocks a genuine BPMN conflict (nodes disagreeing on the same sub-table\'s contract)', () => {
    const conflict = parseMiAssignmentsFromBpmn(
      bpmn(
        task('a', 'user', 'people', { assigneeField: 'owner' })
        + task('b', 'role', 'people', { roleField: 'role' }),
      ),
    )
    const guard = validateMiAssignmentComponents(conflict, [{ bindingId: 11, tableName: 'people' }], {
      subForms: {},
    })
    expect(guard.blocking[0]).toMatchObject({ code: 'CONFLICTING_MI_ASSIGNMENT_CONFIG', subTableName: 'people' })
  })
})
