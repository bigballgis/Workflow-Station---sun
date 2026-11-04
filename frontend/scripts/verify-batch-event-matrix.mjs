/** Real supported-event matrix against an explicitly tagged synthetic FU. No route mocking or Vue state injection. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword, loginViaPortalPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
const origin = 'http://localhost:3000'
const statePath = new URL('../../.codex-batch-matrix-state.json', import.meta.url)
const state = JSON.parse(readFileSync(statePath, 'utf8'))
const mode = process.argv[2]
const asynchronous = process.env.BATCH_EVENT_ASYNC === '1'
function asyncScripts(value) {
 if (typeof value === 'string' && value.startsWith('$FNX:')) return '$FNX:return Promise.resolve().then(function () {' + value.slice(5) + '\n});'
 if (Array.isArray(value)) return value.map(asyncScripts)
 if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, asyncScripts(item)]))
 return value
}
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
const fetchRequests=[]; page.on('request',request=>{if(request.headers()['x-batch-event-test'])fetchRequests.push({url:new URL(request.url()).pathname+new URL(request.url()).search,marker:request.headers()['x-batch-event-test']})});
page.on('console',message=>{if(message.type()==='error'||message.type()==='warn')console.log(JSON.stringify({step:'console',text:message.text()}))});
page.setDefaultTimeout(12000);page.setDefaultNavigationTimeout(15000);
const previewWrites=[];
const errors = []; page.on('pageerror', e => errors.push(e.message))
const record = event => `$FNX:api.setValue("event_trace", (api.getValue("event_trace") || "") + "|${event}");`
const unwrap = async response => { const body = await response.json(); assert.ok(response.ok() && body.success !== false, body.error?.message || body.message || String(response.status())); return body.data ?? body }
const api = async (method, path, data) => unwrap(await page.request.fetch(origin + '/api/v1' + path, { method, data }))
const portal = async path => unwrap(await page.request.get(origin + '/api/portal' + path))
try {
 await loginViaDwPassword(page)
 const unit = await api('GET', `/function-units/${state.unitId}`)
 assert.ok(unit.tags.includes('batch-verification-synthetic') && unit.name === 'Batch verification eventmatrix20261103')
 const forms = await api('GET', `/function-units/${state.unitId}/forms`)
 if (mode === 'prepare') {
  const table = (await api('GET', `/function-units/${state.unitId}/tables`)).find(t => t.tableType === 'SUB')
  const additions = [
   ['event_conditional','VARCHAR',255], ['event_output','VARCHAR',255], ['event_trace','VARCHAR',4000], ['event_choice','VARCHAR',80], ['event_date','DATE',null], ['event_flag','BOOLEAN',null],
  ].filter(([field]) => !table.fieldDefinitions.some(f => f.fieldName === field)).map(([fieldName,dataType,length]) => ({fieldName,dataType,length,nullable:true,displayName:fieldName}))
  if (additions.length) await api('PUT', `/function-units/${state.unitId}/tables/${table.id}`, {tableName:table.tableName,tableType:table.tableType,tableDisplayName:table.tableDisplayName,
    fields:[...table.fieldDefinitions.map(f => ({...f,pkGeneration:f.pkGenerationJson,computedField:f.computedFieldJson})),...additions]})
  state.store = `dw:${table.tableName}`
  for (const form of forms) {
   const binding = form.tableBindings.find(b=>b.bindingType==='SUB')
   const on = Object.fromEntries(['focus','input','blur','click','clear','change'].map(event=>[event,record('input-'+event)]))
   on.change += ` if(value === "ui") { api.setLabel("event_output","Renamed output"); api.disabled(true,"units"); api.required(true,"event_output"); api.hidden(true,"event_date"); api.display(true,"event_flag"); api.setOptions("event_choice",[{label:"Dynamic",value:"dynamic"}]); api.setFormNotification("UI overlay active","INFO","ui"); } else if(value === "restore") { api.resetLabel("event_output"); api.disabled(false,"units"); api.required(false,"event_output"); api.hidden(false,"event_date"); api.display(false,"event_flag"); api.resetOptions("event_choice"); api.clearFormNotification("ui"); api.clearFieldError("event_output"); } else if(value === "error") api.setFieldError("event_output","Synthetic field error"); else if(value === "focus-output") api.setFocus("event_output"); else if(value === "deny") api.setValue("entry_token","forbidden"); else if(value === "throw-row" && api.getValue("units") === 2) throw new Error("Synthetic row failure");`
   const rules = [
    {type:'input',field:'item_label',title:'Event source',props:{clearable:true},control:[{value:'conditional',rule:[{type:'input',field:'event_conditional',title:'Conditional field',hook:{deleted:record('hook-deleted')}}]}],on,hook:Object.fromEntries(['load','mounted','value','watch','titleClick'].map(event=>[event,record('hook-'+event)]))},
    {type:'inputNumber',field:'units',title:'Event number',value:1,props:{min:0,max:100},on:{change:record('number-change')}},
    {type:'input',field:'event_output',title:'Event output',effect:{fetch:{action:`/api/v1/function-units/${state.unitId}`,to:'props.placeholder',parse:'[[FORM-CREATE-PREFIX-function (body) { return "Fetched options"; }-FORM-CREATE-SUFFIX]]'}}},
    {type:'select',field:'event_choice',title:'Event choice',options:[{label:'Original',value:'original'}],on:{change:record('select-change'),visibleChange:record('select-visible')}},
    {type:'datePicker',field:'event_date',title:'Event date',hidden:true,props:{type:'date',valueFormat:'YYYY-MM-DD'},on:{change:record('date-change'),focus:record('date-focus'),blur:record('date-blur'),panelChange:record('date-panel'),calendarChange:record('date-calendar')},hook:{hidden:record('hook-hidden')}},
    {type:'switch',field:'event_flag',title:'Event flag',display:false,value:false,on:{change:record('switch-change')}},
    {type:'input',field:'event_trace',title:'Event trace',props:{type:'textarea',rows:4},effect:{fetch:{action:`/api/v1/function-units/${state.unitId}?batchEventCancelled=1`,to:'props.placeholder'}}},
   ]
   const options = {beforeFetch:record('native-fetch')+'config.headers["X-Batch-Event-Test"] = "verified"; if(config.action.includes("batchEventCancelled")) { api.setValue("event_trace",api.getValue("event_trace")+"|fetch-cancelled"); return false; }',form:{labelPosition:'left',labelWidth:'auto'},onCreated:'$FNX:api.setValue("event_trace","created");api.hidden(false,"event_date");api.display(false,"event_flag");',onMounted:record('form-mounted'),onReload:record('form-reload'),
     onChange:'$FNX:if(field === "item_label") api.setValue("event_trace",(api.getValue("event_trace")||"")+"|form-change");',
     onReset:'$FNX:if(api.getValue("item_label") === "reset-block") throw new Error("Synthetic reset guard"); if(api.getValue("item_label") === "reset-false") return false;',
     beforeSubmit:record('before-submit')+'if(api.getValue("item_label") === "reject") return false;',
     onSubmit:record('form-submit')+'api.setValue("event_output","submitted:"+api.getValue("item_label"));',
   }
   form.configJson.batchForms[binding.id]=asynchronous ? asyncScripts({schemaVersion:1,rule:rules,options}) : {schemaVersion:1,rule:rules,options}
   form.fieldPermissions={...form.fieldPermissions,[`${binding.id}:event_conditional`]:'EDITABLE',...Object.fromEntries(rules.map(r=>[`${binding.id}:${r.field}`,'EDITABLE']))}
   await api('PUT',`/function-units/${state.unitId}/forms/${form.id}`,form)
  }
  const deployment=await api('POST',`/function-units/${state.unitId}/deploy`,{environment:'DEVELOPMENT',autoEnable:true,conflictStrategy:'FAIL',changeLog:'Synthetic full Batch event matrix'})
  await waitForFixtureDeployment(api,deployment.deploymentId)
  const output=execFileSync(process.execPath,[fileURLToPath(new URL('./start-batch-verification-fixture.mjs',import.meta.url))],{env:{...process.env,FIXTURE_PROCESS_KEY:'batch_verify_eventmatrix20261103',FIXTURE_PK_STRATEGY:'uuid'},encoding:'utf8'})
  const ready=output.split(/\r?\n/).find(line=>line.includes('synthetic-tasks'));assert.ok(ready,output)
  state.taskId=JSON.parse(ready).tasks[0].taskId;state.processId=JSON.parse(ready).tasks[0].processInstanceId
  writeFileSync(statePath,JSON.stringify(state,null,2));console.log(JSON.stringify({step:'prepared',...state}))
 } else if(mode==='dw' || mode==='portal') {
  let preview, grid, baseline
  if(mode==='dw') {
   const form=forms.find(f=>f.formType==='TASK')
   await page.goto(`${origin}/dev/function-units/${state.unitId}`)
   await page.getByRole('tab',{name:/Form/}).click()
   await page.getByRole('row').filter({hasText:form.formName}).getByRole('button',{name:'Edit',exact:true}).click()
   await page.locator('.form-editor-view .header-actions').getByRole('button',{name:'Preview',exact:true}).click()
   preview=page.locator('.form-preview-dialog').last();await preview.waitFor();page.on('request',request=>{if(!['GET','HEAD','OPTIONS'].includes(request.method()))previewWrites.push(new URL(request.url()).pathname)})
   await preview.locator('.el-form-item').filter({hasText:'Case key'}).locator('input').first().fill('SYNTHETIC-MATRIX')
   grid=preview.locator('.sub-table-field').first()
   await grid.getByRole('button',{name:'Batch Add',exact:true}).click()
  } else {
   await loginViaPortalPassword(page)
   await page.goto(`${origin}/portal/tasks/${state.taskId}`)
   grid=page.locator('.sub-table-field').first();await grid.locator('.el-table__body-wrapper tbody tr').nth(1).waitFor()
   await page.waitForLoadState('networkidle');baseline=(await portal(`/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store]
   await grid.locator('.el-table__body-wrapper tbody tr .el-checkbox').first().click()
   await grid.getByRole('button',{name:'Batch Edit',exact:true}).click()
  }
  const dialog=page.locator('.batch-edit-dialog:visible');await dialog.waitFor()
  const field=title=>dialog.locator('.el-form-item').filter({has:page.locator('.el-form-item__label',{hasText:new RegExp('^'+title+'$')})})
  const source=field('Event source').locator('input');const trace=field('Event trace').locator('textarea')
  const seen=async token=>{await page.waitForFunction(({token})=>[...document.querySelectorAll('.batch-edit-dialog textarea')].some(el=>el.value.includes(token)),{token},{timeout:5000})}
  await seen('native-fetch');await seen('fetch-cancelled');await page.waitForLoadState('networkidle');assert.ok(!fetchRequests.some(request=>request.url.includes('batchEventCancelled')));assert.ok(fetchRequests.some(request=>request.marker==='verified'));await seen('created');await seen('hook-load');await seen('hook-mounted');await seen('form-mounted');await seen('hook-watch');await field('Event date').waitFor({state:'visible',timeout:5000});await field('Event flag').waitFor({state:'visible',timeout:5000})
  if(mode==='dw') {
   await dialog.getByRole('spinbutton',{name:'Rows to add',exact:true}).fill('2');await source.click()
   await dialog.locator('.batch-preview-row').click();await page.getByRole('option',{name:'Preview row 2',exact:true}).click();await seen('form-reload')
   await dialog.locator('.batch-preview-row').click();await page.getByRole('option',{name:'Preview row 1',exact:true}).click();await seen('form-reload')
  }
  await source.click();await seen('input-focus');await seen('input-click');await field('Event source').locator('.el-form-item__label').click();await seen('hook-titleClick')
  await source.fill('ordinary');await seen('input-input');await source.press('Tab');await seen('input-blur');await seen('input-change');await seen('hook-value');await seen('form-change')
  await source.hover();await field('Event source').locator('.el-input__clear').click();await seen('input-clear')
  await source.fill('ui');await source.press('Tab')
  await field('Renamed output').waitFor();assert.equal(await field('Event number').locator('input').isDisabled(),true)
  assert.equal(await field('Event date').count(),0);await field('Event flag').waitFor({state:'hidden',timeout:5000})
  await dialog.getByText('UI overlay active',{exact:true}).waitFor();await seen('hook-hidden')
  await field('Event choice').locator('.el-select').click();await page.getByRole('option',{name:'Dynamic',exact:true}).click();await seen('select-change');await seen('select-visible')
  await source.fill('restore');await source.press('Tab');await field('Event output').waitFor();await page.waitForFunction(el=>!el.disabled,await field('Event number').locator('input').elementHandle(),{timeout:5000})
  await field('Event date').waitFor();await field('Event flag').waitFor({state:'visible',timeout:5000})
  await field('Event flag').locator('.el-switch').click();await seen('switch-change')
  await field('Event number').locator('input').fill('7');await source.click();await seen('number-change')
  await field('Event choice').locator('.el-select').click();await page.getByRole('option',{name:'Original',exact:true}).click()
  await field('Event date').locator('input').click();await page.locator('.el-picker-panel:visible').getByRole('button',{name:/Next Month/i}).click();await seen('date-panel');await field('Event date').locator('input').fill('2026-11-03');await source.click();await seen('date-change')
  await source.fill('focus-output');await page.waitForFunction(()=>document.activeElement?.closest('.el-form-item')?.textContent?.includes('Event output'))
  await source.fill('error');await source.press('Tab');await dialog.getByText('Synthetic field error',{exact:true}).waitFor()
  await dialog.getByRole('button',{name:/Apply/}).click();assert.equal(await dialog.isVisible(),true)
  await source.fill('restore');await source.press('Tab');assert.equal(await dialog.getByText('Synthetic field error',{exact:true}).count(),0)
  await source.fill('reject');await source.press('Tab');await dialog.getByRole('button',{name:/Apply/}).click();assert.equal(await dialog.isVisible(),true)
  await source.fill('reset-block');await source.press('Tab');await dialog.getByRole('button',{name:'Cancel',exact:true}).click()
  const confirm=page.locator('.el-message-box:visible');if(await confirm.count())await confirm.getByRole('button',{name:/OK|Confirm/}).click()
  await dialog.getByText('Synthetic reset guard',{exact:true}).waitFor()
  await source.fill('reset-false');await source.press('Tab');await dialog.getByRole('button',{name:'Cancel',exact:true}).click()
  const resetConfirm=page.locator('.el-message-box:visible');if(await resetConfirm.count())await resetConfirm.getByRole('button',{name:/OK|Confirm/}).click()
  assert.equal(await dialog.isVisible(),true)
  await source.fill('conditional');await source.press('Tab');await field('Conditional field').waitFor();await source.fill('accepted');await source.press('Tab');await field('Conditional field').waitFor({state:'detached'});await seen('hook-deleted')
  assert.ok((await trace.inputValue()).includes('form-change'))
  const folder=new URL(`../${mode==='dw'?'developer-workstation':'user-portal'}/verification-screenshots/`,import.meta.url);mkdirSync(folder,{recursive:true})
  await dialog.screenshot({path:fileURLToPath(new URL(`2026-11-03-batch-event-matrix-${mode}${asynchronous?'-async':''}.png`,folder))})
  const saved=mode==='portal'?page.waitForResponse(r=>r.url().endsWith(`/tasks/${state.taskId}/submit`)&&r.request().method()==='POST'):undefined
  await dialog.getByRole('button',{name:/Apply/}).click();await dialog.waitFor({state:'hidden'})
  if(saved)assert.equal((await(await saved).json()).success,true)
  if(mode==='portal') {
   const rows=(await portal(`/tasks/${state.taskId}/form-data`)).fieldValues.__subTables__[state.store]
   assert.equal(rows[0].event_output,'submitted:accepted');assert.equal(rows[0].event_choice,'original');assert.equal(rows[0].event_flag,true);assert.match(rows[0].event_trace,/before-submit.*form-submit/)
   const strip=row=>Object.fromEntries(Object.entries(row).filter(([k])=>k!=='_wsRowVersion'))
   assert.deepEqual(strip(rows[1]),strip(baseline[1]))
  } else {assert.match(await grid.innerText(),/accepted/);assert.equal(await grid.locator('.el-table__body-wrapper tbody tr').count(),2);assert.deepEqual(previewWrites,[]);const reloaded=await api('GET',`/function-units/${state.unitId}/forms`);assert.deepEqual(reloaded.find(form=>form.formType==='TASK').configJson,forms.find(form=>form.formType==='TASK').configJson)}
  assert.deepEqual(errors,[])
  console.log(JSON.stringify({step:'matrix-ui-passed',mode,formEvents:['onCreated','onMounted','onChange','onReset','beforeSubmit','onSubmit','beforeFetch',...(mode==='dw'?['onReload']:[])],nativeEvents:['input','focus','blur','click','clear','change','select.visibleChange','switch.change','number.change','date.change','date.panelChange'],overlays:['label','disabled','required','hidden','display','options','notification','focus','fieldError'],pageErrors:errors.length}))
 } else throw Error('Use prepare, dw or portal')
} catch(error) { await page.screenshot({timeout:5000,path:fileURLToPath(new URL(`../developer-workstation/verification-screenshots/2026-11-03-batch-event-matrix-${mode}-failure-${Date.now()}.png`,import.meta.url))}).catch(()=>{}); console.log(JSON.stringify({step:'failure-dom',alerts:await page.locator('.batch-edit-dialog .el-alert').allTextContents(),fetchRequests,inputs:await page.locator('.batch-edit-dialog input,.batch-edit-dialog textarea').evaluateAll(els=>els.map(el=>({value:el.value,placeholder:el.placeholder}))),state:await page.locator('.batch-edit-dialog .el-form-item').evaluateAll(items=>items.map(el=>({text:el.innerText,html:el.outerHTML.slice(0,1800)})))})); throw error } finally {await browser.close()}
