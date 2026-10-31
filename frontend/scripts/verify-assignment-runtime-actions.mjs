/** Actual handoff/reopen SFCs in an owned Docker preview; synthetic API only, not business end-to-end acceptance. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync, existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..'), app=resolve(root,'frontend/user-portal')
const generated=resolve(app,'tmp/assignment-actions-preview'), dist=resolve(root,'tmp/assignment-actions-preview-dist')
const screenshots=resolve(app,'verification-screenshots'), marker=resolve(generated,'.owned-preview')
const docker=args=>execFileSync('docker',args,{encoding:'utf8',windowsHide:true}).trim()
if(existsSync(generated)) assert.equal(readFileSync(marker,'utf8'),'assignment-actions-preview')
mkdirSync(generated,{recursive:true}); mkdirSync(screenshots,{recursive:true}); writeFileSync(marker,'assignment-actions-preview')
writeFileSync(resolve(generated,'main.mjs'),`
import {createApp,h,ref} from 'vue'
import {createRouter,createMemoryHistory} from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@/styles/index.scss'
import i18n from '@/i18n'
import AssignmentHandoffAction from '@/components/tasks/AssignmentHandoffAction.vue'
import AssignmentReopenAction from '@/components/AssignmentReopenAction.vue'
const locale=ref('en'), epoch=ref(0)
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{render:()=>null}},{path:'/applications/:id',name:'ApplicationDetail',component:{render:()=>null}}]})
router.push('/')
window.__actions={set(value){i18n.global.locale.value=value;locale.value=value;epoch.value++}}
createApp({setup(){return()=>h('main',{class:'preview'},[
  h('h1','Assignment action verification'),
  h('article',[h('h2','Automatically released holding'),h(AssignmentHandoffAction,{key:'handoff'+epoch.value,taskId:'synthetic-task',enabled:true})]),
  h('article',[h('h2','Ended top-level request'),h(AssignmentReopenAction,{key:'reopen'+epoch.value,processId:'synthetic-source',status:'COMPLETED'})])
])}}).use(router).use(ElementPlus).use(i18n).mount('#app')
`)
writeFileSync(resolve(generated,'index.html'),'<!doctype html><html><head><meta charset="UTF-8"><link rel="icon" href="data:,"><style>body{margin:0;background:#f5f7fa}.preview{padding:24px;max-width:980px;margin:auto}h1{font-size:20px}h2{font-size:14px;color:#64748b}article{margin:24px 0;padding:20px;background:white}</style></head><body><div id="app"></div><script type="module" src="./main.mjs"></script></body></html>')
await createRequire(resolve(app,'package.json'))('vite').build({root:app,configFile:resolve(app,'vite.config.ts'),base:'/portal/',
  build:{outDir:dist,emptyOutDir:false,rollupOptions:{input:resolve(generated,'index.html')}}})
let container,browser
const paths=[],errors=[],operations=[]
try{
  container=docker(['run','-d','--name',`assignment-actions-ui-${Date.now()}`,'-p','127.0.0.1::80','-e','KONG_PROXY_URL=http://127.0.0.1:9',
    '--mount',`type=bind,src=${dist},dst=/usr/share/nginx/html/portal,readonly`,'dev-user-portal-frontend:latest'])
  assert.match(container,/^[a-f0-9]{64}$/)
  const port=docker(['port',container,'80/tcp']).split(':').at(-1)
  browser=await chromium.launch({headless:true}); const page=await browser.newPage({viewport:{width:1100,height:800}})
  page.on('pageerror',error=>errors.push(error.message))
  await page.route('**/api/**',async route=>{
    const url=new URL(route.request().url()), path=url.pathname
    if(path.endsWith('/handoff-availability')) return route.fulfill({json:{success:true,data:{contractVersion:1,resourceId:'synthetic-task',allowed:true,targets:[{id:'synthetic-eligible',label:'Eligible colleague'}]}}})
    if(path.endsWith('/reopen-availability')) return route.fulfill({json:{success:true,data:{contractVersion:1,resourceId:'synthetic-source',allowed:true,targets:[]}}})
    if(path.endsWith('/synthetic-source/reopen')) {
      const request=route.request().postDataJSON(); operations.push(request.operationId)
      return route.fulfill({json:{success:true,data:{operationId:request.operationId,processInstanceId:'synthetic-round',processDefinitionId:'synthetic-frozen',synchronizationState:'PENDING_SYNC'}}})
    }
    errors.push(`Unexpected API ${path}`); return route.abort()
  })
  await page.goto(`http://127.0.0.1:${port}/portal/tmp/assignment-actions-preview/index.html`,{waitUntil:'networkidle'})
  for(const locale of ['en','zh-CN','zh-TW']){
    await page.evaluate(value=>{sessionStorage.clear();window.__actions.set(value)},locale)
    await page.locator('[data-test="assignment-handoff"]').click()
    const dialog=page.locator('.el-dialog:visible'); await dialog.waitFor()
    await page.waitForFunction(()=>[...document.querySelectorAll('.el-overlay')].some(element=>getComputedStyle(element).display!=='none'&&getComputedStyle(element).opacity==='1'&&!element.classList.contains('dialog-fade-enter-active')))
    const label=dialog.locator('.el-form-item__label'), input=dialog.locator('.el-select')
    const bounds=await label.boundingBox(), control=await input.boundingBox()
    assert(bounds && control); assert(bounds.x+bounds.width<=control.x+1)
    assert.equal(await label.evaluate(element=>element.scrollHeight>element.clientHeight),false)
    assert.doesNotMatch(await dialog.innerText(),/task\.assignmentActions|common\./)
    let path=resolve(screenshots,`2026-10-29_assignment-handoff-${locale}.png`)
    await page.screenshot({path,animations:'disabled'});paths.push(path)
    await dialog.locator('.el-dialog__footer button').first().click();await dialog.waitFor({state:'hidden'})
    await page.locator('[data-test="assignment-reopen"]').click()
    const confirm=page.locator('.el-message-box');await confirm.waitFor()
    await page.waitForFunction(()=>{const el=document.querySelector('.el-overlay.is-message-box');return el&&getComputedStyle(el).opacity==='1'&&!el.classList.contains('msgbox-fade-enter-active')})
    assert.doesNotMatch(await confirm.innerText(),/task\.assignmentActions|common\./)
    path=resolve(screenshots,`2026-10-29_assignment-reopen-confirm-${locale}.png`)
    await page.screenshot({path,animations:'disabled'});paths.push(path)
    await confirm.locator('.el-message-box__btns button').last().click();await confirm.waitFor({state:'hidden'})
    await page.waitForFunction(()=>document.querySelector('article:last-child .el-alert'))
    await page.locator('[data-test="assignment-reopen"]').click();await page.waitForTimeout(150)
    assert.equal(operations.at(-1),operations.at(-2))
    path=resolve(screenshots,`2026-10-29_assignment-reopen-pending-${locale}.png`)
    await page.screenshot({path,animations:'disabled'});paths.push(path)
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
  }
  assert.deepEqual(errors,[])
  const result={passed:true,scope:'actual SFCs and synthetic API in owned Docker; not business end-to-end',paths}
  writeFileSync(resolve(root,'tmp/assignment-runtime-actions-ui-result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
}finally{await browser?.close();if(container&&/^[a-f0-9]{64}$/.test(container))docker(['rm','-f',container])}
