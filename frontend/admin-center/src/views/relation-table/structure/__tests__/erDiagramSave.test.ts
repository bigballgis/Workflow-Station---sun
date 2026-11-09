import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ErDiagram from '../er-diagram.vue'
import { relationTableStructureApi, type RelationTableResponse } from '@/api/relationTable'
import { canWrite } from '@/utils/permission'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ back: vi.fn(), push: vi.fn() }), useRoute: () => ({ params: {} }) }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn(), error: vi.fn() },
}))
vi.mock('@/utils/permission', () => ({ canWrite: vi.fn() }))
vi.mock('@/api/relationTable', () => ({ relationTableStructureApi: { list: vi.fn(), update: vi.fn() } }))

/** The diagram editor is replaced by a stub whose v-model the test drives. */
const Editor = defineComponent({ props: ['modelValue', 'tables'], emits: ['update:modelValue'], template: '<div />' })
const Button = defineComponent({ template: '<button @click="$emit(\'click\')"><slot /></button>', emits: ['click'] })

type Field = { id: number; fieldName: string; dataType: string; isForeignKey?: boolean; refTableId?: number; refPrimaryKeyFields?: string[] }
const table = (id: number, canWriteTable: boolean, fields: Field[]) => ({
  id, tableName: `t${id}`, displayName: `T${id}`, status: 'DEPLOYED', canWrite: canWriteTable, fieldDefinitions: fields,
}) as unknown as RelationTableResponse
const pk = (id: number): Field => ({ id, fieldName: 'code', dataType: 'VARCHAR' })
const fk = (id: number, to: number): Field => ({ id, fieldName: 'parent', dataType: 'VARCHAR', isForeignKey: true, refTableId: to, refPrimaryKeyFields: ['code'] })

function mountDiagram() {
  return mount(ErDiagram, { global: { directives: { loading: () => {} }, stubs: {
    PageHeader: { template: '<div><slot name="actions" /></div>' }, ElButton: Button,
    ElCard: { template: '<div><slot /></div>' }, RelationDiagramEditor: Editor,
  } } })
}
const saveButton = (wrapper: ReturnType<typeof mountDiagram>) => wrapper.findAll('button').find(b => b.text() === 'common.save')

describe('ER diagram Save (D-47: read-only callers, tables the caller may not change)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(relationTableStructureApi.update).mockResolvedValue({} as never)
  })

  it('is not offered to a read-only caller', async () => {
    vi.mocked(canWrite).mockReturnValue(false)
    vi.mocked(relationTableStructureApi.list).mockResolvedValue([table(1, false, [pk(11)])])
    const wrapper = mountDiagram(); await flushPromises()
    expect(saveButton(wrapper)).toBeUndefined()
  })

  it('writes only the tables whose foreign keys changed, leaving another team\'s table with a foreign key alone', async () => {
    vi.mocked(canWrite).mockReturnValue(true)
    // t1 (own) and t2 (another team's, with an existing FK to t1) are drawn; t3 is own as well.
    vi.mocked(relationTableStructureApi.list).mockResolvedValue([
      table(1, true, [pk(11)]), table(2, false, [pk(21), fk(22, 1)]), table(3, true, [pk(31), { id: 32, fieldName: 'parent', dataType: 'VARCHAR' }]),
    ])
    const wrapper = mountDiagram(); await flushPromises()
    expect(saveButton(wrapper)).toBeDefined()

    const editor = wrapper.findComponent(Editor)
    const drawn = [...editor.props('modelValue'),
      { sourceTableId: 3, sourceFieldName: 'parent', targetTableId: 1, targetFieldName: 'code', relationType: 'ONE_TO_MANY' }]
    editor.vm.$emit('update:modelValue', drawn); await flushPromises()
    await saveButton(wrapper)!.trigger('click'); await flushPromises()

    expect(relationTableStructureApi.update).toHaveBeenCalledTimes(1)
    expect(vi.mocked(relationTableStructureApi.update).mock.calls[0][0]).toBe(3)
  })

  it('keeps the stored settings the diagram does not draw on a table it writes', async () => {
    vi.mocked(canWrite).mockReturnValue(true)
    const lookupConfig = { refTableId: 1, displayField: 'name' }
    vi.mocked(relationTableStructureApi.list).mockResolvedValue([
      table(1, true, [pk(11)]),
      table(3, true, [
        pk(31),
        { id: 32, fieldName: 'parent', dataType: 'VARCHAR' },
        { id: 33, fieldName: 'owner', dataType: 'LOOKUP', lookupConfig } as unknown as Field,
        { id: 34, fieldName: 'amount', dataType: 'DECIMAL', precision: 12, scale: 2 } as unknown as Field,
        { id: 35, fieldName: 'pair', dataType: 'VARCHAR', isForeignKey: true, refTableId: 1, refPrimaryKeyFields: ['code', 'ver'] },
        { id: 36, fieldName: 'draft', dataType: 'VARCHAR', isForeignKey: true },
      ]),
    ])
    const wrapper = mountDiagram(); await flushPromises()
    const editor = wrapper.findComponent(Editor)
    editor.vm.$emit('update:modelValue', [...editor.props('modelValue'),
      { sourceTableId: 3, sourceFieldName: 'parent', targetTableId: 1, targetFieldName: 'code', relationType: 'ONE_TO_MANY' }])
    await flushPromises()
    await saveButton(wrapper)!.trigger('click'); await flushPromises()

    const [tableId, body] = vi.mocked(relationTableStructureApi.update).mock.calls[0]
    expect(tableId).toBe(3)
    const byName = Object.fromEntries((body.fieldDefinitions ?? []).map(f => [f.fieldName, f]))
    expect(byName.owner.lookupConfig).toEqual(lookupConfig)
    expect(byName.amount).toMatchObject({ precision: 12, scale: 2 })
    expect(byName.pair).toMatchObject({ isForeignKey: true, refTableId: 1, refPrimaryKeyFields: ['code', 'ver'] })
    expect(byName.draft.isForeignKey).toBeUndefined()
    expect(byName.parent).toMatchObject({ isForeignKey: true, refTableId: 1, refPrimaryKeyFields: ['code'] })
  })

  it('is withdrawn once the save would change a table the caller may not change', async () => {
    vi.mocked(canWrite).mockReturnValue(true)
    vi.mocked(relationTableStructureApi.list).mockResolvedValue([
      table(1, true, [pk(11)]), table(2, false, [pk(21), fk(22, 1)]),
    ])
    const wrapper = mountDiagram(); await flushPromises()
    const editor = wrapper.findComponent(Editor)
    editor.vm.$emit('update:modelValue', []); await flushPromises()   // drops t2's foreign key
    expect(saveButton(wrapper)).toBeUndefined()
  })
})
