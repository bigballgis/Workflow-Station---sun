import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import {readFile,writeFile} from 'node:fs/promises'
import {loginViaDwPassword} from './playwright-login.mjs'
const origin='http://localhost:3000',stateFile='/tmp/action-xml-shape-fixtures.json'
const samples=JSON.parse(await readFile(stateFile,'utf8'))
assert.equal(samples.length,4)
const browser=await chromium.launch({headless:true})
const page=await browser.newPage({viewport:{width:1480,height:1050}})
const errors=[],failures=[]
async function api(method,path,data){
 const r=await page.request.fetch(`${origin}/api/v1${path}`,{method,...(data===undefined?{}:{data})})
 const b=await r.json();assert(r.ok()&&b.success!==false,`${method} ${path}: HTTP ${r.status()} ${b.message||''}`);return b.data
}
async function verify(sample,label){
 const xml=(await api('GET',`/function-units/${sample.unit.id}/process`)).bpmnXml
 const actions=await api('GET',`/function-units/${sample.unit.id}/actions`)
 const ids=await page.evaluate(xml=>{
  const d=new DOMParser().parseFromString(xml,'text/xml')
  return ['actionIds','globalActionIds'].map(key=>{
   const p=Array.from(d.getElementsByTagNameNS('*','property')).find(p=>p.getAttribute('name')===key)
   return JSON.parse(p.hasAttribute('value')?p.getAttribute('value'):p.textContent)
  })
 },xml)
 const names=ids.map(list=>list.map(id=>actions.find(a=>String(a.id)===String(id).trim())?.actionName||`missing:${id}`))
 assert.deepEqual(names,[['Save Draft','Approve'],['Reject']])
 await page.goto(`${origin}/dev/function-units/${sample.unit.id}`,{waitUntil:'networkidle'})
 await page.getByRole('tab',{name:'Process Design',exact:true}).click()
 await page.locator('.djs-element[data-element-id="review_a"] .djs-hit').first().click()
 await page.getByTestId('node-action-order').waitFor()
 await page.getByTestId('node-action-order').scrollIntoViewIfNeeded()
 await page.waitForFunction(()=>JSON.stringify(Array.from(document.querySelectorAll('.action-order-name'),e=>e.textContent.replace(/^\s*\d+\.\s*/, '').trim()))===JSON.stringify(['Save Draft','Approve']))
 assert.deepEqual(await page.locator('.global-action-name').allTextContents(),['Reject'])
 await page.waitForLoadState('networkidle')
 await page.screenshot({path:`frontend/developer-workstation/verification-screenshots/2026-10-23_xml-${sample.shape}-${label}.png`,fullPage:true})
 console.log(JSON.stringify({shape:sample.shape,label,id:sample.unit.id,ids,names}))
}
try {
 await loginViaDwPassword(page)
 await page.goto(`${origin}/dev/function-units/${samples[0].unit.id}`,{waitUntil:'networkidle'})
 await page.getByRole('tab',{name:'Process Design',exact:true}).waitFor()
 page.on('pageerror',e=>errors.push(e.message))
 page.on('console',m=>{if(m.type()==='error')errors.push(m.text())})
 page.on('requestfailed',r=>failures.push({path:new URL(r.url()).pathname,error:r.failure()?.errorText}))
 page.on('response',r=>{if(r.url().startsWith(`${origin}/api/v1/`)&&r.status()>=400)failures.push({path:new URL(r.url()).pathname,status:r.status()})})
 for(const sample of samples){
  assert(sample.unit.name.startsWith('Action XML QA'))
  assert.equal((await api('GET',`/function-units/${sample.unit.id}`)).name,sample.unit.name)
  const actions=await api('GET',`/function-units/${sample.unit.id}/actions`)
  const forms=await api('GET',`/function-units/${sample.unit.id}/forms`)
  const xml=await page.evaluate(({xml,shape,actions,form})=>{
   const d=new DOMParser().parseFromString(xml,'text/xml')
   const id=name=>actions.find(a=>a.actionName===name).id
   const values={actionIds:[` ${id('Save Draft')} `,id('Approve')],globalActionIds:[String(id('Reject'))],formId:form.id}
   for(const [key,value]of Object.entries(values)){
    const p=Array.from(d.getElementsByTagNameNS('*','property')).find(p=>p.getAttribute('name')===key)
    const raw=typeof value==='object'?JSON.stringify(value):String(value)
    if(p.hasAttribute('value'))p.setAttribute('value',raw);else p.textContent=raw
   }
   let result=new XMLSerializer().serializeToString(d)
   if(shape==='paired')result=result.replace(/<c:property([^>]*name="(?:globalActionIds|actionIds)"[^>]*)\/>/g,'<c:property$1></c:property>')
   return result
  },{xml:sample.xml,shape:sample.shape,actions,form:forms.find(f=>f.formName==='QA task form')})
  await api('POST',`/function-units/${sample.unit.id}/process`,{bpmnXml:xml})
  const r=await page.request.get(`${origin}/api/v1/export-import/function-units/${sample.unit.id}/export`)
  assert(r.ok());const buffer=await r.body();assert(buffer.subarray(0,2).equals(Buffer.from('PK')))
  const changeLog=`Action-XML-${sample.shape}-${Date.now()}`
  const imported=await page.request.post(`${origin}/api/v1/export-import/import?changeLog=${encodeURIComponent(changeLog)}`,{multipart:{file:{name:'action-xml.zip',mimeType:'application/zip',buffer}}})
  const b=await imported.json();assert(imported.ok()&&b.success!==false,b.message||'Import failed')
  assert.equal(b.data.functionUnitId,sample.unit.id);assert.equal(b.data.versioned,true)
  await verify(sample,'imported')
  const target=(await api('GET',`/function-units/${sample.unit.id}/versions`)).find(v=>v.changeLog===changeLog)
  assert(target);sample.rollbackVersionId=target.id;await writeFile(stateFile,JSON.stringify(samples))
  await api('POST',`/function-units/${sample.unit.id}/versions/${target.id}/rollback`)
  await verify(sample,'rolled-back')
 }
 assert.deepEqual(errors,[]);assert.deepEqual(failures,[])
 console.log(JSON.stringify({result:'PASS',samples:4,errors,failures}))
}finally{await browser.close()}
