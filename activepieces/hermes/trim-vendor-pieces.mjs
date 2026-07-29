#!/usr/bin/env node
// HERMES-PATCH-013 —— vendor 树 community piece 收敛。
//
// 上游 0.84.0 在 packages/pieces/community/ 下带了 694 个件，而 pnpm-workspace.yaml 的
// `packages/pieces/community/*` 会把它们全部纳入根 pnpm install 的依赖解析。这些件：
//   · 不在 hermes/pieces.json 白名单里 → 没有 piece_metadata 行 → 设计器根本选不到；
//   · 不被 api / worker / web import（HERMES-PATCH-012 摘掉最后 4 个 import 之后）；
//   · Dockerfile 构建期本来就 rm -rf 掉，对成品镜像零贡献。
// 唯一的实际效果是把上百个第三方 SDK（含 @anthropic-ai/sdk、openai、@google/genai …）拖进
// 每一次 install —— 在受限内网 / 气隙环境里，这是纯粹的失败面。
//
// 按名字或按依赖过滤都不可靠：47 个 *-ai 件用 httpClient 直连模型 API，不带任何 SDK 依赖。
// 所以这里用**白名单**：只保留下面 KEEP 里逐条写明理由的件，其余全删。
//
// HERMES-PATCH-011（先按 SDK 依赖删掉 8 个厂商 AI 件）是本脚本的前身，已被这里的白名单吸收，
// 台账里保留该行只为记录动机；rebase 后只需跑本脚本，不必再单独重放 011。
//
// 用法：
//   node hermes/trim-vendor-pieces.mjs           删除并清理 tsconfig 映射
//   node hermes/trim-vendor-pieces.mjs --check   只检查（有残留则退出码 1，供 CI / rebase 后自检）
//
// rebase 到新上游 tag 之后：跑一次本脚本，再 `pnpm install --lockfile-only` 重生成锁文件。

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const communityDir = path.join(root, 'packages/pieces/community')
const tsconfigPath = path.join(root, 'tsconfig.base.json')

// 保留清单 —— 每一条都必须有理由，没理由的不许加。
// 注：piece-ai 曾在此清单里，2026-07-28 移除——AI Generate 已改用 HTTP piece 直连模型端点，
// 那条 run_agent 链路连同它要打的补丁（HERMES-PATCH-002）一起作废。
const KEEP = {
    'biz-calendar': '自研件，hermes/tarballs/ 里 tgz 的源码真源',
    'hash-helper': '自研件，同上',
    'json': '白名单件 @activepieces/piece-json，源码在 community（其余白名单件在 core/）',
    'postgres': '白名单件 @activepieces/piece-postgres，同上',
}

const check = process.argv.includes('--check')

const present = fs.readdirSync(communityDir, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => e.name)

const missing = Object.keys(KEEP).filter((n) => !present.includes(n))
if (missing.length > 0) {
    console.error(`FATAL: 保留清单里的件不在 vendor 树里: ${missing.join(', ')}`)
    process.exit(1)
}

const doomed = present.filter((n) => !(n in KEEP))

if (check) {
    if (doomed.length > 0) {
        console.error(`FAIL: ${doomed.length} 个未收敛的 community piece: ${doomed.slice(0, 10).join(', ')}${doomed.length > 10 ? ' …' : ''}`)
        console.error('跑 `node hermes/trim-vendor-pieces.mjs` 收敛，然后重生成 pnpm-lock.yaml')
        process.exit(1)
    }
    console.log(`OK: community 已收敛到 ${present.length} 个件（${present.join(', ')}）`)
    process.exit(0)
}

for (const name of doomed) {
    fs.rmSync(path.join(communityDir, name), { recursive: true, force: true })
}

// tsconfig.base.json 的 paths 逐个 piece 一条映射；删目录后指向不存在的 index.ts 的那些要一并摘掉。
// 行级删除而不是 JSON.parse/stringify —— 后者会把整个 1900 行文件重排，vendor diff 没法看。
const lines = fs.readFileSync(tsconfigPath, 'utf8').split('\n')
const kept = []
let removedMappings = 0
for (let i = 0; i < lines.length; i++) {
    const m = /^ {6}"@activepieces\/piece-[^"]+": \[$/.exec(lines[i])
    const target = m && /^ {8}"([^"]+)"$/.exec(lines[i + 1] ?? '')
    if (target && lines[i + 2]?.trim() === '],' && !fs.existsSync(path.join(root, target[1]))) {
        i += 2
        removedMappings++
        continue
    }
    kept.push(lines[i])
}
fs.writeFileSync(tsconfigPath, kept.join('\n'))
JSON.parse(fs.readFileSync(tsconfigPath, 'utf8')) // fail-loud：改坏了立刻炸

console.log(`删除 ${doomed.length} 个 community piece，保留 ${present.length - doomed.length} 个：${Object.keys(KEEP).join(', ')}`)
console.log(`摘掉 ${removedMappings} 条 tsconfig.base.json path 映射`)
console.log('下一步：pnpm install --lockfile-only')
