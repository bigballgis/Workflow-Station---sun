import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import {readFile,writeFile,mkdir} from 'node:fs/promises'
import {loginViaDwPassword} from './playwright-login.mjs'
const phase=process.argv[2];assert(['red','green'].includes(phase))
const origin='http://localhost:3000',state='/tmp/action-quoted-clone-fixtures.json'
const samples=await readFile(state,'utf8').then(JSON.parse).catch(e=>{if(e.code==='ENOENT'&&phase==='red')return [];throw e})
const browser=await chromium.launch({headless:true}),page=await browser.newPage({viewport:{width:1480,height:1050}})
const errors=[],failures=[];let authenticated=false
page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{if(authenticated&&r.url().startsWith(`${origin}/api/v1/`)&&r.status()>=400)failures.push(r.status())})
const persist=()=>writeFile(state,JSON.stringify(samples))
async function api(method,path,data){const r=await page.request.fetch(`${origin}/api/v1${path}`,{method,...(data===undefined?{}:{data})});const b=await r.json();assert(r.ok()&&b.success!==false,`${method} ${path}: ${r.status()} ${b.message||''}`);return b.data}
const prop=(name,value)=>`<c:property name="${name}" value="${String(typeof value==='object'?JSON.stringify(value):value).replaceAll('"','&quot;')}"/>`
function diagram(s){const [save,approve,reject]=s.actions.map(a=>a.id)
 return `<b:definitions xmlns:b="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:c="http://workflow.platform/schema/custom" xmlns:d="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" targetNamespace="quoted"><b:process id="${s.unit.code}" isExecutable="true"><b:extensionElements><c:properties>${prop('globalActionIds',[s.quoted?String(reject):reject])}${prop('globalActionNames',['Reject'])}</c:properties></b:extensionElements><b:startEvent id="start"/><b:userTask id="review_a" name="Review clone"><b:extensionElements><c:properties>${prop('formId',s.form.id)}${prop('formName',s.form.formName)}${prop('assigneeType','INITIATOR')}${prop('assigneeLabel','Process Initiator')}${prop('actionIds',[s.quoted?` ${save} `:save,approve])}${prop('actionNames',['Save Draft','Approve'])}</c:properties></b:extensionElements></b:userTask><b:endEvent id="end"/><b:sequenceFlow id="s1" sourceRef="start" targetRef="review_a"/><b:sequenceFlow id="s2" sourceRef="review_a" targetRef="end"/></b:process><d:BPMNDiagram id="diagram"><d:BPMNPlane id="plane" bpmnElement="${s.unit.code}">${[['start',100,36,36],['review_a',230,120,80],['end',480,36,36]].map(([id,x,w,h])=>`<d:BPMNShape id="shape_${id}" bpmnElement="${id}"><dc:Bounds x="${x}" y="200" width="${w}" height="${h}"/></d:BPMNShape>`).join('')}${[['s1',136,230],['s2',350,480]].map(([id,x,y])=>`<d:BPMNEdge id="edge_${id}" bpmnElement="${id}"><di:waypoint x="${x}" y="220"/><di:waypoint x="${y}" y="220"/></d:BPMNEdge>`).join('')}</d:BPMNPlane></d:BPMNDiagram></b:definitions>`}
async function bindingNames(id){const process=await api('GET',`/function-units/${id}/process`),actions=await api('GET',`/function-units/${id}/actions`)
 const ids=await page.evaluate(xml=>{const d=new DOMParser().parseFromString(xml,'text/xml');return ['actionIds','globalActionIds'].map(key=>JSON.parse(Array.from(d.getElementsByTagNameNS('*','property')).find(e=>e.getAttribute('name')===key).getAttribute('value')))},process.bpmnXml)
 return ids.map(list=>list.map(id=>actions.find(a=>String(a.id)===String(id).trim())?.actionName||`missing:${id}`))}
