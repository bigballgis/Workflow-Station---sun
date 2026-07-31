import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SubTableAddDialog from '../SubTableAddDialog.vue'
import type { AssignmentConfig } from '@/utils/miAssignmentConfig'

/**
 * The Assignment Mode block must render the assignee / role / BU fields INSIDE its
 * own framed box rather than leaving them stranded elsewhere in the dialog.
 * Structure is asserted on the real rendered DOM (marker head followed by the
 * fields it owns), since that adjacency is what the CSS frame depends on.
 */

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (key: string) => key }) }
})

vi.mock('@/api/user', () => ({
  userApi: { searchUsers: vi.fn().mockResolvedValue([]) },
}))

vi.mock('@/api/admin', () => ({
  getBusinessUnitTree: vi.fn().mockResolvedValue([]),
  getRolesByBusinessUnit: vi.fn().mockResolvedValue([]),
}))

const CONFIG: AssignmentConfig = {
  allowUser: true,
  allowRole: true,
  assigneeField: 'assignee',
  roleField: 'role_code',
  buField: 'bu_code',
}

const COLUMNS = [
  { field: 'name', label: 'Name', type: 'text' },
  { field: 'assignee', label: 'Assignee', type: 'text' },
  { field: 'bu_code', label: 'Business Unit', type: 'text' },
  { field: 'role_code', label: 'Role', type: 'text' },
  { field: 'note', label: 'Note', type: 'text' },
]

/** Designer places the marker LAST — the fix must still pull its fields up to it. */
const FORM_FIELDS = [
  { key: 'name', label: 'Name', type: 'text' },
  { key: 'assignee', label: 'Assignee', type: 'text' },
  { key: 'bu_code', label: 'Business Unit', type: 'text' },
  { key: 'role_code', label: 'Role', type: 'text' },
  { key: 'note', label: 'Note', type: 'text' },
  { key: 'mi-marker', label: '', type: 'miAssignment' },
]

function mountDialog() {
  return mount(SubTableAddDialog, {
    props: {
      visible: true,
      mode: 'add',
      columns: COLUMNS,
      formFields: FORM_FIELDS,
      assignmentConfig: CONFIG,
    } as never,
    attachTo: document.body,
    global: { stubs: { teleport: true } },
  })
}

