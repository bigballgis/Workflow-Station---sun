import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'
const fixture=JSON.parse(readFileSync('../.codex-1232-audit-fixture.json','utf8'))
const session=JSON.parse(readFileSync('../.codex-1232-session.json','utf8'))
const browser=await chromium.launch({headless:true});const context=await browser.newContext({storageState:session.state,viewport:{width:1700,height:1700}});const page=await context.newPage()
const errors=[];page.on('pageerror',e=>errors.push(e.message))
const origin='http://localhost:3000'
async function data(r){const b=await r.json();assert(r.ok()&&b.success!==false,JSON.stringify(b));return b.data??b}
try{
 let taskId
 if(process.env.REUSE==='1')({taskId}=JSON.parse(readFileSync('../.codex-1232-options-fixture.json','utf8')))
 else{
 await loginViaDwPassword(page)
 const base=`${origin}/api/v1/function-units/${fixture.fuId}`
 const forms=await data(await page.request.get(`${base}/forms`));const form=forms.find(f=>f.id===fixture.formId)
 const config=form.configJson, sub=config.subForms[fixture.bindingId];sub.options={...sub.options,form:{...sub.options?.form,labelPosition:'top',labelWidth:180,size:'small'}}
 await data(await page.request.put(`${base}/forms/${form.id}`,{data:{configJson:config,formName:form.formName,formType:form.formType,fieldPermissions:form.fieldPermissions}}))
 const deployment=await data(await page.request.post(`${base}/deploy`,{data:{autoEnable:true,changeLog:'Isolated form-level top labels and small controls verification'}}))
 for(let i=0;i<30;i++){const state=await data(await page.request.get(`${origin}/api/v1/function-units/deployments/${deployment.deploymentId}/status`));if(state.status==='SUCCESS')break;assert.notEqual(state.status,'FAILED',JSON.stringify(state));await page.waitForTimeout(500)}
 await page.goto(`${origin}/portal/processes/start/${fixture.fuCode}`)
 await page.locator('.sub-table-field').first().getByRole('button',{name:'Add',exact:true}).click();let dlg=page.getByRole('dialog').last()
 await dlg.locator('.el-form-item').filter({hasText:'Card Number'}).locator('input').fill('1232-options-top')
 await dlg.getByRole('button',{name:'Save',exact:true}).click();await dlg.waitFor({state:'hidden'})
 const pending=page.waitForResponse(r=>r.url().endsWith(`/${fixture.fuCode}/start`)&&r.request().method()==='POST');await page.getByRole('button',{name:'Case Submit',exact:true}).click()
 const started=await data(await pending),pid=started.processInstanceId??started.id
 assert.match(pid,/^[0-9a-f-]{36}$/)
 taskId=execFileSync('docker',['exec','platform-postgres-dev','psql','-U','platform_dev','-d','workflow_platform_dev','-Atc',`select id_ from act_ru_task where proc_inst_id_='${pid}' and name_='Transaction Assignment'`],{encoding:'utf8'}).trim()
 writeFileSync('../.codex-1232-options-fixture.json',JSON.stringify({taskId,pid,fuId:fixture.fuId},null,2))
 }
 await page.goto(`${origin}/portal/tasks/${taskId}`);await page.getByText('Details',{exact:true}).first().waitFor()
 const claim=page.getByRole('button',{name:'Claim',exact:true});if(await claim.isVisible())await claim.click()
 for(const kind of ['Edit','Details']){
 if(kind==='Edit')await page.getByRole('button',{name:'Edit',exact:true}).first().click();else await page.getByText('Details',{exact:true}).first().click()
 const dlg=page.getByRole('dialog').last();await page.waitForTimeout(600)
 assert(await dlg.locator('.el-form--label-top').count()>0,kind+' top labels')
 assert(await dlg.locator('.el-input--small').count()>0,kind+' small controls')
 await page.screenshot({path:`user-portal/verification-screenshots/2026-10-31_1232-options-${kind.toLowerCase()}.png`,fullPage:true})
 console.log('PASS',kind,'top labels and small controls',taskId)
 await dlg.getByRole('button',{name:'Cancel',exact:true}).click()
 }
 assert.deepEqual(errors,[])
}finally{await browser.close()}
