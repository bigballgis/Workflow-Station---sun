/** BU/Role and person mode events on an isolated synthetic assignment FU. */
import assert from 'node:assert/strict'
import { chromium } from 'playwright'
import { readFileSync,writeFileSync,mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { loginViaDwPassword,loginViaPortalPassword,loginViaAdminPassword } from './playwright-login.mjs'
import { waitForFixtureDeployment } from './fixture-deployment.mjs'
import { selectExistingBatchRolePool } from './batch-verification-pool.mjs'
const statePath=new URL('../../.codex-batch-role-event-state.json',import.meta.url)
const state=JSON.parse(readFileSync(statePath,'utf8'));const mode=process.argv[2]
const origin='http://localhost:3000';const b=await chromium.launch({headless:true})
try {
 const page=await b.newPage({viewport:{width:1600,height:1100}});page.setDefaultTimeout(12000)
 const errors=[];page.on('pageerror',e=>errors.push(e.message))
 const unwrap=async response=>{const body=await response.json();assert.ok(response.ok()&&body.success!==false,body.error?.message||body.message);return body.data??body}
 const dw=async(method,path,data)=>unwrap(await page.request.fetch(origin+'/api/v1'+path,{method,data}))
 await loginViaDwPassword(page);const unit=await dw('GET',`/function-units/${state.unitId}`)
 assert.equal(unit.name,'Batch verification eventroles20261103');assert.ok(unit.tags.includes('batch-verification-synthetic'))
 if(mode==='prepare') {
  for(const form of await dw('GET',`/function-units/${state.unitId}/forms`)) {
   const binding=form.tableBindings.find(x=>x.bindingType==='SUB')
   form.configJson.batchForms[binding.id]={schemaVersion:1,rule:[{type:'miAssignment',children:[
     {type:'input',field:'handler_code',title:'Event person'}, {type:'input',field:'unit_code',title:'Event BU',props:{filterable:true}}, {type:'input',field:'pool_code',title:'Event role'},
   ]},{type:'input',field:'item_label',title:'Assignment event output'},{type:'inputNumber',field:'units',title:'Units'}],options:{onChange:'$FNX:if(field === "handler_code") api.setValue("item_label","person:"+value); if(field === "unit_code") api.setValue("item_label","bu:"+value); if(field === "pool_code") api.setValue("item_label","role:"+value);'}}
   await dw('PUT',`/function-units/${state.unitId}/forms/${form.id}`,form)
  }
  const deploy=await dw('POST',`/function-units/${state.unitId}/deploy`,{environment:'DEVELOPMENT',autoEnable:true,conflictStrategy:'FAIL',changeLog:'Synthetic BU/Role event matrix'})
  await waitForFixtureDeployment(dw,deploy.deploymentId)
  const output=execFileSync(process.execPath,[fileURLToPath(new URL('./start-batch-verification-fixture.mjs',import.meta.url))],{env:{...process.env,FIXTURE_PROCESS_KEY:'batch_verify_eventroles20261103',FIXTURE_PK_STRATEGY:'uuid'},encoding:'utf8'})
  const ready=JSON.parse(output.split(/\r?\n/).find(line=>line.includes('synthetic-tasks')))
  Object.assign(state,ready.tasks[0]);writeFileSync(statePath,JSON.stringify(state,null,2));console.log(JSON.stringify({step:'role-prepared',unitId:state.unitId,taskId:state.taskId}))
 } else if(mode==='portal') {
  await loginViaPortalPassword(page)
  const admin=await b.newPage();await loginViaAdminPassword(admin)
  const pool=await selectExistingBatchRolePool(page,admin)
  const getRows=async()=>{const data=await unwrap(await page.request.get(`${origin}/api/portal/tasks/${state.taskId}/form-data`));const cap=Object.values(data.subTableBatchCapabilities)[0];assert.equal(cap.assignmentConfig.allowRole,true);return data.fieldValues.__subTables__[`dw:${cap.rowDesign.binding.tableName}`]}
  await page.goto(`${origin}/portal/tasks/${state.taskId}`);const grid=page.locator('.sub-table-field').first();await grid.locator('tbody tr').nth(1).waitFor();await page.waitForLoadState('networkidle');const baseline=await getRows()
  await grid.locator('.el-table__body-wrapper tbody tr').first().locator('.el-checkbox').click();await grid.getByRole('button',{name:'Batch Edit',exact:true}).click()
  const dialog=page.locator('.batch-edit-dialog:visible');const field=label=>dialog.locator('.el-form-item').filter({has:page.locator('.el-form-item__label',{hasText:new RegExp('^'+label+'$')})})
  const output=field('Assignment event output').locator('input')
  await dialog.getByRole('radio',{name:/Assign by role/}).click()
  await field('Event role').locator('.el-select__wrapper.is-disabled').waitFor()
  await field('Event BU').locator('.el-cascader input').fill(pool.bu.name)
  await page.locator('.el-cascader__suggestion-item:visible').filter({hasText:pool.bu.name}).first().click()
  await page.waitForFunction(code=>[...document.querySelectorAll('.batch-edit-dialog input')].some(el=>el.value==='bu:'+code),pool.bu.code)
  await field('Event role').locator('.el-select').click();await page.getByRole('option',{name:pool.role.name,exact:true}).click()
  await page.waitForFunction(code=>[...document.querySelectorAll('.batch-edit-dialog input')].some(el=>el.value==='role:'+code),pool.role.code)
  assert.equal(await output.inputValue(),'role:'+pool.role.code)
  // A discarded person selection must not survive returning to role mode.
  await dialog.getByRole('radio',{name:/Assign by person/}).click()
  const person=field('Event person').locator('.el-select');await person.click();await person.locator('input').fill('developer')
  await page.getByRole('option').filter({hasText:/developer|Developer/}).first().click()
  await page.waitForFunction(()=>[...document.querySelectorAll('.batch-edit-dialog input')].some(el=>el.value.startsWith('person:')))
  await dialog.getByRole('radio',{name:/Assign by role/}).click()
  await field('Event BU').locator('.el-cascader input').fill(pool.bu.name);await page.locator('.el-cascader__suggestion-item:visible').filter({hasText:pool.bu.name}).first().click()
  await field('Event role').locator('.el-select').click();await page.getByRole('option',{name:pool.role.name,exact:true}).click()
  await page.waitForFunction(code=>[...document.querySelectorAll('.batch-edit-dialog input')].some(el=>el.value==='role:'+code),pool.role.code)
  mkdirSync(new URL('../user-portal/verification-screenshots/',import.meta.url),{recursive:true})
  await dialog.screenshot({path:fileURLToPath(new URL('../user-portal/verification-screenshots/2026-11-03-batch-role-event-roundtrip.png',import.meta.url))})
  const saved=page.waitForResponse(r=>r.url().endsWith(`/tasks/${state.taskId}/submit`)&&r.request().method()==='POST')
  await dialog.getByRole('button',{name:/Apply/}).click();const response=await saved;await unwrap(response)
  const payload=response.request().postDataJSON();assert.deepEqual(payload.subTableBatchChanges.map(c=>c.operation).sort(),['ASSIGN','EDIT'])
  const rows=await getRows();assert.equal(rows[0].unit_code,pool.bu.code);assert.equal(rows[0].pool_code,pool.role.code);assert.ok(!rows[0].handler_code);assert.equal(rows[0].item_label,'role:'+pool.role.code)
  const strip=row=>Object.fromEntries(Object.entries(row).filter(([key])=>key!=='_wsRowVersion'));assert.deepEqual(strip(rows[1]),strip(baseline[1]));assert.deepEqual(errors,[])
  console.log(JSON.stringify({step:'role-events-passed',unitId:state.unitId,buChange:true,roleChange:true,personChange:true,rolePersonRole:true,unselectedUnchanged:true,pageErrors:0}))
 } else throw Error('Use prepare or portal')
} catch(error) {const page=b.contexts()[0]?.pages()[0];if(page)await page.screenshot({path:fileURLToPath(new URL('../user-portal/verification-screenshots/2026-11-03-batch-role-event-failure.png',import.meta.url)),timeout:5000}).catch(()=>{});throw error} finally {await b.close()}