describe('SubTableAddDialog — Assignment Mode block', () => {
  it('renders the marker head and owns the mode-relevant field', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.mi-assignment-block__head').exists()).toBe(true)

    // Default mode is "person" → the assignee field is owned; role/BU are hidden.
    const owned = wrapper.findAll('.mi-assignment-block__field')
    expect(owned.length).toBeGreaterThan(0)
    const ownedProps = owned.map(w => w.attributes('class') || '')
    expect(ownedProps.length).toBe(1)
    // Role / BU must not render in person mode at all.
    expect(wrapper.html()).not.toContain('role_code')
    expect(wrapper.html()).not.toContain('bu_code')

    // Exactly one field closes the box.
    expect(wrapper.findAll('.mi-assignment-block__field--last')).toHaveLength(1)

    wrapper.unmount()
  })

  it('swaps to BU + Role inside the block when the row is role-assigned', async () => {
    const wrapper = mount(SubTableAddDialog, {
      props: {
        visible: true,
        mode: 'edit',
        // Existing role/BU values → resolveAssignModeFromRow() picks "role".
        initialData: { role_code: 'MANAGER', bu_code: 'E2E_FINANCE' },
        columns: COLUMNS,
        formFields: FORM_FIELDS,
        assignmentConfig: CONFIG,
      } as never,
      attachTo: document.body,
      global: { stubs: { teleport: true } },
    })
    await wrapper.vm.$nextTick()

    // Role mode owns TWO fields (BU + Role); assignee must be gone.
    expect(wrapper.findAll('.mi-assignment-block__field')).toHaveLength(2)
    expect(wrapper.findAll('.mi-assignment-block__field--last')).toHaveLength(1)

    wrapper.unmount()
  })

  /**
   * FU 50005 "Assign Task" shape: BPMN configures assignee + BU + role, but the
   * sub-form was designed before the Assignment Mode component existed, so it has
   * no marker. The block must still render — with its picker inside it — instead
   * of an empty frame beside a stranded Assignee row.
   */
  it('renders the block and owns its picker when the design has no marker', async () => {
    const wrapper = mount(SubTableAddDialog, {
      props: {
        visible: true,
        mode: 'add',
        columns: COLUMNS,
        formFields: FORM_FIELDS.filter(f => f.type !== 'miAssignment'),
        assignmentConfig: CONFIG,
      } as never,
      attachTo: document.body,
      global: { stubs: { teleport: true } },
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.mi-assignment-block__head').exists()).toBe(true)
    // The block is never an empty frame: it owns the picker for the active mode.
    expect(wrapper.findAll('.mi-assignment-block__field')).toHaveLength(1)
    expect(wrapper.findAll('.mi-assignment-block__field--last')).toHaveLength(1)

    wrapper.unmount()
  })

  /**
   * The designer nests assignee / BU / role inside the Assignment Mode container
   * so the whole unit drags as one. The dialog must still render them as real,
   * individually-bound fields inside the block.
   */
  it('renders fields nested inside the container as owned block fields', async () => {
    const wrapper = mount(SubTableAddDialog, {
      props: {
        visible: true,
        mode: 'add',
        columns: COLUMNS,
        formFields: [
          { key: 'name', label: 'Name', type: 'text' },
          {
            key: 'mi-container',
            label: '',
            type: 'miAssignment',
            children: [
              { key: 'assignee', label: 'Assignee', type: 'text' },
              { key: 'bu_code', label: 'Business Unit', type: 'text' },
              { key: 'role_code', label: 'Role', type: 'text' },
            ],
          },
          { key: 'note', label: 'Note', type: 'text' },
        ],
        assignmentConfig: CONFIG,
      } as never,
      attachTo: document.body,
      global: { stubs: { teleport: true } },
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.mi-assignment-block__head').exists()).toBe(true)
    // Person mode owns the nested assignee only; role/BU stay out of the DOM.
    expect(wrapper.findAll('.mi-assignment-block__field')).toHaveLength(1)
    expect(wrapper.findAll('.mi-assignment-block__field--last')).toHaveLength(1)
    // Fields outside the container are unaffected.
    expect(wrapper.html()).toContain('Note')

    wrapper.unmount()
  })

  it('offers both destinations as selectable mode cards', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()

    const cards = wrapper.findAll('.mi-assignment-mode-card')
    expect(cards).toHaveLength(2)
    // Exactly one destination is active at a time.
    expect(wrapper.findAll('.mi-assignment-mode-card.is-selected')).toHaveLength(1)
    expect(cards[0]!.attributes('aria-checked')).toBe('true')

    // Choosing "by role" swaps the owned picker to BU + Role.
    await cards[1]!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(cards[1]!.attributes('aria-checked')).toBe('true')
    expect(wrapper.findAll('.mi-assignment-block__field')).toHaveLength(2)

    wrapper.unmount()
  })

  it('places owned fields immediately after the marker head in DOM order', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()

    const nodes = Array.from(
      (wrapper.element as Element)
        .querySelectorAll('.mi-assignment-block__head, .mi-assignment-block__field'),
    )
    expect(nodes.length).toBeGreaterThanOrEqual(2)
    // Head must come first, then its owned fields — contiguous, nothing in between.
    expect(nodes[0]!.classList.contains('mi-assignment-block__head')).toBe(true)
    for (const node of nodes.slice(1)) {
      expect(node.classList.contains('mi-assignment-block__field')).toBe(true)
    }

    wrapper.unmount()
  })
})
