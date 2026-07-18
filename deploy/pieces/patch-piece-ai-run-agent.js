#!/usr/bin/env node
// Patches the prewarmed @activepieces/piece-ai run_agent action (runs in Dockerfile).
//
// Two defects for our DeepSeek reasoning-model usage (deepseek-v4-pro via the
// platform "custom" OpenAI-compatible provider):
//
//   A. streamText() sets NO maxOutputTokens, so the provider default applies.
//      DeepSeek counts REASONING tokens against max_tokens, and its default budget
//      is small enough that a reasoning-heavy task (AI-generation DESIGN phase)
//      burns the whole budget on chain-of-thought and stops BEFORE emitting the
//      actual document — the flow then returns a reply with no document markers.
//      Fix: set maxOutputTokens explicitly (32768; DeepSeek v4-pro accepts 65536).
//
//   B. 'reasoning-delta' chunks are appended to the MARKDOWN output alongside real
//      text-deltas, so the parsed "reply" shown in the chat UI starts with pages of
//      chain-of-thought narration. Fix: drop reasoning deltas from the output.
//
// FAIL-LOUD: each pattern must match in at least one run-agent.js copy, and every
// copy must match consistently. If an AP/piece upgrade changes the code, the docker
// build breaks here and the patch must be re-verified against run-agent.js.
'use strict'
const fs = require('fs')
const path = require('path')

const CACHE_ROOT = '/usr/src/app/cache'

// A: insert maxOutputTokens right before the stopWhen line of streamText().
const STOPWHEN_RE = /(\n(\s*)stopWhen: \[\(0, ai_1\.stepCountIs\)\(maxSteps\), \(0, ai_1\.hasToolCall\)\(shared_1\.TASK_COMPLETION_TOOL_NAME\)\],)/
const STOPWHEN_SUB = '\n$2maxOutputTokens: 32768,$1'

// B: empty the reasoning-delta case body (keep the case so the switch stays exhaustive).
const REASONING_RE = /case 'reasoning-delta': \{\s*if \('text' in chunk && typeof chunk\.text === 'string'\) \{\s*outputBuilder\.addMarkdown\(chunk\.text\);\s*\}\s*else if \('delta' in chunk && typeof chunk\.delta === 'string'\) \{\s*outputBuilder\.addMarkdown\(chunk\.delta\);\s*\}\s*break;\s*\}/
const REASONING_SUB = "case 'reasoning-delta': {\n                                    break;\n                                }"

function findRunAgentFiles(dir, out) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, entry.name)
        if (entry.isSymbolicLink()) continue // patch real files only; symlinked trees resolve to them
        if (entry.isDirectory()) {
            findRunAgentFiles(p, out)
        } else if (entry.isFile() && entry.name === 'run-agent.js' && p.includes('piece-ai')) {
            out.push(p)
        }
    }
    return out
}

const files = findRunAgentFiles(CACHE_ROOT, [])
if (files.length === 0) {
    console.error('FATAL: no piece-ai run-agent.js found under ' + CACHE_ROOT
        + ' — prewarm layout changed, re-verify this patch.')
    process.exit(1)
}

let patched = 0
for (const file of files) {
    let source = fs.readFileSync(file, 'utf8')
    const hasStopWhen = STOPWHEN_RE.test(source)
    const hasReasoning = REASONING_RE.test(source)
    if (!hasStopWhen || !hasReasoning) {
        console.error(`FATAL: pattern mismatch in ${file} (stopWhen=${hasStopWhen}, reasoning=${hasReasoning}). `
            + 'piece-ai run-agent.js changed — re-verify this patch before shipping.')
        process.exit(1)
    }
    source = source.replace(STOPWHEN_RE, STOPWHEN_SUB)
    source = source.replace(REASONING_RE, REASONING_SUB)
    fs.writeFileSync(file, source)
    console.log(`patched ${file}`)
    patched++
}
console.log(`piece-ai run-agent patch applied to ${patched} file(s) (maxOutputTokens=32768 + reasoning excluded from output)`)
