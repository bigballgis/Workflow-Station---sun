import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import {writeFile,mkdir} from 'node:fs/promises'
import {loginViaDwPassword} from './playwright-login.mjs'
const phase=process.argv[2];assert(['red','green'].includes(phase))
const origin='http://localhost:3000',samples=[],evidence=[],errors=[],failures=[]
const browser=await chromium.launch({headless:true}),page=await browser.newPage({viewport:{width:1480,height:1050}})
const state=`/tmp/action-four-${phase}-fixtures.json`,output='frontend/developer-workstation/verification-screenshots'
async function api(method,path,data){
 const r=await page.request.fetch(`${origin}/api/v1${path}`,{method,...(data===undefined?{}:{data})}),b=await r.json()
 assert(r.ok()&&b.success!==false,`${method} ${path}: ${r.status()} ${b.message||''}`);return b.data
}
const persist=()=>writeFile(state,JSON.stringify({samples,evidence}))
const prop=(name,value)=>`<c:property name="${name}" value="${String(typeof value==='object'?JSON.stringify(value):value).replaceAll('&','&amp;').replaceAll('"','&quot;')}"/>`
function diagram(unit,actions,form){
 const [save,approve,reject]=actions
 return `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" targetNamespace="qa"><bpmn:process id="${unit.code}" isExecutable="true"><bpmn:extensionElements><c:properties>${prop('globalActionIds',[reject.id])}${prop('globalActionNames',['Reject'])}</c:properties></bpmn:extensionElements><bpmn:startEvent id="start"/><bpmn:userTask id="review_a" name="Review"><bpmn:extensionElements><c:properties>${prop('formId',form.id)}${prop('formName',form.formName)}${prop('assigneeType','INITIATOR')}${prop('actionIds',[save.id,approve.id])}${prop('actionNames',['Save Draft','Approve'])}</c:properties></bpmn:extensionElements></bpmn:userTask><bpmn:endEvent id="end"/><bpmn:sequenceFlow id="s1" sourceRef="start" targetRef="review_a"/><bpmn:sequenceFlow id="s2" sourceRef="review_a" targetRef="end"/></bpmn:process></bpmn:definitions>`
}
async function edit(name){
 await page.getByRole('row').filter({has:page.getByText(name,{exact:true})}).getByRole('button',{name:'Edit',exact:true}).click()
 await page.locator('.action-editor .action-name').filter({hasText:name}).waitFor()
}
async function back(){await page.getByRole('button',{name:'Back to List',exact:true}).click();await page.locator('.action-list').waitFor()}
async function configIsolation(unit,type,key,config){
 const a=await api('POST',`/function-units/${unit.id}/actions`,{actionName:`${type} A`,actionType:type,configJson:config})
 const b=await api('POST',`/function-units/${unit.id}/actions`,{actionName:`${type} B`,actionType:type,configJson:{}})
 await page.goto(`${origin}/dev/function-units/${unit.id}`,{waitUntil:'networkidle'})
 await page.getByRole('tab',{name:'Action Design',exact:true}).click()
 await edit(a.actionName);await back();await edit(b.actionName)
 const response=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith(`/actions/${b.id}`))
 await page.locator('.action-editor').getByRole('button',{name:'Save',exact:true}).click()
 assert((await response).ok());await page.waitForLoadState('networkidle')
 const saved=(await api('GET',`/function-units/${unit.id}/actions`)).find(x=>x.id===b.id).configJson
 if(phase==='red')assert.deepEqual(saved[key],config[key]);else assert.deepEqual(saved[key],[])
 await page.screenshot({path:`${output}/2026-10-23_four-${type}-${phase}.png`,fullPage:true})
 evidence.push({issue:'R3',type,actionId:b.id,config:saved});await persist();await back()
}
try{
 await mkdir(output,{recursive:true});await loginViaDwPassword(page)
 await page.goto(`${origin}/dev/function-units`,{waitUntil:'networkidle'})
 page.on('pageerror',e=>errors.push(e.message))
 page.on('console',m=>{if(m.type()==='error')errors.push(m.text())})
 page.on('requestfailed',r=>failures.push({path:new URL(r.url()).pathname,error:r.failure()?.errorText}))
 const groups=await api('GET','/function-units/my-dev-groups')
 const unit=await api('POST','/function-units',{name:`Action CRUD QA ${phase} ${Date.now()}`,tags:['action-crud-synthetic'],virtualGroupIds:[groups.publicGroupId]})
 samples.push(unit);await persist();const base=`/function-units/${unit.id}`
 const actions=[]
 for(const [actionName,actionType]of[['Save Draft','SAVE'],['Approve','APPROVE'],['Reject','REJECT']])actions.push(await api('POST',`${base}/actions`,{actionName,actionType,configJson:{}}))
 const form=await api('POST',`${base}/forms`,{formName:'QA task form',formType:'TASK',scene:'TASK',configJson:{rule:[],options:{}}})
 const xml=diagram(unit,actions,form);await api('POST',`${base}/process`,{bpmnXml:xml})
 const clone=await api('POST',`${base}/clone?newName=${encodeURIComponent(`Action CRUD QA clone ${phase} ${Date.now()}`)}`)
 samples.push(clone);await persist()
 const clonedActions=await api('GET',`/function-units/${clone.id}/actions`)
 for(const name of ['Approve','Reject']){
  const action=clonedActions.find(a=>a.actionName===name),r=await page.request.delete(`${origin}/api/v1/function-units/${clone.id}/actions/${action.id}`),body=await r.json()
  if(phase==='red')assert(r.ok()&&body.success!==false)
  else{assert(!r.ok()&&body.success===false);assert((await api('GET',`/function-units/${clone.id}/actions`)).some(a=>a.id===action.id))}
  evidence.push({issue:'R1',unit:clone.id,name,actionId:action.id,status:r.status(),protected:!r.ok()});await persist()
 }
 const approve=actions[1]
 await api('PUT',`${base}/actions/${approve.id}`,{actionName:'Approve invoice',actionType:'APPROVE',configJson:{}})
 const replacement=await api('POST',`${base}/actions`,{actionName:'Approve',actionType:'REJECT',configJson:{}})
 await api('POST',`${base}/process`,{bpmnXml:xml})
 const after=(await api('GET',`${base}/process`)).bpmnXml
 const ids=await page.evaluate(xml=>{const d=new DOMParser().parseFromString(xml,'text/xml');return JSON.parse(Array.from(d.getElementsByTagNameNS('*','property')).find(p=>p.getAttribute('name')==='actionIds').getAttribute('value'))},after)
 assert(ids.map(String).includes(String(phase==='red'?replacement.id:approve.id)))
 assert(!ids.map(String).includes(String(phase==='red'?approve.id:replacement.id)))
 evidence.push({issue:'R2',unit:unit.id,originalId:approve.id,replacementId:replacement.id,ids});await persist()
 await configIsolation(unit,'COMPOSITE','subActions',{subActions:[actions[0].id],executionOrder:'parallel'})
 await configIsolation(unit,'WITHDRAW','allowedFromStatus',{allowedFromStatus:['PENDING']})
 assert.deepEqual(errors,[]);assert.deepEqual(failures,[])
 console.log(JSON.stringify({phase,result:phase==='red'?'REPRODUCED':'PASS',samples:samples.map(s=>s.id),evidence,errors,failures}))
}catch(e){await page.screenshot({path:`${output}/2026-10-23_four-${phase}-failure.png`,fullPage:true});console.error(JSON.stringify({phase,errors,failures,message:e.message}));throw e}finally{await browser.close()}
