import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'
import { auditMetrics } from './verify-1232-form-parity-metrics.mjs'
const browser = await chromium.launch({headless:true})
const session=JSON.parse(readFileSync('../.codex-1232-session.json','utf8'))
if(process.env.ORIGIN) session.state.origins.push({...session.state.origins.find(item=>item.origin==='http://localhost:3000'),origin:process.env.ORIGIN})
const context=await browser.newContext({storageState:session.state,viewport:{width:1700,height:1500}})
const page=await context.newPage(); page.setDefaultTimeout(20000)
const errors=[];page.on('pageerror',e=>errors.push(e.message))
const origin=process.env.ORIGIN??'http://localhost:3000'
const phase=process.env.PHASE??'after'
const out='../.codex-1232-audit-fixture.json'
async function body(response){const b=await response.json();assert(response.ok()&&b.success!==false,JSON.stringify(b));return b.data??b}
try {
 let fixture
 if(process.env.REUSE==='1')fixture=JSON.parse(readFileSync(out,'utf8'))
 else {
 await loginViaDwPassword(page)
 const clone=await body(await page.request.post(`${origin}/api/v1/function-units/50123/clone?newName=${encodeURIComponent('1232 Edit comprehensive audit '+Date.now())}`))
 const base=`${origin}/api/v1/function-units/${clone.id}`
 const forms=await body(await page.request.get(`${base}/forms`))
 const form=forms.find(f=>f.formName.endsWith('Transaction Assignment'))
 const binding=form.tableBindings.find(b=>b.bindingType==='SUB')
 const cfg=form.configJson, sub=cfg.subForms[binding.id]
 const flat=[];const walk=rules=>rules.forEach(r=>{if(r.field)flat.push(r);if(r.children)walk(r.children)});walk(sub.rule)
 const old=name=>structuredClone(flat.find(r=>r.field===name))
 const input=(field,title,props={})=>({type:'input',field,title,props})
 const card=old('card_number'),auth=old('authorization_code')
 card.title='Audit left column';auth.title='Audit right column'
 sub.rule=[old('row_id'),
 {type:'elRow',props:{gutter:24},children:[{type:'elCol',props:{span:12},children:[card]},{type:'elCol',props:{span:12},children:[auth]}]},
 {type:'inputNumber',field:'billing_amount',title:'Audit number step 5',props:{step:5,controlsPosition:'right',precision:2}},
 {type:'switch',field:'merchant_credit',title:'Audit switch',props:{activeText:'Audit enabled',inactiveText:'Audit disabled'}},
 {type:'timePicker',field:'dispute_reason',title:'Audit time',props:{placeholder:'Audit time placeholder'}},
 {type:'datePicker',field:'arn',title:'Audit date range',props:{type:'daterange',startPlaceholder:'Audit start',endPlaceholder:'Audit end'}},
 {type:'select',field:'case_type',title:'Audit searchable select',props:{filterable:true},options:[{value:'enabled',label:'Audit enabled option'},{value:'disabled',label:'Audit disabled option',disabled:true}]},
 {type:'elTabs',children:[{type:'elTabPane',props:{label:'Audit tab A',name:'a'},children:[input('incoming_channel','Audit tab field')]},{type:'elTabPane',props:{label:'Audit tab B',name:'b'},children:[input('billing_currency','Audit tab B field')]}]},
 {type:'elCollapse',children:[{type:'elCollapseItem',props:{title:'Audit collapse',name:'a'},children:[input('dispute_currency','Audit collapse field')]}]},
 {type:'fcTitle',props:{title:'Audit section title'}},
 {type:'alert',props:{title:'Audit instruction',type:'info'}},
 input('merchant_name','Audit no word counter',{maxlength:80,showWordLimit:false}),
 input('transaction_status','Audit hidden field',{hidden:true}),
 input('transaction_no','Audit read only',{readonly:true}),
 {type:'elCard',props:{header:'Audit outer card'},children:[{type:'elCard',props:{header:'Audit inner card'},children:[input('dispute_amount','Audit nested field')]}]},
 ]
 await body(await page.request.put(`${base}/forms/${form.id}`,{data:{configJson:cfg,formName:form.formName,formType:form.formType,fieldPermissions:form.fieldPermissions}}))
 const deployment=await body(await page.request.post(`${base}/deploy`,{data:{autoEnable:true,changeLog:'Isolated Edit/Form Design display audit'}}))
 fixture={fuId:clone.id,fuCode:clone.code,formId:form.id,bindingId:binding.id,deployment}
 writeFileSync(out,JSON.stringify(fixture,null,2));console.log('FIXTURE',fixture)
 await page.goto(`${origin}/portal/processes/start/${clone.code}`)
 await page.locator('.sub-table-field').first().getByRole('button',{name:'Add',exact:true}).click()
 let dlg=page.getByRole('dialog').last();await dlg.locator('.el-form-item').filter({hasText:'Card Number'}).locator('input').fill('1232-comprehensive-audit')
 await dlg.getByRole('button',{name:'Save',exact:true}).click();await dlg.waitFor({state:'hidden'})
 const pending=page.waitForResponse(r=>r.url().endsWith(`/${clone.code}/start`)&&r.request().method()==='POST')
 await page.getByRole('button',{name:'Case Submit',exact:true}).click()
 const started=await body(await pending);fixture.pid=started.processInstanceId??started.id
 assert.match(fixture.pid,/^[0-9a-f-]{36}$/)
 fixture.taskId=execFileSync('docker',['exec','platform-postgres-dev','psql','-U','platform_dev','-d','workflow_platform_dev','-Atc',`select id_ from act_ru_task where proc_inst_id_='${fixture.pid}' and name_='Transaction Assignment'`],{encoding:'utf8'}).trim()
 writeFileSync(out,JSON.stringify(fixture,null,2))
 }
 await page.goto(`${origin}/portal/tasks/${fixture.taskId}`)
 await page.getByText('Transaction Assignment',{exact:true}).first().waitFor()
 await page.getByText('Details',{exact:true}).first().waitFor()
 const claim=page.getByRole('button',{name:'Claim',exact:true});if(await claim.isVisible())await claim.click()
 await page.getByRole('button',{name:'Edit',exact:true}).first().click()
 let dlg=page.getByRole('dialog').last();await page.waitForTimeout(600)
 const edit=await dlg.innerText();console.log('EDIT',edit)
 await page.screenshot({path:`user-portal/verification-screenshots/2026-10-31_1232-comprehensive-${phase}-edit.png`,fullPage:true})
 const metrics={edit:{text:edit,range:await dlg.locator('.el-date-editor--daterange').count(),time:await dlg.locator('.el-date-editor--time').count(),tabs:await dlg.locator('.el-tabs').count(),collapse:await dlg.locator('.el-collapse').count(),errors:[...errors]}}
 metrics.edit.presentation=await auditMetrics(page,dlg)
 await dlg.getByRole('button',{name:'Cancel',exact:true}).click()
 await page.getByText('Details',{exact:true}).first().click();dlg=page.getByRole('dialog').last();await page.waitForTimeout(600)
 metrics.details={text:await dlg.innerText(),range:await dlg.locator('.el-date-editor--daterange').count(),time:await dlg.locator('.el-date-editor--time').count(),tabs:await dlg.locator('.el-tabs').count(),collapse:await dlg.locator('.el-collapse').count(),errors:[...errors]}
 console.log('DETAILS',metrics.details.text)
 await page.screenshot({path:`user-portal/verification-screenshots/2026-10-31_1232-comprehensive-${phase}-details.png`,fullPage:true})
 metrics.details.presentation=await auditMetrics(page,dlg)
 writeFileSync(`../.codex-1232-audit-${phase}-ui.json`,JSON.stringify(metrics,null,2))
 console.log('AUDIT',metrics)
 for(const kind of ['edit','details']) {
  const m=metrics[kind].presentation
  assert.equal(m.columnsSameRow,true,kind+' grid columns')
  for(const key of ['range','time','tabs','collapse','nestedCardTitle','sectionTitle'])assert.equal(m[key],1,kind+' '+key)
  assert.equal(m.switchLabels,true,kind+' switch labels')
  assert.equal(Number(m.incrementValue),5,kind+' number step')
  assert.equal(m.searchInputReadOnly,null,kind+' select search')
  assert.match(m.disabledOptionClass,/is-disabled/,kind+' disabled option')
  assert.equal(m.wordCountDisabled,0,kind+' explicit false counter')
  assert.equal(m.sectionFontSize,'24px',kind+' title font size')
  assert.equal(m.defaultSelectPlaceholder,'Select',kind+' native select default')
  assert.equal(m.readOnlyDisabled,true,kind+' readonly')
  assert.deepEqual(metrics[kind].errors,[],kind+' browser errors')
 }
 console.log('PASS full designer/Edit/Details presentation assertions')
}catch(e){console.log('PAGE ERRORS',errors);console.log((await page.locator('body').innerText()).slice(-5000));throw e}finally{await browser.close()}
