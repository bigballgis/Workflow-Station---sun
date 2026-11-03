import { describe, expect, it, vi } from 'vitest'
import Workspace from '@platform-shared/batch/BatchWorkspace.vue'
import { mount, flushPromises } from '@vue/test-utils'
import formCreate from '@form-create/element-ui'
import { registerFormCreateReadonlyParser } from '../registerFormCreateReadonlyParser'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { parseFormCreateEventHandler } from '../formCreateEventRuntime'
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) } }))

const passthrough = { template: '<div><slot name="header"/><slot/><slot name="footer"/></div>' }
function workspace(extra: Record<string, unknown>, controls: Record<string, unknown>) {
  return mount(Workspace, { props: { visible: true, rows: [{ key: 'one', caption: 'A', amount: 1 }], selectedRowIds: ['one'],
    fields: [{field:'caption',label:'Caption',type:'input',required:false,options:[]},{field:'amount',label:'Amount',type:'inputNumber',required:false,options:[]}],
    rules: [{type:'input',field:'caption'},{type:'inputNumber',field:'amount'}],
    rowIdentity: row=>String(row.key), defaultValueForField:()=>null, formatError:String, ...extra,
  }, global: { stubs: { ElDialog:passthrough,ElForm:passthrough,ElFormItem:{...passthrough,methods:{validate:()=>Promise.resolve(),clearValidate:()=>{}}},
    ElRow:passthrough,ElCol:passthrough,ElButton:passthrough,ElAlert:true,ElSelect:true,ElOption:true,...controls } } })
}
function state(wrapper: ReturnType<typeof workspace>) { return wrapper.vm as unknown as {setValue(field:string,value:unknown):boolean} }

describe('DW Batch native state under the authoring readonly parser', () => {
  it('can re-enable the real numeric control after script disabled it', async () => {
    registerFormCreateReadonlyParser(formCreate as { parser: (name: string, config: unknown) => void })
    const { ElInputNumber, ElInput } = await vi.importActual<typeof import('element-plus')>('element-plus')
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      options: { onChange: '$FNX:if(field === "caption") api.disabled(value === "lock", "amount")' },
    }, { ElInputNumber, ElInput })
    await flushPromises(); state(wrapper).setValue('caption', 'lock'); await flushPromises()
    expect((wrapper.find('.el-input-number input').element as HTMLInputElement).disabled).toBe(true)
    state(wrapper).setValue('caption', 'unlock'); await flushPromises()
    expect((wrapper.find('.el-input-number input').element as HTMLInputElement).disabled).toBe(false)
  })
  it('keeps designer readonly when script requests enabled', async () => {
    registerFormCreateReadonlyParser(formCreate as { parser: (name: string, config: unknown) => void })
    const { ElInputNumber, ElInput } = await vi.importActual<typeof import('element-plus')>('element-plus')
    const wrapper = workspace({ runRowEvents: createBatchRowEventRunner(parseFormCreateEventHandler),
      rules: [{ type: 'input', field: 'caption' }, { type: 'inputNumber', field: 'amount', props: { readonly: true } }],
      options: { onChange: '$FNX:if(field === "caption") api.disabled(false, "amount")' },
    }, { ElInputNumber, ElInput })
    await flushPromises(); state(wrapper).setValue('caption', 'unlock'); await flushPromises()
    expect((wrapper.find('.el-input-number input').element as HTMLInputElement).disabled).toBe(true)
  })
})
