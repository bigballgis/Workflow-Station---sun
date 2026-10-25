import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import {readFile,writeFile} from 'node:fs/promises'
import {loginViaDwPassword} from './playwright-login.mjs'
const origin='http://localhost:3000'
const stateFile='/tmp/action-round4-ui-fixtures.json'
const samples=JSON.parse(await readFile(stateFile,'utf8'))
const sample=samples.find(s=>s.shape==='duplicate')
assert(sample?.unit.name.startsWith('Action Round4 QA'))
const browser=await chromium.launch({headless:true})
const page=await browser.newPage({viewport:{width:1480,height:1050}})
async function api(method,path,data){const r=await page.request.fetch(`${origin}/api/v1${path}`,{method,...(data===undefined?{}:{data})});const b=await r.json();assert(r.ok()&&b.success!==false,`${method} ${path} HTTP ${r.status()}: ${b.message||''}`);return b.data}
async function verify(label){
 const process=await api('GET',`/function-units/${sample.unit.id}/process`)
 const actions=await api('GET',`/function-units/${sample.unit.id}/actions`)
 const ids=await page.evaluate(xml=>{const d=new DOMParser().parseFromString(xml,'text/xml');return ['property','values'].map(tag=>Array.from(d.getElementsByTagNameNS('*',tag)).filter(e=>e.getAttribute('name')==='actionIds').map(e=>JSON.parse(e.getAttribute('value'))))},process.bpmnXml)
 const describe=ids=>ids.map(id=>actions.find(a=>String(a.id)===String(id))?.actionName||`missing:${id}`)
 assert.deepEqual(describe(ids[0][0]),['Save Draft','Approve']);assert.deepEqual(describe(ids[1][0]),['View Record','Save Draft'])
 console.log(JSON.stringify({label,id:sample.unit.id,property:describe(ids[0][0]),values:describe(ids[1][0]),ids}))
 await page.goto(`${origin}/dev/function-units/${sample.unit.id}`,{waitUntil:'networkidle'})
 await page.getByRole('tab',{name:'Process Design',exact:true}).click()
 await page.locator('.djs-element[data-element-id="review_a"] .djs-hit').first().click()
 await page.getByTestId('node-action-order').waitFor();await page.getByTestId('node-action-order').scrollIntoViewIfNeeded()
 const names=(await page.getByTestId('node-action-order').locator('.action-order-name').allTextContents()).map(s=>s.replace(/^\s*\d+\.\s*/, '').trim())
 assert.deepEqual(names,['View Record','Save Draft'])
 assert.deepEqual(await page.locator('.global-action-name').allTextContents(),['Reject'])
 await page.screenshot({path:`frontend/developer-workstation/verification-screenshots/2026-10-23_round4-${label}.png`,fullPage:true})
}
try{
 await loginViaDwPassword(page)
 const current=await api('GET',`/function-units/${sample.unit.id}`);assert.equal(current.name,sample.unit.name)
 const exported=await page.request.get(`${origin}/api/v1/export-import/function-units/${sample.unit.id}/export`)
 assert(exported.ok());const buffer=await exported.body();assert(buffer.subarray(0,2).equals(Buffer.from('PK')))
 await writeFile('/tmp/action-round4-export.zip',buffer)
 const r=await page.request.post(`${origin}/api/v1/export-import/import?changeLog=Action-round4-verified-import`,{multipart:{file:{name:'action-round4.zip',mimeType:'application/zip',buffer}}})
 const result=await r.json();assert(r.ok()&&result.success!==false,`Import HTTP ${r.status()}: ${result.message||''}`)
 assert.equal(result.data.versioned,true);assert.equal(result.data.functionUnitId,sample.unit.id)
 console.log(JSON.stringify({step:'same-name-import',...result.data}))
 await verify('imported')
 const versions=await api('GET',`/function-units/${sample.unit.id}/versions`)
 const target=versions.find(v=>v.changeLog==='Action-round4-verified-import')
 assert(target,'Import must create a snapshot before replacing the QA source')
 sample.rollbackVersionId=target.id;await writeFile(stateFile,JSON.stringify(samples))
 await api('POST',`/function-units/${sample.unit.id}/versions/${target.id}/rollback`)
 console.log(JSON.stringify({step:'rollback',id:sample.unit.id,versionId:target.id}))
 await verify('rolled-back')
 console.log('PASS: real Export -> same-name Import -> Rollback preserved both Action copies and effective order')
}finally{await browser.close()}
