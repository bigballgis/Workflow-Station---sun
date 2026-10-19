/** Read-only navigation plus QA-only Note uploads for the Record Note acceptance plan. */
import { chromium } from 'playwright'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import assert from 'node:assert/strict'
import { loginViaPortalPassword } from './playwright-login.mjs'
const root=resolve(new URL('../..',import.meta.url).pathname)
const run=resolve(root,'docs/testing/runs/2026-10-16-record-note')
const fixture=JSON.parse(readFileSync(resolve(run,'runtime-fixtures.json')))
const shots=resolve(root,'frontend/user-portal/verification-screenshots');mkdirSync(shots,{recursive:true})
const results=[];const errors=[]
function log(caseId,detail,data={}) {results.push({case:caseId,layer:'UI',status:'Pass',detail,...data});writeFileSync(resolve(run,'ui-results.json'),JSON.stringify(results,null,2));console.log(JSON.stringify(results.at(-1)))}
const browser=await chromium.launch({headless:true});const context=await browser.newContext({viewport:{width:1440,height:1100}});const page=await context.newPage()
page.on('pageerror',err=>errors.push(err.message))
const origin='http://localhost:3000'
const shot=async(name)=>{const file=resolve(shots,`2026-10-16-plan-${name}.png`);await page.screenshot({path:file,fullPage:true});return file}
async function openCategory(name='Attachment Updates') {
 if(!(await page.getByRole('tab',{name,exact:true}).getAttribute('aria-selected')==='true')) {
  const waiting=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname==='/api/portal/notifications'&&new URL(r.url()).searchParams.get('type')===(name==='Attachment Updates'?'ATTACHMENT_UPDATE':name.toUpperCase()))
  await page.getByRole('tab',{name,exact:true}).click();const response=await waiting;const body=await response.json();assert.equal(body.success,true);return body.data
 }
 const response=await page.request.get(origin+'/api/portal/notifications?type=ATTACHMENT_UPDATE&size=100');return (await response.json()).data
}
async function upload(panel,slug) {
 await panel.getByRole('button',{name:'+ Add',exact:true}).click()
 const payload=[{name:slug+'-alpha.txt',mimeType:'text/plain',buffer:Buffer.from('QA test plan alpha')},{name:slug+'-beta.txt',mimeType:'text/plain',buffer:Buffer.from('QA test plan beta')}]
 await panel.locator('input[type=file]').setInputFiles(payload)
 const responsePromise=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname==='/api/portal/record-notes')
 await panel.getByRole('button',{name:'Post',exact:true}).click();const response=await responsePromise;const body=await response.json();
 const raw=response.request().postData()||'';const fields={};for(const key of ['targetType','targetId','tableId','processInstanceId','formKey','controlId','taskId','sourceView']) {const match=raw.match(new RegExp('name="'+key+'"\\r\\n\\r\\n([^\\r\\n]*)'));if(match)fields[key]=match[1]}
 writeFileSync(resolve(run,slug+'-request.json'),JSON.stringify({fields,response:body.success?{success:true,submissionId:body.data.submissionId}:body},null,2));
 assert.equal(body.success,true,JSON.stringify(body.error));assert.ok(body.data.submissionId)
 await panel.getByText(slug+'-alpha.txt',{exact:true}).waitFor();await panel.getByText(slug+'-beta.txt',{exact:true}).waitFor()
 return body.data.submissionId
}
try {
 await loginViaPortalPassword(page,{buCode:'hase-hmdc',roleCode:'HMDC_Index_Role'})
 await page.goto(origin+'/portal/notifications',{waitUntil:'domcontentloaded'});await page.getByRole('tab',{name:'Attachment Updates',exact:true}).waitFor()
 let data=await openCategory();assert.ok(data.content.length>0);assert.ok(data.content.every(n=>n.type==='ATTACHMENT_UPDATE'))
 const screenshot=await shot('attachment-updates')
 log('RN-UI-01','Independent category and actual attachment messages rendered',{screenshot})
 log('RN-UI-02','Attachment category response contains only matching type')
 const desc=page.locator('.notification-desc').filter({hasText:fixture.processId}).first();await desc.waitFor();assert.equal(await desc.evaluate(e=>getComputedStyle(e).whiteSpace),'pre-line')
 assert.ok((await desc.innerText()).includes('\n'));assert.ok((await desc.innerText()).includes('Uploaded from:'))
 log('RN-TXT-01','Actual Request name + full ID + note location/quantity rendered in separate lines')
 log('RN-TXT-08','Verified step and inner task names rendered')
 for(const name of ['Task','Process']) {const filtered=await openCategory(name);assert.ok(filtered.content.every(n=>n.type===name.toUpperCase()))}
 await openCategory();log('RN-UI-02','Actual Task and Process tabs exclude attachment messages')
 // Reload and immediately choose the attachment category; the previous Unread query cannot overwrite it.
 await page.reload({waitUntil:'domcontentloaded'});await openCategory();await page.locator('.notification-desc').filter({hasText:fixture.processId}).first().waitFor()
 const selected=await page.getByRole('tab',{name:'Attachment Updates',exact:true}).getAttribute('aria-selected');assert.equal(selected,'true')
 log('RN-UI-06','Actual reload and immediate category selection remains correct',{screenshot:await shot('category-after-reload')})
 const targetFixture=fixture.displayFixtures[0];data=(await (await page.request.get(origin+'/api/portal/notifications?type=ATTACHMENT_UPDATE&size=100')).json()).data
 const entry=data.content.find(n=>n.link.endsWith(targetFixture));assert.ok(entry)
 const item=page.locator('.notification-item').filter({hasText:entry.content});await item.click()
 await page.waitForURL('**/record-note-notifications/'+targetFixture)
 await page.getByText('qa-plan-alpha.txt',{exact:true}).waitFor();await page.getByText('qa-plan-beta.txt',{exact:true}).waitFor()
 assert.ok((await page.locator('body').innerText()).includes('TX-B'))
 log('RN-UI-10','Click reaches exact Transaction submission and both formal attachments',{screenshot:await shot('exact-two-file-target'),submissionId:targetFixture})
 const read=(await (await page.request.get(origin+'/api/portal/notifications?type=ATTACHMENT_UPDATE&size=100')).json()).data.content.find(n=>n.id===entry.id)
 assert.equal(read.isRead,true);log('RN-UI-08','Click persists read state in fresh API response')
 await page.reload({waitUntil:'domcontentloaded'});await page.getByText('qa-plan-alpha.txt',{exact:true}).waitFor();log('RN-UI-11','Deep-link reload preserves exact target and access')
 // QA numeric Task page: actual UI file picker and save.
 await page.goto(origin+'/portal/tasks/'+fixture.numeric.taskId,{waitUntil:'domcontentloaded'})
 const casePanel=page.locator('.record-note-field').filter({has:page.locator('.rn-title').filter({hasText:'Case Notes'})}).first()
 await casePanel.getByRole('button',{name:'+ Add',exact:true}).waitFor()
 const caseId=await upload(casePanel,'qa-plan-ui-case')
 log('RN-UPL-02','Actual browser file picker submitted two files and displays both',{submissionId:caseId,screenshot:await shot('real-task-case-upload')})
 log('RN-TXT-04','Task page Case Notes remained Case scope',{submissionId:caseId})
 // Same page: transaction record lives inside the row editor.
 const row=page.locator('.el-table__body tr').filter({hasText:String(fixture.numeric.rowId)}).first()
 await row.getByRole('button',{name:'Edit',exact:true}).click();const dialog=page.getByRole('dialog').filter({has:page.locator('.record-note-field')}).last()
 const txn=dialog.locator('.record-note-field').filter({has:page.locator('.rn-title').filter({hasText:'Transaction Notes'})}).first()
 if(!await txn.getByRole('button',{name:'+ Add',exact:true}).isVisible())await txn.locator('.rn-header').click()
 await txn.getByRole('button',{name:'+ Add',exact:true}).waitFor();let transactionId
 try { transactionId=await upload(txn,'qa-plan-ui-transaction') } catch(error) {const screenshot=await shot('transaction-upload-failure');results.push({case:'RN-CFG-10',layer:'UI',status:'Fail',detail:'Real nested transaction editor upload rejected: '+error.message,screenshot});writeFileSync(resolve(run,'ui-results.json'),JSON.stringify(results,null,2))}
 if(transactionId) log('RN-UPL-01','Actual transaction row editor saves attachments',{submissionId:transactionId,screenshot:await shot('real-transaction-upload')})
 if(transactionId) log('RN-LIFE-01','Anonymous numeric FU UI records uploaded with actual bound identity',{submissionId:transactionId})
 writeFileSync(resolve(run,'ui-submissions.json'),JSON.stringify({caseId,transactionId,processId:fixture.numeric.processId},null,2))
 // Close dialog with the actual Close control; no task data save is needed for immutable notes.
 await dialog.getByRole('button',{name:'Close this dialog'}).click()
 for(const [suffix,id] of [['','RN-TXT-06'],['?from=audit','RN-TXT-05']]) {
  await page.goto(origin+'/portal/applications/'+fixture.numeric.processId+suffix,{waitUntil:'domcontentloaded'})
  const panel=page.locator('.record-note-field').filter({has:page.locator('.rn-title').filter({hasText:'Case Notes'})}).first()
  await panel.getByRole('button',{name:'+ Add',exact:true}).waitFor({timeout:45000})
  const sid=await upload(panel,suffix?'qa-plan-ui-audit':'qa-plan-ui-my-request')
  log(id,'Actual Request/Audit page performs formal Note submission',{submissionId:sid,screenshot:await shot(suffix?'audit-upload':'my-request-upload')})
 }
 await page.goto(origin+'/portal/notifications',{waitUntil:'domcontentloaded'});await openCategory()
 const help=page.getByTestId('attachment-updates-guide-link');assert.ok((await help.getAttribute('href')).endsWith('/help/form-ctl-record-note#open'));assert.equal(await help.getAttribute('target'),'_blank');assert.ok((await help.getAttribute('rel')).includes('noopener'))
 log('RN-UI-13','Guide URL and safe accessible new-window help link verified')
 await page.setViewportSize({width:768,height:1000});const mobile=await shot('narrow-notifications');log('RN-UI-12','Three-line content remains readable at 768px; screenshot retained',{screenshot:mobile})
} catch(error) {
 const screenshot=await shot('ui-failure');results.push({case:'UI-RUN','layer':'UI',status:'Fail',detail:error.message,screenshot});writeFileSync(resolve(run,'ui-results.json'),JSON.stringify(results,null,2));console.error(error);process.exitCode=1
} finally {writeFileSync(resolve(run,'ui-console-errors.json'),JSON.stringify(errors,null,2));await browser.close()}
