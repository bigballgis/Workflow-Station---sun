import { describe, it, expect, vi } from 'vitest'
import { defineComponent, h, provide, computed, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import MiAssignmentPlaceholderWidget from '../MiAssignmentPlaceholderWidget.vue'
import MiAssignmentConfigScope from '../MiAssignmentConfigScope.vue'
import MiAssignmentPreviewScope from '../MiAssignmentPreviewScope.vue'
import { MI_ASSIGNMENT_CONFIG_KEY, type AssignmentConfig } from '@/utils/miAssignmentConfig'

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return { ...actual, useI18n: () => ({ t: (k: string) => k }) }
})

const PARTICIPANTS_CONFIG: AssignmentConfig = {
  allowUser: true,
  allowRole: true,
  assigneeField: 'assignee',
  roleField: 'role_code',
  buField: 'bu_code',
}

/** Stands in for FormDesigner, which provides the config of the open sub-table tab. */
const DesignerHost = defineComponent({
  props: { activeTabConfig: { type: Object, default: undefined } },
  setup(props, { slots }) {
    provide(MI_ASSIGNMENT_CONFIG_KEY, computed(() => props.activeTabConfig as AssignmentConfig | undefined))
    return () => h('div', slots.default?.())
  },
})

/** The real wrapper preview uses to bind a block to one sub-table. */
const ScopedSubTable = MiAssignmentConfigScope

function isUnconfigured(wrapper: ReturnType<typeof mount>): boolean {
  return wrapper.find('.mi-assignment-widget').classes().includes('is-unconfigured')
}

/**
 * The designer provides the assignment contract of whichever sub-table tab is open.
 * Preview renders every sub-table at once, so without a nearer provide each one reads
 * the open tab's config, and every other sub-table wrongly reports that it has no
 * matching multi-instance assignment.
 */
describe('MiAssignmentPlaceholderWidget — config scoping', () => {
  const mountInPreview = (openTabConfig: AssignmentConfig | undefined,
                          thisSubTableConfig: AssignmentConfig | undefined) =>
    mount(DesignerHost, {
      props: { activeTabConfig: openTabConfig },
      slots: {
        default: () => h(ScopedSubTable, { assignmentConfig: thisSubTableConfig },
          () => h(MiAssignmentPlaceholderWidget)),
      },
      global: { stubs: { 'el-alert': true } },
    })

  it('uses the sub-table it is rendered in, not the open designer tab', () => {
    expect(isUnconfigured(mountInPreview(undefined, PARTICIPANTS_CONFIG))).toBe(false)
  })

  it('reports unconfigured when this sub-table has none, even if the open tab has one', () => {
    expect(isUnconfigured(mountInPreview(PARTICIPANTS_CONFIG, undefined))).toBe(true)
  })

  it('still falls back to the designer-level config on the canvas', () => {
    const wrapper = mount(DesignerHost, {
      props: { activeTabConfig: PARTICIPANTS_CONFIG },
      slots: { default: () => h(MiAssignmentPlaceholderWidget) },
      global: { stubs: { 'el-alert': true } },
    })

    expect(isUnconfigured(wrapper)).toBe(false)
  })

  it('reports unconfigured when nothing provides a contract', () => {
    const wrapper = mount(MiAssignmentPlaceholderWidget, {
      global: {
        provide: { [MI_ASSIGNMENT_CONFIG_KEY as symbol]: ref(undefined) },
        stubs: { 'el-alert': true },
      },
    })

    expect(isUnconfigured(wrapper)).toBe(true)
  })
})

/**
 * The scoping above only holds because SubTableField re-provides the contract for the
 * inline form strip it renders. Mounting the whole component would drag in form-create
 * and a table's worth of stubs, so pin the wiring at its source: the provide call must
 * be present and must track the `assignmentConfig` prop.
 */
