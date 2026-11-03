import { flushPromises } from '@vue/test-utils'
import type { BatchEventContext } from '@platform-shared/batch/eventContext'
import type { BatchAssignmentDirectory } from '@platform-shared/batch/assignmentDirectoryTypes'
import AssignmentEditor from '@platform-shared/batch/BatchAssignmentEditor.vue'
import { batchLookupComponentKey } from '@platform-shared/batch/lookupComponent'
import { defineComponent, h, ref, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import { workspace, state, rows } from './batchWorkspaceTestHelpers'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) } }))
describe('selected-row Batch Form', () => {
  it('collects per-row event backfills in the declared patches and leaves unselected rows intact', async () => {
    const runRowEvents = createBatchRowEventRunner(parseFormCreateEventHandler)
    const wrapper = workspace({ runRowEvents, rules: [
      { field: 'caption', type: 'input', on: { change: '$FNX:api.setValue("amount", api.getValue("amount") + 10)' } },
      { field: 'amount', type: 'inputNumber' },
    ] })
    state(wrapper).setValue('caption', 'Shared')
    await state(wrapper).apply()
    const [next, changes] = wrapper.emitted('apply')![0]!
    expect(next).toMatchObject([{ caption: 'Shared', amount: 11 }, { caption: 'Shared', amount: 12 }, { caption: 'C', amount: 3 }])
    expect(changes).toMatchObject({ rowPatches: [
      { rowId: 'one', fields: { amount: { kind: 'set', value: 11 } } },
      { rowId: 'two', fields: { amount: { kind: 'set', value: 12 } } },
    ] })
  })
  it('applies form onChange backfills from the assignment area to outside fields without replay', async () => {
    const runRowEvents = createBatchRowEventRunner(parseFormCreateEventHandler)
    const wrapper = workspace({ runRowEvents,
      assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'recipient' },
      options: { onChange: '$FNX:if (field === "recipient") { api.setValue("caption", value.email); api.setValue("amount", api.getValue("amount") + 10); }' },
      rules: [{ type: 'miAssignment', children: [{ type: 'lookup', field: 'recipient' }] },
        { type: 'input', field: 'caption' }, { type: 'inputNumber', field: 'amount' }],
    })
    state(wrapper).setValue('recipient', { id: 'synthetic-user', email: 'synthetic@example.invalid' })
    await state(wrapper).apply()
    const [next, changes] = wrapper.emitted('apply')![0]!
    expect(next).toMatchObject([{ caption: 'synthetic@example.invalid', amount: 11 }, { caption: 'synthetic@example.invalid', amount: 12 }, { caption: 'C', amount: 3 }])
    expect(changes).toMatchObject({ rowPatches: [{ rowId: 'one' }, { rowId: 'two' }] })
  })
  it('rolls every selected draft back when one row rejects a script write', async () => {
    const runRowEvents = createBatchRowEventRunner(parseFormCreateEventHandler)
    const wrapper = workspace({ runRowEvents, rules: [
      { type: 'input', field: 'caption', on: { change: '$FNX:api.setValue("amount", 9); if (api.getValue("key") === "two") api.setValue("unknown", 1);' } },
      { type: 'inputNumber', field: 'amount' },
    ] })
    state(wrapper).setValue('caption', 'rejected')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.props('rows')).toEqual(rows)
  })
  it('compares the assignment against the original owner after events have changed the draft', async () => {
    const source = rows.map(row => ({ ...row, recipient: 'original-person' }))
    const collect = vi.fn((selected: Array<Record<string, unknown>>) => ({ rows: selected.map(row => ({ ...row, recipient: 'new-person' })),
      change: { operation: 'ASSIGN', batchControlId: 'grid', bindingId: '1', rowPatches: [] } }))
    const wrapper = workspace({ rows: source, showAssign: true,
      assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'recipient' },
      collectAssignment: collect, assignmentHasChanges: () => true,
      runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onChange: '$FNX:if (field === "recipient") api.setValue("caption", value.email)' },
      rules: [{ type: 'miAssignment', children: [{ type: 'lookup', field: 'recipient' }] }, { type: 'input', field: 'caption' }],
    })
    state(wrapper).setValue('recipient', { id: 'new-person', email: 'synthetic@example.invalid' })
    await state(wrapper).apply()
    expect(collect.mock.calls[0]![0]).toMatchObject([
      { recipient: 'original-person', caption: 'synthetic@example.invalid' },
      { recipient: 'original-person', caption: 'synthetic@example.invalid' },
    ])
    expect(wrapper.emitted('apply-workspace')).toHaveLength(1)
  })
  it('keeps the Assign component mounted when a sibling field changes', async () => {
    const AssignmentState = defineComponent({ setup() { const edits = ref(0); return () => h('button', { class: 'assignment-state', onClick: () => edits.value++ }, String(edits.value)) } })
    const wrapper = workspace({ showAssign: true, runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'miAssignment' }, { type: 'input', field: 'caption' }],
    }, { assignment: () => h(AssignmentState) })
    await nextTick()
    await wrapper.find('.assignment-state').trigger('click')
    state(wrapper).setValue('caption', 'changed outside Assign')
    await nextTick(); await nextTick()
    expect(wrapper.find('.assignment-state').text()).toBe('1')
  })
  it('does not commit an assignment Lookup draft without a typed Assign intent', async () => {
    const source = rows.map(row => ({ ...row, recipient: 'original-person' }))
    const wrapper = workspace({ rows: source, showAssign: true,
      assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'recipient' },
      assignmentHasChanges: () => false,
      runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'miAssignment', children: [{ type: 'lookup', field: 'recipient' }] }, { type: 'input', field: 'caption' }],
    })
    state(wrapper).setValue('recipient', { id: 'uncommitted-person' })
    state(wrapper).setValue('caption', 'ordinary change')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toMatchObject([{ recipient: 'original-person' }, { recipient: 'original-person' }, { recipient: 'original-person' }])
  })
  it('synchronizes derived values into the existing native form API', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'input', field: 'caption' }, { type: 'inputNumber', field: 'amount' }],
      options: { onChange: '$FNX:if (field === "caption") api.setValue("amount", 42)' },
    })
    await nextTick(); await nextTick()
    state(wrapper).setValue('caption', 'filled')
    await nextTick(); await nextTick()
    const form = wrapper.findComponent({ name: 'BatchDesignedForm' }).vm as unknown as { nativeApi: { getValue(field: string): unknown }; ready: boolean }
    expect(form.ready).toBe(true)
    expect(form.nativeApi.getValue('amount')).toBe(42)
  })
  it('preserves typed Assign intent and renders an outside backfill after a real Lookup model event', async () => {
    const config = { allowUser: true, allowRole: false, assigneeField: 'recipient' }
    const editor = ref<InstanceType<typeof AssignmentEditor>>()
    const Lookup = defineComponent({ emits: ['update:modelValue'], setup(_props, { emit }) {
      return () => h('button', { class: 'choose-person', onClick: () => emit('update:modelValue', { id: 'new-person', email: 'synthetic@example.invalid' }) }, 'Choose')
    } })
    const wrapper = workspace({ showAssign: true, assignmentConfig: config,
      runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onChange: '$FNX:if (field === "recipient") api.setValue("caption", value.email)' },
      rules: [{ type: 'miAssignment', children: [{ type: 'lookup', field: 'recipient' }] }, { type: 'input', field: 'caption' }],
      assignmentHasChanges: () => editor.value?.hasChanges() === true,
      collectAssignment: (selected: Array<Record<string, unknown>>) => editor.value!.collect(selected),
    }, { assignment: ({ rule, rows: selected, eventContext }: { rule: Record<string, any>; rows: Array<Record<string, unknown>>; eventContext: BatchEventContext }) => h(AssignmentEditor, { ref: editor, rule, rows: selected, eventContext,
      capability: { batchControlId: 'grid', bindingId: '1', assignmentConfig: config },
      rowIdentity: (row: Record<string, unknown>) => String(row.key), rowScopeKey: (row: Record<string, unknown>) => ({ key: row.key }),
      createDirectory: (): BatchAssignmentDirectory => ({ error: ref(''), businessUnits: ref([]), userOptions: {}, userLoading: {}, report: () => {},
        loadBusinessUnits: async () => {}, requireBusinessUnitCode: value => value, requireBusinessUnitId: value => value,
        loadRoles: async () => [], searchUsers: () => {}, loadUsers: async ids => ids.map(id => ({ id, username: id, name: id })) }),
    }) }, { [batchLookupComponentKey as symbol]: Lookup })
    await flushPromises()
    await wrapper.findAll('[role="radio"]')[0]!.trigger('click')
    await flushPromises()
    await wrapper.find('.choose-person').trigger('click')
    await flushPromises()
    const sessionState = wrapper.vm as unknown as { eventSessions: Array<{ data: Record<string, unknown> }> }
    expect(sessionState.eventSessions[0]!.data.caption).toBe('synthetic@example.invalid')
    const main = wrapper.findAllComponents({ name: 'BatchDesignedForm' })[0]!.vm as unknown as { nativeApi: { getValue(field: string): unknown } }
    expect(main.nativeApi.getValue('caption')).toBe('synthetic@example.invalid')
    expect((wrapper.findAllComponents({ name: 'BatchDesignedForm' })[0]!.find('input').element as HTMLInputElement).value).toBe('synthetic@example.invalid')
    expect(editor.value!.hasChanges()).toBe(true)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply-workspace')![0]![3]).toMatchObject({ operation: 'ASSIGN' })
  })
  it('blocks script ownership writes inside conditional rules', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      assignmentConfig: { allowUser: true, allowRole: false, assigneeField: 'recipient' },
      options: { onCreated: '$FNX:api.setValue("recipient", "forged-person")' },
      rules: [{ type: 'input', field: 'caption', control: [{ value: 'A', rule: [{ type: 'input', field: 'recipient' }] }] }],
    })
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
  })
  it.each(['readonly', 'disabled'])('honors a top-level designer %s lock independently of DW parsers', async key => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'input', field: 'caption', [key]: true }, { type: 'inputNumber', field: 'amount' }],
    })
    state(wrapper).setValue('caption', 'forged')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('focuses the real input requested by an event script', async () => {
    const { ElInput } = await vi.importActual<typeof import('element-plus')>('element-plus')
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'input', field: 'caption' }, { type: 'input', field: 'amount' }],
      options: { onChange: '$FNX:if(field === "caption") api.setFocus("amount")' },
    }, {}, {}, { ElInput }, document.body)
    try {
      await flushPromises(); state(wrapper).setValue('caption', 'focus'); await flushPromises()
      expect(document.activeElement).toBe(wrapper.findAll('input')[1]!.element)
    } finally { wrapper.unmount() }
  })
  it('restores designer options in the native Select after resetOptions', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'input', field: 'caption' }, { type: 'select', field: 'active', options: [{ label: 'Original', value: true }] }],
      options: { onChange: '$FNX:if(field === "caption") { if(value === "dynamic") api.setOptions("active",[{label:"Dynamic",value:false}]); else api.resetOptions("active"); }' },
    })
    await flushPromises(); state(wrapper).setValue('caption', 'dynamic'); await flushPromises()
    const native = wrapper.findComponent({ name: 'BatchDesignedForm' }).vm as unknown as { nativeApi: { getRule(field: string): { options: unknown[] } } }
    expect(native.nativeApi.getRule('active').options).toEqual([{ label: 'Dynamic', value: false }])
    state(wrapper).setValue('caption', 'restore'); await flushPromises()
    expect(native.nativeApi.getRule('active').options).toEqual([{ label: 'Original', value: true }])
  })
  it('cancels the native fetch chain when beforeFetch returns false', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler), options: { beforeFetch: '$FNX:return false' } })
    await flushPromises()
    await expect(wrapper.findComponent({ name: 'FormCreate' }).props('option').beforeFetch({}, {})).rejects.toBe(false)
    wrapper.unmount()
  })
  it('rejects the native fetch chain and rolls back writes when its script throws', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { beforeFetch: '$FNX:api.setValue("caption","partial"); throw new Error("Fetch rejected")' },
    })
    await flushPromises()
    await expect(wrapper.findComponent({ name: 'FormCreate' }).props('option').beforeFetch({}, {})).rejects.toThrow('Fetch rejected')
    const context = wrapper.vm as unknown as { eventSessions: Array<{data: Record<string, unknown>}> }
    expect(context.eventSessions[0]!.data.caption).toBe('A')
    wrapper.unmount()
  })
  it.each(['hidden', 'display'])('shows an initially %s-hidden field from an initialization script', async kind => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{type:'input',field:'caption',[kind]:kind === 'hidden'}, {type:'inputNumber',field:'amount'}],
      options: { onCreated: '$FNX:api.'+kind+'(false,"caption")' },
    })
    await flushPromises()
    expect(wrapper.findAll('input')).toHaveLength(2)
    expect(wrapper.findAll('input')[0]!.isVisible()).toBe(true)
    wrapper.unmount()
  })
  it('renders writes made by the native beforeFetch callback immediately', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { beforeFetch: '$FNX:api.setValue("caption", "fetched")' },
    })
    await flushPromises()
    const form = wrapper.findComponent({ name: 'FormCreate' })
    form.props('option').beforeFetch({}, {})
    await flushPromises()
    expect((wrapper.findAll('input')[0]!.element as HTMLInputElement).value).toBe('fetched')
  })
  it('restores the real Element Plus numeric input after disabling it', async () => {
    const { ElInputNumber, ElInput } = await vi.importActual<typeof import('element-plus')>('element-plus')
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onChange: '$FNX:if(field === "caption") api.disabled(value === "lock", "amount")' },
    }, {}, {}, { ElInputNumber, ElInput })
    await flushPromises()
    state(wrapper).setValue('caption', 'lock'); await flushPromises()
    expect((wrapper.find('.el-input-number input').element as HTMLInputElement).disabled).toBe(true)
    state(wrapper).setValue('caption', 'unlock'); await flushPromises()
    expect((wrapper.find('.el-input-number input').element as HTMLInputElement).disabled).toBe(false)
  })
  it('restores a native control after scripts disable and then enable it', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onChange: '$FNX:if(field === "caption") api.disabled(value === "lock", "amount")' },
    })
    await flushPromises()
    state(wrapper).setValue('caption', 'lock'); await flushPromises()
    const native = wrapper.findComponent({ name: 'BatchDesignedForm' }).vm as unknown as { nativeApi: { getRule(field: string): { props: { disabled: boolean } } } }
    expect(native.nativeApi.getRule('amount').props.disabled).toBe(true)
    state(wrapper).setValue('caption', 'unlock'); await flushPromises()
    expect(native.nativeApi.getRule('amount').props.disabled).toBe(false)
    expect((wrapper.findAll('input')[1]!.element as HTMLInputElement).disabled).toBe(false)
  })
  it('re-runs the same role change after role/person/role mode switching', async () => {
    const config = { allowUser: true, allowRole: true, assigneeField: 'recipient', buField: 'org', roleField: 'role' }
    const editor = ref<InstanceType<typeof AssignmentEditor>>()
    const Lookup = defineComponent({ emits: ['update:modelValue'], setup(_props, { emit }) {
      return () => h('button', { class: 'choose-mode-person', onClick: () => emit('update:modelValue', { id: 'person', email: 'person@example.invalid' }) }, 'Choose')
    } })
    const wrapper = workspace({ showAssign: true, assignmentConfig: config,
      runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onChange: '$FNX:if(field === "recipient") api.setValue("caption", "person"); if(field === "role") api.setValue("caption", "role:"+value);' },
      rules: [{ type: 'miAssignment', children: [{ type: 'lookup', field: 'recipient' }] }, { type: 'input', field: 'caption' }],
    }, { assignment: ({ rule, rows: selected, eventContext }: { rule: Record<string, any>; rows: Array<Record<string, unknown>>; eventContext: BatchEventContext }) => h(AssignmentEditor, { ref: editor, rule, rows: selected, eventContext,
      capability: { batchControlId: 'grid', bindingId: '1', assignmentConfig: config },
      rowIdentity: (row: Record<string, unknown>) => String(row.key), rowScopeKey: (row: Record<string, unknown>) => ({ key: row.key }),
      createDirectory: (): BatchAssignmentDirectory => ({ error: ref(''), businessUnits: ref([]), userOptions: {}, userLoading: {}, report: () => {},
        loadBusinessUnits: async () => {}, requireBusinessUnitCode: value => value, requireBusinessUnitId: value => value,
        loadRoles: async () => [], searchUsers: () => {}, loadUsers: async ids => ids.map(id => ({ id, username: id, name: id })) }),
    }) }, { [batchLookupComponentKey as symbol]: Lookup })
    await flushPromises()
    await wrapper.findAll('[role="radio"]')[1]!.trigger('click'); await flushPromises()
    state(wrapper).setValue('role', 'role-A'); await flushPromises()
    await wrapper.findAll('[role="radio"]')[0]!.trigger('click'); await flushPromises()
    await wrapper.find('.choose-mode-person').trigger('click'); await flushPromises()
    await wrapper.findAll('[role="radio"]')[1]!.trigger('click'); await flushPromises()
    state(wrapper).setValue('role', 'role-A'); await flushPromises()
    const main = wrapper.findAllComponents({ name: 'BatchDesignedForm' })[0]!.vm as unknown as { nativeApi: { getValue(field: string): unknown } }
    expect(main.nativeApi.getValue('caption')).toBe('role:role-A')
    wrapper.unmount()
  })
  it('rolls back every assignment field and row if a mode-clear event fails', async () => {
    const source = rows.map(row => ({ ...row, recipient: 'person', role: 'pool' }))
    const wrapper = workspace({ rows: source,
      assignmentConfig: { allowUser: true, allowRole: true, assigneeField: 'recipient', roleField: 'role' },
      runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{type:'miAssignment',children:[{type:'input',field:'recipient'},{type:'input',field:'role'}]}, {type:'input',field:'caption'}],
      options: { onChange: '$FNX:if(field === "recipient") { api.setValue("caption","cleared"); if(api.getValue("key") === "two") throw new Error("clear rejected"); }' },
    })
    const context = wrapper.vm as unknown as { sharedEventContext: BatchEventContext; eventSessions: Array<{data: Record<string, unknown>}> }
    expect(context.sharedEventContext.clearAssignment()).toBe(false)
    expect(context.eventSessions.map(session=>session.data)).toEqual(source.slice(0,2))
    await state(wrapper).apply(); expect(wrapper.emitted('apply')).toBeUndefined()
    wrapper.unmount()
  })
  it('does not clear a failed initialization by editing an unrelated field', async () => {
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onCreated: '$FNX:throw new Error("Synthetic initialization failure")' },
    })
    state(wrapper).setValue('caption', 'must not bypass initialization')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('apply-workspace')).toBeUndefined()
  })
  it('honors disabled layout ancestors when collecting user input', async () => {
    const wrapper = workspace({ rules: [{ type: 'elCard', props: { disabled: true }, children: [{ type: 'input', field: 'caption', title: 'Caption' }] }] })
    state(wrapper).setValue('caption', 'must not write')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('starts unchanged, including fields with designer defaults', async () => {
    const wrapper = workspace({ defaultValueForField: () => 'default' })
    expect(state(wrapper).values).toEqual({})
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(wrapper.emitted('update:visible')).toEqual([[false]])
  })
  it('fills once and changes only selected rows, preserving hidden values', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('caption', 'Shared')
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toEqual(rows.map((row, i) => i < 2 ? { ...row, caption: 'Shared' } : row))
    expect(wrapper.emitted('apply')![0]![1]).toMatchObject({ uniformPatches: [{ rowIds: ['one', 'two'], fields: { caption: { kind: 'set', value: 'Shared' } } }] })
  })
  it('preserves zero and false as explicit changes', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', 0); state(wrapper).setValue('active', false)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toMatchObject([{ amount: 0, active: false }, { amount: 0, active: false }, { amount: 3, active: true }])
  })
  it('distinguishes a cleared field from untouched fields', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', null)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')![0]![0]).toMatchObject([{ caption: 'A', amount: null }, { caption: 'B', amount: null }, { caption: 'C', amount: 3 }])
  })
  it('rejects clearing a required field atomically', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('caption', null)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
    expect(ElMessage.error).toHaveBeenCalledWith('Error: subTable.batchInvalidField')
  })
  it('does not change protected fields even if an update event is forged', async () => {
    const wrapper = workspace({ fieldWriteModes: { caption: 'READONLY' }, fieldDefinitions: [{ fieldName: 'amount', isPrimaryKey: true }] })
    state(wrapper).setValue('caption', 'bad'); state(wrapper).setValue('amount', 8)
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('passes only the outer selection to assignment collection', async () => {
    const collect = vi.fn(selected => ({ rows: selected, change: undefined }))
    const wrapper = workspace({ showAssign: true, collectAssignment: collect, assignmentHasChanges: () => true })
    await state(wrapper).apply()
    expect(collect).toHaveBeenCalledWith(rows.slice(0, 2))
  })
  it('allows ordinary edits when an untouched assignment block is not mounted in a lazy tab', async () => {
    const collect = vi.fn(() => { throw new Error('editor is not mounted') })
    const wrapper = workspace({ showAssign: true, collectAssignment: collect, assignmentHasChanges: () => false })
    state(wrapper).setValue('caption', 'Ordinary edit')
    await state(wrapper).apply()
    expect(collect).not.toHaveBeenCalled()
    expect(wrapper.emitted('apply')).toHaveLength(1)
  })
  it('rejects changes to a selected record while the form is open', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', 7)
    await wrapper.setProps({ rows: rows.map((row, i) => i === 0 ? { ...row, amount: 99 } : row) })
    await state(wrapper).apply()
    expect(wrapper.emitted('apply')).toBeUndefined()
  })
  it('preserves concurrent updates to unselected rows', async () => {
    const wrapper = workspace()
    state(wrapper).setValue('amount', 7)
    await wrapper.setProps({ rows: rows.map((row, i) => i === 2 ? { ...row, caption: 'Other edit' } : row) })
    await state(wrapper).apply()
    expect((wrapper.emitted('apply')![0]![0] as typeof rows)[2]!.caption).toBe('Other edit')
  })
  it('keeps designed field columns instead of flattening their spans', () => {
    const wrapper = workspace({ rules: [
      { type: 'input', field: 'caption', col: { span: 12 } },
      { type: 'inputNumber', field: 'amount', col: { span: 12, offset: 0 } },
    ] })
    expect(wrapper.findAll('[span="12"]')).toHaveLength(2)
  })
  it('renders the configured layout without row tables or internal checkboxes', () => {
    const wrapper = workspace({ rules: [{ type: 'elCard', props: { header: 'Designed card' }, children: [{ type: 'input', field: 'caption', title: 'Caption' }, { type: 'miAssignment' }] }] })
    expect(wrapper.findComponent({ name: 'BatchDesignedForm' }).exists()).toBe(true)
    expect(wrapper.findAll('input')).toHaveLength(1)
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('[type="checkbox"]').exists()).toBe(false)
  })
})
