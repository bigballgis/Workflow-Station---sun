import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalFormFields from '../PortalFormFields.vue'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

/**
 * The Assignment Mode block must render in the surfaces driven by PortalFormFields —
 * the Link Form dialog and the Inline Form widget — not only in the grid's Add/Edit
 * dialog (SubTableAddDialog, covered by subTableAddDialogMiBlock.test.ts).
 *
 * Regression: `miAssignment` was listed in LAYOUT_CONTAINER_TYPES but had no render
 * branch, so the designer's marker fell through to the leaf renderer. That drew an
 * empty label-less box and its children — the assignee / BU / role rules the marker
 * owns — never rendered at all. The block was authored (MiAssignmentModeBlock.vue)
 * but never wired in, so every surface reaching a sub-form through this renderer
 * silently lost the block while the grid dialog kept it.
 */

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

const CONFIG: AssignmentConfig = {
  allowUser: true,
  allowRole: true,
  assigneeField: 'assignee',
  roleField: 'role_code',
  buField: 'bu_code',
}

/** The marker container owns the assignment rules as its CHILDREN, as the designer saves them. */
const FIELDS = [
  { key: 'name', label: 'Name', type: 'text' },
  {
    key: 'mi-marker',
    label: '',
    type: 'miAssignment',
    children: [
      { key: 'assignee', label: 'Assignee', type: 'text' },
      { key: 'bu_code', label: 'Business Unit', type: 'text' },
      { key: 'role_code', label: 'Role', type: 'text' },
    ],
  },
  { key: 'note', label: 'Note', type: 'text' },
]

function mountFields(overrides: Record<string, unknown> = {}) {
  return mount(PortalFormFields, {
    props: {
      fields: FIELDS,
      model: {},
      editable: true,
      readonly: false,
      assignmentConfig: CONFIG,
      ...overrides,
    } as never,
    global: { stubs: { teleport: true } },
  })
}

describe('PortalFormFields — Assignment Mode block', () => {
  it('renders the block instead of an empty leaf box for the miAssignment marker', () => {
    const wrapper = mountFields()

    expect(wrapper.find('.mi-assignment-block').exists()).toBe(true)
    // Both mode cards always render — a single-mode contract locks one rather than hiding it.
    expect(wrapper.findAll('.mi-assignment-mode-card')).toHaveLength(2)

    wrapper.unmount()
  })

  it('renders the active mode\'s own picker inside the block, not the other mode\'s', () => {
    const wrapper = mountFields()

    // Default mode is "person": the assignee picker renders, role/BU do not.
    const block = wrapper.find('.mi-assignment-block__fields')
    expect(block.exists()).toBe(true)
    expect(block.html()).toContain('Assignee')
    expect(block.html()).not.toContain('Business Unit')
    expect(block.html()).not.toContain('Role')

    wrapper.unmount()
  })

  it('opens on role mode and shows BU + Role when the row is already role-assigned', () => {
    const wrapper = mountFields({ model: { role_code: 'MANAGER', bu_code: 'E2E_FINANCE' } })

    const block = wrapper.find('.mi-assignment-block__fields')
    expect(block.html()).toContain('Business Unit')
    expect(block.html()).toContain('Role')
    expect(block.html()).not.toContain('Assignee')

    wrapper.unmount()
  })

  it('keeps the marker\'s children rendering flat when BPMN configured no contract', () => {
    // No assignmentConfig → no block, but the children are ordinary fields and must
    // still render. Dropping them is what made the pickers vanish entirely.
    const wrapper = mountFields({ assignmentConfig: undefined })

    expect(wrapper.find('.mi-assignment-block').exists()).toBe(false)
    expect(wrapper.html()).toContain('Assignee')
    expect(wrapper.html()).toContain('Business Unit')
    expect(wrapper.html()).toContain('Role')

    wrapper.unmount()
  })

  /**
   * The designer's Hide toggle removes the block AND the fields it owns — the same rule
   * dialogFormLayout applies for the grid's Add/Edit dialog. Rendering the children flat
   * here would leak the very pickers Hide is meant to remove.
   */
  it('drops the block and its owned fields when the designer hid the marker', () => {
    const wrapper = mountFields({
      fields: [
        { key: 'name', label: 'Name', type: 'text' },
        { ...FIELDS[1], hidden: true },
        { key: 'note', label: 'Note', type: 'text' },
      ],
    })

    expect(wrapper.find('.mi-assignment-block').exists()).toBe(false)
    expect(wrapper.html()).not.toContain('Assignee')
    expect(wrapper.html()).not.toContain('Business Unit')
    // The ordinary sibling fields are untouched.
    expect(wrapper.html()).toContain('Name')
    expect(wrapper.html()).toContain('Note')

    wrapper.unmount()
  })

  it('hides the block\'s owned fields too when Hide is set and no contract exists', () => {
    const wrapper = mountFields({
      assignmentConfig: undefined,
      fields: [{ ...FIELDS[1], hidden: true }],
    })

    expect(wrapper.html()).not.toContain('Assignee')
    expect(wrapper.html()).not.toContain('Role')

    wrapper.unmount()
  })

  /**
   * Designer Readonly on the block must lock the mode cards too — otherwise the user could
   * still move the row between a named assignee and a role pool even though the pickers are
   * disabled. Same rule SubTableAddDialog enforces via assignmentBlockReadonly.
   */
  it('locks both mode cards when the marker is readonly', () => {
    const wrapper = mountFields({
      fields: [
        { key: 'name', label: 'Name', type: 'text' },
        { ...FIELDS[1], readonly: true },
      ],
    })

    const cards = wrapper.findAll('.mi-assignment-mode-card')
    expect(cards).toHaveLength(2)
    expect(cards.every(c => c.classes().includes('is-disabled'))).toBe(true)

    wrapper.unmount()
  })

  it('locks the mode cards when the whole form is read-only', () => {
    const wrapper = mountFields({ readonly: true, editable: false })

    const cards = wrapper.findAll('.mi-assignment-mode-card')
    expect(cards.every(c => c.classes().includes('is-disabled'))).toBe(true)

    wrapper.unmount()
  })

  it('finds the marker nested inside a layout container', () => {
    const wrapper = mountFields({
      fields: [{ key: 'card', type: 'card', span: 24, children: FIELDS }],
    })

    expect(wrapper.find('.mi-assignment-block').exists()).toBe(true)

    wrapper.unmount()
  })
})