describe('preview blocks scope the assignment contract to their binding', () => {
  const read = (file: string) =>
    readFileSync(resolve(process.cwd(), 'src/components/designer', file), 'utf-8')

  it('SubTableField provides for the inline strip it renders below the table', () => {
    expect(read('SubTableField.vue')).toMatch(
      /provide\(MI_ASSIGNMENT_CONFIG_KEY,\s*computed\(\(\) => props\.assignmentConfig\)\)/)
  })

  /**
   * The Inline Form block renders a sub-table's whole designed form — the marker's
   * most common home — as a bare form-create, so it needs the wrapper explicitly.
   *
   * It uses MiAssignmentPreviewScope rather than the plain config scope: the block is a
   * runtime surface, so it must also provide the active mode (see the suite below).
   */
  it('the Inline Form preview block is wrapped in the scope', () => {
    const preview = read('FormPreviewItems.vue')
    const inlineBlock = preview.slice(
      preview.indexOf("item.kind === 'inlineSubForm'"),
      preview.indexOf("item.kind === 'relationTable'"),
    )
    expect(inlineBlock).toContain('<MiAssignmentPreviewScope')
    expect(inlineBlock).toContain(':assignment-config="item.binding.assignmentConfig"')
  })
})

/**
 * Parity with the Add/Edit dialog: the Inline Form block is a RUNTIME surface, not the
 * designer canvas. Providing MI_ASSIGNMENT_MODE_KEY is what makes the widget select a
 * card, show only that mode's pickers, and accept a click on the other card — without it
 * the block rendered two dead cards with all three pickers stacked underneath.
 */
describe('MiAssignmentPreviewScope — runtime shaping for the Inline Form block', () => {
  const RULE = [
    { type: 'miAssignment', props: {}, children: [
      { type: 'input', field: 'assignee', title: 'Assignee' },
      { type: 'select', field: 'bu_code', title: 'Business Unit' },
      { type: 'select', field: 'role_code', title: 'Role' },
    ] },
  ]

  const mountScope = (config: AssignmentConfig | undefined, row: Record<string, unknown> = {}) =>
    mount(MiAssignmentPreviewScope, {
      props: { rule: RULE, assignmentConfig: config, row },
      slots: { default: () => h(MiAssignmentPlaceholderWidget) },
      global: { stubs: { 'el-alert': true } },
    })

  it('renders the widget as a runtime surface, with the active mode selected', () => {
    const wrapper = mountScope(PARTICIPANTS_CONFIG)
    // is-static marks the designer canvas; a selected card marks the runtime surface.
    expect(wrapper.find('.mi-assignment-widget__modes').classes()).not.toContain('is-static')
    expect(wrapper.findAll('.mi-assignment-mode-card.is-selected')).toHaveLength(1)
  })

  it('exposes only the active mode\'s fields through the slot', () => {
    const fieldsOf = (rule: any[]): string[] => rule.flatMap(r =>
      [...(r?.field ? [String(r.field)] : []), ...fieldsOf(r?.children ?? [])])

    let slotRule: any[] = []
    mount(MiAssignmentPreviewScope, {
      props: { rule: RULE, assignmentConfig: PARTICIPANTS_CONFIG, row: {} },
      slots: { default: (p: any) => { slotRule = p.rule; return h('div') } },
    })

    // Opens on 'person' for an empty row: the assignee picker, not BU + Role.
    expect(fieldsOf(slotRule)).toEqual(['assignee'])
  })

  it('opens on role when the row already carries a role, like the dialog', () => {
    const fieldsOf = (rule: any[]): string[] => rule.flatMap(r =>
      [...(r?.field ? [String(r.field)] : []), ...fieldsOf(r?.children ?? [])])

    let slotRule: any[] = []
    mount(MiAssignmentPreviewScope, {
      props: { rule: RULE, assignmentConfig: PARTICIPANTS_CONFIG, row: { role_code: 'MANAGER' } },
      slots: { default: (p: any) => { slotRule = p.rule; return h('div') } },
    })

    expect(fieldsOf(slotRule)).toEqual(['bu_code', 'role_code'])
  })

  it('passes the rule through untouched when the sub-table has no contract', () => {
    let slotRule: any[] = []
    mount(MiAssignmentPreviewScope, {
      props: { rule: RULE, assignmentConfig: undefined, row: {} },
      slots: { default: (p: any) => { slotRule = p.rule; return h('div') } },
    })

    // Deep equality, not identity: Vue hands the slot a reactive proxy of the prop.
    expect(slotRule).toStrictEqual(RULE)
  })
})
