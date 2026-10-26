import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { loginViaDwPassword } from './playwright-login.mjs'
const phase = process.argv[2]
assert(['red','green'].includes(phase))
const origin = 'http://localhost:3000'
const stateFile = '/tmp/action-round4-ui-fixtures.json'
const output = 'frontend/developer-workstation/verification-screenshots'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({viewport:{width:1480,height:1050}})
const errors=[], failures=[]
let authenticated=false
page.on('pageerror', e=>errors.push(e.message))
page.on('response', r=>{if(authenticated && r.url().startsWith(`${origin}/api/v1/`) && r.status()>=400) failures.push(r.status())})
const samples=await readFile(stateFile,'utf8').then(JSON.parse).catch(e=>{if(e.code==='ENOENT' && phase==='red') return [];throw e})
const persist=()=>writeFile(stateFile,JSON.stringify(samples))
async function api(method,path,data){const r=await page.request.fetch(`${origin}/api/v1${path}`,{method,...(data===undefined?{}:{data})});const body=await r.json();assert(r.ok()&&body.success!==false,`${method} ${path} HTTP ${r.status()}: ${body.message||''}`);return body.data}
const prop=(name,value,prefix='c',tag='property')=>`<${prefix}:${tag} name="${name}" value="${String(typeof value === 'object' ? JSON.stringify(value) : value).replaceAll('"','&quot;')}"/>`
function xmlFor(s){
 const [save,approve,reject,view]=s.actions.map(a=>a.id)
 const normal=prop('formId',s.form.id)+prop('formName',s.form.formName)+prop('assigneeType','INITIATOR')+prop('assigneeLabel','Process Initiator')
 let actions=prop('actionIds',[save,approve])+prop('actionNames',['Save Draft','Approve'])
 if(s.shape==='mixed') actions=prop('actionIds',[save,approve],'l')+prop('actionNames',['Save Draft','Approve'],'l')
 if(s.shape==='whitespace') actions=prop('actionIds',[` ${save} `,approve])+prop('actionNames',['Save Draft','Approve'])
 if(s.shape==='duplicate') actions=prop('actionIds',[save,approve])+prop('actionIds',[view,save],'c','values')
 let extensions=`<c:properties>${normal}${s.shape==='default'?'':actions}</c:properties>`
 if(s.shape==='default') extensions+=`<properties xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><property name="actionIds" value="[${save},${approve}]"/><property name="actionNames" value="[&quot;Save Draft&quot;,&quot;Approve&quot;]"/></properties>`
 return `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" xmlns:l="http://custom.bpmn.io/schema" xmlns:d="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="round4"><b:process id="${s.unit.code}" isExecutable="true"><b:extensionElements><c:properties>${prop('globalActionIds',[reject])}${prop('globalActionNames',['Reject'])}</c:properties></b:extensionElements><b:startEvent id="start"/><b:userTask id="review_a" name="Review legacy"><b:extensionElements>${extensions}</b:extensionElements></b:userTask><b:endEvent id="end"/><b:sequenceFlow id="s1" sourceRef="start" targetRef="review_a"/><b:sequenceFlow id="s2" sourceRef="review_a" targetRef="end"/></b:process><d:BPMNDiagram id="diagram"><d:BPMNPlane id="plane" bpmnElement="${s.unit.code}">${[['start',100,36,36],['review_a',230,120,80],['end',480,36,36]].map(([id,x,w,h])=>`<d:BPMNShape id="shape_${id}" bpmnElement="${id}"><dc:Bounds x="${x}" y="200" width="${w}" height="${h}"/></d:BPMNShape>`).join('')}${[['s1',136,230],['s2',350,480]].map(([id,x,y])=>`<d:BPMNEdge id="edge_${id}" bpmnElement="${id}"><di:waypoint x="${x}" y="220"/><di:waypoint x="${y}" y="220"/></d:BPMNEdge>`).join('')}</d:BPMNPlane></d:BPMNDiagram></b:definitions>`
}
async function inspect(){await page.getByRole('tab',{name:'Process Design',exact:true}).click();await page.locator('.djs-element[data-element-id="review_a"] .djs-hit').first().click();await page.getByTestId('node-action-order').waitFor();await page.getByTestId('node-action-order').scrollIntoViewIfNeeded()}
const names=()=>page.getByTestId('node-action-order').locator('.action-order-name').allTextContents().then(a=>a.map(s=>s.replace(/^\s*\d+\.\s*/, '').trim()))
const expectNames=async n=>page.waitForFunction(n=>JSON.stringify(Array.from(document.querySelectorAll('.action-order-name'),e=>e.textContent.replace(/^\s*\d+\.\s*/, '').trim()))===JSON.stringify(n),n)
async function saveDesign(){const result=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/process'));await page.locator('.process-designer > .designer-toolbar').getByRole('button',{name:'Save',exact:true}).click();assert((await(await result).json()).success)}
try{
 await mkdir(output,{recursive:true});await loginViaDwPassword(page);authenticated=true
 if(!samples.length){const groups=await api('GET','/function-units/my-dev-groups');for(const shape of ['mixed','default','whitespace','duplicate']){
  const unit=await api('POST','/function-units',{name:`Action Round4 QA ${shape} ${Date.now()}`,tags:['action-round4-synthetic'],virtualGroupIds:[groups.publicGroupId]})
  const s={unit,shape,actions:[]};samples.push(s);await persist()
  for(const [actionName,actionType] of [['Save Draft','SAVE'],['Approve','APPROVE'],['Reject','REJECT'],['View Record','FORM_POPUP']])s.actions.push(await api('POST',`/function-units/${unit.id}/actions`,{actionName,actionType,configJson:{}}))
  s.form=await api('POST',`/function-units/${unit.id}/forms`,{formName:'QA task form',formType:'TASK',scene:'TASK',configJson:{rule:[],options:{}}});s.xml=xmlFor(s);await persist()
 }}
 for(const s of samples){
  await api('POST',`/function-units/${s.unit.id}/process`,{bpmnXml:s.xml})
  if(s.shape==='duplicate'){
   const clone=await api('POST',`/function-units/${s.unit.id}/clone?newName=${encodeURIComponent(`Action Round4 QA clone ${phase} ${Date.now()}`)}`)
   s[`${phase}CloneId`]=clone.id;await persist()
   const process=await api('GET',`/function-units/${clone.id}/process`)
   const actions=await api('GET',`/function-units/${clone.id}/actions`)
   const lists=await page.evaluate(xml=>{const d=new DOMParser().parseFromString(xml,'text/xml');return ['property','values'].map(tag=>Array.from(d.getElementsByTagNameNS('*',tag)).filter(e=>e.getAttribute('name')==='actionIds').map(e=>JSON.parse(e.getAttribute('value'))))},process.bpmnXml)
   const describe=ids=>ids.map(id=>actions.find(a=>String(a.id)===String(id))?.actionName||`missing:${id}`)
   assert.deepEqual(describe(lists[0][0]),['Save Draft','Approve'])
   assert.deepEqual(describe(lists[1][0]),phase==='red'?['Save Draft','Approve']:['View Record','Save Draft'])
   console.log(JSON.stringify({phase,shape:s.shape,id:s.unit.id,cloneId:clone.id,property:describe(lists[0][0]),values:describe(lists[1][0])}))
   await page.goto(`${origin}/dev/function-units/${clone.id}`,{waitUntil:'networkidle'});await inspect()
   await expectNames(phase==='red'?['Save Draft','Approve']:['View Record','Save Draft'])
  }else{
   await page.goto(`${origin}/dev/function-units/${s.unit.id}`,{waitUntil:'networkidle'})
   if(s.shape==='mixed'){
    await page.getByRole('tab',{name:'Action Design',exact:true}).click();await page.getByRole('row').filter({hasText:'Save Draft'}).click()
    const result=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/process'))
    await page.getByRole('button',{name:'Save Binding',exact:true}).click();assert((await(await result).json()).success)
   }
   await inspect()
   if(phase==='red'){
    if(s.shape!=='whitespace') assert.deepEqual(await names(),[])
    else{assert((await names())[0].includes(String(s.actions[0].id)));await page.getByTestId('node-action-order').locator('.action-order-controls').last().locator('button').first().click();await page.getByText('One or more bound actions no longer exist', {exact:false}).count()}
   }else{
    await expectNames(['Save Draft','Approve']);assert.deepEqual(await page.locator('.global-action-name').allTextContents(),['Reject'])
    if(s.shape==='whitespace'){
     const order=page.getByTestId('node-action-order');await order.locator('.action-order-name').last().dragTo(order.locator('.action-order-name').first());await expectNames(['Approve','Save Draft'])
     const toolbar=page.locator('.process-designer > .designer-toolbar .el-button-group').first()
     await toolbar.locator('button').nth(3).click();await expectNames(['Save Draft','Approve']);await toolbar.locator('button').nth(4).click();await expectNames(['Approve','Save Draft'])
    }
    await saveDesign();await page.reload({waitUntil:'networkidle'});await inspect();await expectNames(s.shape==='whitespace'?['Approve','Save Draft']:['Save Draft','Approve'])
   }
  }
  await page.screenshot({path:`${output}/2026-10-23_round4-${s.shape}-${phase}.png`,fullPage:true})
  console.log(JSON.stringify({phase,shape:s.shape,id:s.unit.id,result:phase==='red'?'REPRODUCED':'PASS'}))
 }
 if(phase==='green'){assert.deepEqual(errors,[]);assert.deepEqual(failures,[])}
}catch(e){await page.screenshot({path:`${output}/2026-10-23_round4-${phase}-failure.png`,fullPage:true});console.error(JSON.stringify({phase,errors,failures,message:e.message}));throw e}finally{await browser.close()}
