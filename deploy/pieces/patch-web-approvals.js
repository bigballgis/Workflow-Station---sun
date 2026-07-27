#!/usr/bin/env node
// HERMES-PATCH-001 (build-time; docs/ap-integration/HERMES_PATCHES.md)
// Patches the AP web bundle for the offline/allowlist deployment (runs in Dockerfile).
//
// The piece selector's "Approvals" tab (approvals-tab-content.tsx) hardcodes SIX SaaS
// pieces (slack/discord/ms-teams/ms-outlook/gmail/telegram-bot) and renders a skeleton
// until ALL of them load. With an allowlisted piece_metadata those six 404 forever, so
// the tab spins forever and floods the console. The feature ("request approval via a
// Slack/Teams/Gmail message") cannot work in an air-gapped cluster at all, so:
//   A. empty the APPROVAL_PIECES_CONFIG array  -> no more 404 queries, no skeleton
//   B. disable the tab entry                   -> tab hidden from the selector
//
// FAIL-LOUD: each pattern must match exactly once. If an AP image upgrade changes the
// minified code, the docker build breaks here and the patch must be re-verified against
// the new approvals-tab-content.tsx.
'use strict'
const fs = require('fs')
const path = require('path')

const ASSETS_DIR = '/usr/src/app/dist/packages/web/assets'

// A: `<var>=[{pieceName:"@activepieces/piece-slack",approvalActionNames:[...]}, ... ]`
//    anchored on the known first (slack) and last (telegram-bot) entries.
const CONFIG_RE = /=\[\{pieceName:"@activepieces\/piece-slack",approvalActionNames:[\s\S]*?piece-telegram-bot",approvalActionNames:\["request_approval_message"\]\}\]/
// B: `<cond>&&<tabs>.push({value:<enum>.APPROVALS` — replace the leading condition with !1.
const TAB_PUSH_RE = /([A-Za-z_$][\w$]*)&&([A-Za-z_$][\w$]*\.push\(\{value:[A-Za-z_$][\w$]*\.APPROVALS)/

let configHits = 0
let tabHits = 0
for (const file of fs.readdirSync(ASSETS_DIR).filter(f => f.endsWith('.js'))) {
    const filePath = path.join(ASSETS_DIR, file)
    let source = fs.readFileSync(filePath, 'utf8')
    let changed = false
    if (CONFIG_RE.test(source)) {
        source = source.replace(CONFIG_RE, '=[]')
        configHits++
        changed = true
    }
    if (TAB_PUSH_RE.test(source)) {
        source = source.replace(TAB_PUSH_RE, '!1&&$2')
        tabHits++
        changed = true
    }
    if (changed) {
        fs.writeFileSync(filePath, source)
        console.log(`patched ${file}`)
    }
}

if (configHits !== 1 || tabHits !== 1) {
    console.error(`FATAL: expected exactly 1 hit per pattern, got config=${configHits} tabPush=${tabHits}. `
        + 'AP web bundle changed — re-verify against approvals-tab-content.tsx before shipping.')
    process.exit(1)
}
console.log('approvals tab patch applied (config emptied + tab hidden)')
