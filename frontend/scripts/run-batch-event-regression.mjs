/** Local regression over explicitly tagged Batch fixtures, including real DW/Portal UI. */
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
const frontend=fileURLToPath(new URL('../',import.meta.url))
const args=new Set(process.argv.slice(2))
assert.ok([...args].every(arg=>['--unit-only','--ui-only'].includes(arg)) && args.size<=1,'Use --unit-only or --ui-only')
function command(program,argv,cwd=frontend,env=process.env) {
 console.log('[batch-events]',program,...argv)
 const result=spawnSync(program,argv,{cwd,env,stdio:'inherit',shell:process.platform==='win32'&&program==='pnpm',windowsHide:true})
 if(result.error)throw result.error
 assert.equal(result.status,0,'Batch event regression step failed')
}
const script=(name,mode)=>command(process.execPath,[fileURLToPath(new URL(name,import.meta.url)),mode])
if(!args.has('--ui-only')) {
 command('pnpm',['exec','vitest','run','batchNativeEventState','formCreate','batchSubTableConfig','useFormSave.batch','useNativePreviewEvents','--reporter=dot'],fileURLToPath(new URL('../developer-workstation/',import.meta.url)))
 command('pnpm',['exec','vitest','run','formCreate','batchEvent','batchRowEvents','batchAsync','assigneeEmailConfiguration','SubTableBatch','--reporter=dot'],fileURLToPath(new URL('../user-portal/',import.meta.url)))
}
if(!args.has('--unit-only')) {
 for(const name of ['.codex-batch-matrix-state.json','.codex-batch-role-event-state.json','.codex-batch-events-fixture.json','.codex-async-events-fixture.json']) {
  assert.ok(existsSync(new URL('../../'+name,import.meta.url)),`Missing local fixture ${name}; see docs/verification/2026-11-03-batch-form-events-expanded.md`)
 }
 const cooldown=async()=>{console.log('[batch-events] Waiting 60 seconds between authentication groups; gateway rate limits remain enabled.');await new Promise(resolve=>setTimeout(resolve,60000))}
 script('verify-batch-event-matrix.mjs','prepare');script('verify-batch-event-matrix.mjs','dw');script('verify-batch-event-matrix.mjs','portal')
 await cooldown()
 for(const mode of ['prepare','dw','portal']) command(process.execPath,[fileURLToPath(new URL('verify-batch-event-matrix.mjs',import.meta.url)),mode],frontend,{...process.env,BATCH_EVENT_ASYNC:'1'})
 await cooldown()
 script('verify-batch-role-events.mjs','prepare');script('verify-batch-role-events.mjs','portal')
 await cooldown()
 script('verify-batch-form-events.mjs','configure');await cooldown();script('verify-batch-form-events.mjs','verify-ui')
 await cooldown()
 script('verify-batch-async-events.mjs','original');script('verify-batch-async-events.mjs','ui')
 await cooldown();script('verify-batch-async-events.mjs','dw-original')
 await cooldown()
 script('verify-batch-async-events.mjs','guarded');script('verify-batch-async-events.mjs','ui')
 await cooldown();script('verify-batch-async-events.mjs','dw')
}
console.log('[batch-events] PASS')