async function inspect(){await page.getByRole('tab',{name:'Process Design',exact:true}).click();await page.locator('.djs-element[data-element-id="review_a"] .djs-hit').first().click();await page.getByTestId('node-action-order').waitFor();await page.getByTestId('node-action-order').scrollIntoViewIfNeeded()}
async function expectNames(names){await page.waitForFunction(names=>JSON.stringify(Array.from(document.querySelectorAll('.action-order-name'),e=>e.textContent.replace(/^\s*\d+\.\s*/, '').trim()))===JSON.stringify(names),names)}
try{await mkdir('frontend/developer-workstation/verification-screenshots',{recursive:true});await loginViaDwPassword(page);authenticated=true
 if(!samples.length){const groups=await api('GET','/function-units/my-dev-groups');for(const quoted of [false,true]){
  const unit=await api('POST','/function-units',{name:`Action Quoted QA ${quoted?'quoted':'numeric'} ${Date.now()}`,tags:['action-quoted-synthetic'],virtualGroupIds:[groups.publicGroupId]})
  const s={unit,quoted,actions:[]};samples.push(s);await persist()
  for(const [actionName,actionType] of [['Save Draft','SAVE'],['Approve','APPROVE'],['Reject','REJECT']])s.actions.push(await api('POST',`/function-units/${unit.id}/actions`,{actionName,actionType,configJson:{}}))
  s.form=await api('POST',`/function-units/${unit.id}/forms`,{formName:'QA task form',formType:'TASK',scene:'TASK',configJson:{rule:[],options:{}}});s.xml=diagram(s);await api('POST',`/function-units/${unit.id}/process`,{bpmnXml:s.xml});await persist()
 }}
 for(const s of samples){
  const clone=await api('POST',`/function-units/${s.unit.id}/clone?newName=${encodeURIComponent(`Action Quoted QA clone ${phase} ${Date.now()}`)}`)
  s[`${phase}CloneId`]=clone.id;await persist();const actual=await bindingNames(clone.id)
  const healthy=[['Save Draft','Approve'],['Reject']]
  if(phase==='red'&&s.quoted){assert(actual[0][0].startsWith('missing:'));assert(actual[1][0].startsWith('missing:'))}else assert.deepEqual(actual,healthy)
  await page.goto(`${origin}/dev/function-units/${clone.id}`,{waitUntil:'networkidle'});await inspect()
  if(phase==='green'||!s.quoted)await expectNames(['Save Draft','Approve'])
  if(phase==='green'){
   const rows=page.getByTestId('node-action-order').locator('.action-order-name');await rows.last().dragTo(rows.first());await expectNames(['Approve','Save Draft'])
   const toolbar=page.locator('.process-designer > .designer-toolbar .el-button-group').first();await toolbar.locator('button').nth(3).click();await expectNames(['Save Draft','Approve']);await toolbar.locator('button').nth(4).click();await expectNames(['Approve','Save Draft'])
   const response=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/process'));await page.locator('.process-designer > .designer-toolbar').getByRole('button',{name:'Save',exact:true}).click();assert((await(await response).json()).success)
   await page.reload({waitUntil:'networkidle'});await inspect();await expectNames(['Approve','Save Draft']);assert.deepEqual(await bindingNames(clone.id),[['Approve','Save Draft'],['Reject']])
   assert.deepEqual(await page.locator('.global-action-name').allTextContents(),['Reject'])
  }
  await page.screenshot({path:`frontend/developer-workstation/verification-screenshots/2026-10-23_quoted-${s.quoted?'quoted':'numeric'}-${phase}.png`,fullPage:true})
  console.log(JSON.stringify({phase,source:s.unit.id,clone:clone.id,quoted:s.quoted,actual,result:phase==='red'&&s.quoted?'REPRODUCED':'PASS'}))
 }
 if(phase==='green'){assert.deepEqual(errors,[]);assert.deepEqual(failures,[])}
}catch(e){await page.screenshot({path:`frontend/developer-workstation/verification-screenshots/2026-10-23_quoted-${phase}-failure.png`,fullPage:true});console.error(JSON.stringify({errors,failures,message:e.message}));throw e}finally{await browser.close()}
