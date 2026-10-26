import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import {readFile,writeFile} from 'node:fs/promises'
import {loginViaDwPassword} from './playwright-login.mjs'
const origin='http://localhost:3000',stateFile='/tmp/action-quoted-clone-fixtures.json'
const samples=JSON.parse(await readFile(stateFile,'utf8')),sample=samples.find(s=>s.quoted)
assert(sample?.unit.name.startsWith('Action Quoted QA'))
const browser=await chromium.launch({headless:true}),page=await browser.newPage({viewport:{width:1480,height:1050}})
const errors=[],requestFailures=[]
const changeLog=`Action-quoted-verified-import-${Date.now()}`
async function api(method,path,data){const r=await page.request.fetch(`${origin}/api/v1${path}`,{method,...(data===undefined?{}:{data})});const b=await r.json();assert(r.ok()&&b.success!==false,`${method} ${path} HTTP ${r.status()}: ${b.message||''}`);return b.data}
async function verify(label){
 const process=await api('GET',`/function-units/${sample.unit.id}/process`),actions=await api('GET',`/function-units/${sample.unit.id}/actions`)
 const lists=await page.evaluate(xml=>{const d=new DOMParser().parseFromString(xml,'text/xml');return ['actionIds','globalActionIds'].map(key=>JSON.parse(Array.from(d.getElementsByTagNameNS('*','property')).find(p=>p.getAttribute('name')===key).getAttribute('value')))},process.bpmnXml)
 const names=lists.map(list=>list.map(id=>actions.find(a=>String(a.id)===String(id).trim())?.actionName||`missing:${id}`))
 assert.deepEqual(names,[['Save Draft','Approve'],['Reject']]);console.log(JSON.stringify({label,id:sample.unit.id,lists,names}))
 await page.goto(`${origin}/dev/function-units/${sample.unit.id}`,{waitUntil:'networkidle'});await page.getByRole('tab',{name:'Process Design',exact:true}).click();await page.locator('.djs-element[data-element-id="review_a"] .djs-hit').first().click()
 await page.getByTestId('node-action-order').waitFor();await page.getByTestId('node-action-order').scrollIntoViewIfNeeded()
 await page.waitForFunction(()=>JSON.stringify(Array.from(document.querySelectorAll('.action-order-name'),e=>e.textContent.replace(/^\s*\d+\.\s*/, '').trim()))===JSON.stringify(['Save Draft','Approve']))
 assert.deepEqual(await page.locator('.global-action-name').allTextContents(),['Reject'])
 await page.screenshot({path:`frontend/developer-workstation/verification-screenshots/2026-10-23_quoted-${label}.png`,fullPage:true})
}
try{
 await loginViaDwPassword(page)
 await page.goto(`${origin}/dev/function-units/${sample.unit.id}`,{waitUntil:'networkidle'})
 await page.getByRole('tab',{name:'Process Design',exact:true}).waitFor()
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push({text:m.text(),path:m.location().url?new URL(m.location().url).pathname:''})});page.on('requestfailed',r=>requestFailures.push({path:new URL(r.url()).pathname,error:r.failure()?.errorText}))
 assert.equal((await api('GET',`/function-units/${sample.unit.id}`)).name,sample.unit.name)
 // Recreate the quoted-ID source using its current catalog IDs, so every run tests quoted snapshots.
 const currentProcess=await api('GET',`/function-units/${sample.unit.id}/process`)
 const quotedXml=await page.evaluate(xml=>{const d=new DOMParser().parseFromString(xml,'text/xml');for(const key of ['actionIds','globalActionIds']){const p=Array.from(d.getElementsByTagNameNS('*','property')).find(p=>p.getAttribute('name')===key);const ids=JSON.parse(p.getAttribute('value'));p.setAttribute('value',JSON.stringify(ids.map((id,index)=>key==='globalActionIds'||index===0?String(id):id)))}return new XMLSerializer().serializeToString(d)},currentProcess.bpmnXml)
 await api('POST',`/function-units/${sample.unit.id}/process`,{bpmnXml:quotedXml})
 const exportResponse=await page.request.get(`${origin}/api/v1/export-import/function-units/${sample.unit.id}/export`);assert(exportResponse.ok());const buffer=await exportResponse.body();assert(buffer.subarray(0,2).equals(Buffer.from('PK')))
 const r=await page.request.post(`${origin}/api/v1/export-import/import?changeLog=${encodeURIComponent(changeLog)}`,{multipart:{file:{name:'quoted-ids.zip',mimeType:'application/zip',buffer}}})
 const b=await r.json();assert(r.ok()&&b.success!==false,`Import HTTP ${r.status()}: ${b.message||''}`);assert.equal(b.data.functionUnitId,sample.unit.id);assert.equal(b.data.versioned,true)
 await verify('imported')
 const versions=await api('GET',`/function-units/${sample.unit.id}/versions`),target=versions.find(v=>v.changeLog===changeLog);assert(target)
 sample.rollbackVersionId=target.id;await writeFile(stateFile,JSON.stringify(samples));await api('POST',`/function-units/${sample.unit.id}/versions/${target.id}/rollback`)
 await verify('rolled-back');console.log(JSON.stringify({errors,requestFailures}));assert.deepEqual(errors,[]);assert.deepEqual(requestFailures,[]);console.log(JSON.stringify({result:'PASS',rollbackVersionId:target.id,consoleErrors:0}))
}finally{await browser.close()}
