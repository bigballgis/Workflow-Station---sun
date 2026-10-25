import {describe,it,expect,vi} from 'vitest'
import type {ActionDefinition} from '@/api/functionUnit'
import {nextTick,ref} from 'vue'
import {useActionConfig} from '@/composables/actionDesigner/useActionConfig'
import {useActionList} from '@/composables/actionDesigner/useActionList'
vi.mock('element-plus',()=>({ElMessage:{success:vi.fn(),error:vi.fn()},ElMessageBox:{confirm:vi.fn()}}))
function setup(){
 const selectedAction=ref<ActionDefinition | null>(null)
 const store={forms:[],fetchActions:vi.fn(async()=>undefined),fetchForms:vi.fn(async()=>undefined),fetchProcess:vi.fn(async()=>undefined),
  createAction:vi.fn(async()=>undefined),deleteAction:vi.fn(async()=>undefined),
  updateAction:vi.fn<(unit:number,id:string|number,payload:Record<string,unknown>)=>Promise<undefined>>(async()=>undefined)}
 const {actionConfig}=useActionConfig({selectedAction,store,loadActionBinding:vi.fn()})
 const list=useActionList({functionUnitId:1,selectedAction,actionConfig,store,t:k=>k,parseActionBindingsFromBpmn:vi.fn()})
 return {list,store,actionConfig}
}
async function switchAndSave(type:string,config:Record<string,unknown>){
 const test=setup()
 test.list.handleSelectAction({id:12,actionName:'A',actionType:type,configJson:config})
 await nextTick();test.list.handleBackToList();await nextTick()
 test.list.handleSelectAction({id:22,actionName:'B',actionType:type,configJson:{}})
 await nextTick();await test.list.handleSaveAction()
 const saved=test.store.updateAction.mock.calls[0][2].configJson as Record<string,unknown>
 return saved
}
describe('review: independent Action editor configurations',()=>{
 it('does not carry composite steps into a newly opened empty Action',async()=>{
  const saved=await switchAndSave('COMPOSITE',{subActions:[101,102],executionOrder:'parallel'})
  expect(saved.subActions??[]).toEqual([])
  expect(saved.executionOrder).not.toBe('parallel')
 })
 it('does not carry withdrawal status restrictions into another Action',async()=>{
  const saved=await switchAndSave('WITHDRAW',{allowedFromStatus:['PENDING']})
  expect(saved.allowedFromStatus??[]).toEqual([])
 })
 it('control: existing reset handles declared defaults',async()=>{
  const saved=await switchAndSave('APPROVE',{requireComment:true,allowedRoles:['finance']})
  expect(saved.requireComment).toBe(false)
  expect(saved.allowedRoles).toEqual([])
 })
 it('keeps a saved payload snapshot when the editor returns to the list',async()=>{
  const test=setup()
  test.list.handleSelectAction({id:12,actionName:'A',actionType:'APPROVE',configJson:{allowedRoles:['finance']}})
  await nextTick();await test.list.handleSaveAction()
  const saved=test.store.updateAction.mock.calls[0][2].configJson as Record<string,unknown>
  test.list.handleBackToList();await nextTick()
  expect(saved.allowedRoles).toEqual(['finance'])
 })
})
