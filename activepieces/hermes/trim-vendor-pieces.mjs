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
//
// ⚠️ 顺序有讲究，脚本会自己把关（见 assertNothingStillNeedsDoomedPieces）：
// **必须先重放 HERMES-PATCH-012，再跑本脚本**。上游的 api 依赖并 import 了
// slack / square / facebook-leads / intercom 四个件（用于 /v1/app-events 那个未鉴权端点），
// rebase 会把这些依赖带回来；此时先跑本脚本就会删掉仍被 import 的目录，树直接编译不过，
// 而报错只会说"找不到 @activepieces/piece-slack"，完全指不到真正原因。

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const communityDir = path.join(root, 'packages/pieces/community')

// 每个 piece 在 tsconfig 里有一条 path 映射，而映射不止一处：根 tsconfig.base.json 之外，
// web 自己还有两份（删 piece-ai 时踩到的盲区——只清根文件会留下指向已删目录的悬空映射）。
const TSCONFIGS = [
    'tsconfig.base.json',
    'packages/web/tsconfig.app.json',
    'packages/web/tsconfig.spec.json',
]

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

assertNothingStillNeedsDoomedPieces(doomed)

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

let removedMappings = 0
for (const relPath of TSCONFIGS) {
    removedMappings += pruneDeadPieceMappings(relPath)
}

console.log(`删除 ${doomed.length} 个 community piece，保留 ${present.length - doomed.length} 个：${Object.keys(KEEP).join(', ')}`)
console.log(`摘掉 ${removedMappings} 条 tsconfig path 映射（扫了 ${TSCONFIGS.length} 个文件）`)
console.log('下一步：pnpm install --lockfile-only')

// 删之前先确认没人还在用这些件。踩过的坑：rebase 之后上游的 api 会重新依赖并 import
// slack / square / facebook-leads / intercom，此时若先跑本脚本，删掉的目录仍被引用 ——
// 编译错误是"找不到 @activepieces/piece-slack"，没有任何线索指向"HERMES-PATCH-012 还没重放"。
function assertNothingStillNeedsDoomedPieces(doomedDirs) {
    // 树已收敛时无件可删，这些探针就没有对象了 —— 再报错只会制造噪音。
    if (doomedDirs.length === 0) return

    const doomedPackageNames = new Set(doomedDirs.map(readPieceName).filter(Boolean))
    const problems = []

    for (const manifest of collectWorkspaceManifests()) {
        const json = tryReadJson(path.join(root, manifest))
        if (!json) continue
        const declared = Object.keys({ ...json.dependencies, ...json.devDependencies })
        const hits = declared.filter((d) => doomedPackageNames.has(d))
        if (hits.length > 0) {
            problems.push(`${manifest} 仍依赖：${hits.join(', ')}`)
        }
    }

    // 012 的专项探针：这个控制器是那四个件唯一的 import 点，它还在就说明补丁没重放。
    const appEventsModule = 'packages/server/api/src/app/trigger/app-event-routing/app-event-routing.module.ts'
    if (fs.existsSync(path.join(root, appEventsModule))) {
        problems.push(`${appEventsModule} 还在（HERMES-PATCH-012 未重放）`)
    }

    if (problems.length === 0) return

    console.error('REFUSED: 有代码仍在引用即将被删的 piece，先修好再跑本脚本。\n')
    for (const p of problems) console.error('  · ' + p)
    console.error([
        '',
        '最常见的原因是 rebase 到新上游 tag 之后**忘了先重放 HERMES-PATCH-012**：',
        '上游把 /v1/app-events/:pieceUrl 这个未鉴权端点连同 slack / square /',
        'facebook-leads / intercom 四个件的 import 一起带了回来。',
        '',
        '正确顺序：先重放 012（摘掉 app.ts 里的 register + import、删 app-event-routing.module.ts、',
        '清掉 api/package.json 里那四条 workspace 依赖），再跑本脚本，最后 pnpm install --lockfile-only。',
        '详见 docs/ap-integration/HERMES_PATCHES.md 的 012 行。',
    ].join('\n'))
    process.exit(1)
}

function readPieceName(dirName) {
    const json = tryReadJson(path.join(communityDir, dirName, 'package.json'))
    // 绝大多数件的包名就是目录名加前缀，但以 package.json 里的 name 为准 —— 上游有过不一致。
    return json?.name ?? `@activepieces/piece-${dirName}`
}

function collectWorkspaceManifests() {
    const found = []
    const walk = (relDir, depth) => {
        if (depth > 4) return
        let entries
        try {
            entries = fs.readdirSync(path.join(root, relDir), { withFileTypes: true })
        }
        catch {
            return
        }
        for (const entry of entries) {
            if (entry.name === 'node_modules' || entry.name === 'dist') continue
            const rel = path.posix.join(relDir, entry.name)
            // 注定要删的目录里的 manifest 不算引用者，跳过（否则自己引自己）。
            if (rel.startsWith('packages/pieces/community/')) continue
            if (entry.isDirectory()) walk(rel, depth + 1)
            else if (entry.name === 'package.json') found.push(rel)
        }
    }
    walk('packages', 0)
    return found
}

// paths 里逐个 piece 一条三行映射；目标路径已不存在的整条摘掉。
// 行级删除而不是 JSON.parse/stringify —— 后者会把这些文件整体重排，vendor diff 没法看。
function pruneDeadPieceMappings(relPath) {
    const abs = path.join(root, relPath)
    if (!fs.existsSync(abs)) {
        // 上游删掉/改名了某个 tsconfig：不致命，但必须说出来，否则悬空映射会静默留下。
        console.warn(`WARN: ${relPath} 不存在，跳过（上游布局变了？请复核 TSCONFIGS）`)
        return 0
    }
    const original = fs.readFileSync(abs, 'utf8')
    const parsedBefore = tryParseJson(original) // 带注释的 JSONC 会失败，那就跳过下面的校验
    const baseDir = path.dirname(abs)
    const lines = original.split('\n')
    const kept = []
    let removed = 0

    for (let i = 0; i < lines.length; i++) {
        // 上游两种写法都有：短名字的件被格式化器压成单行。只认三行式会漏掉它们
        // （删 piece-ai 时就漏了，8 条悬空映射一直留在树里，直到 VT-04 才发现）。
        const inline = /^\s*"@activepieces\/piece-[^"]+"\s*:\s*\[\s*"([^"]+)"\s*\]\s*,?\s*$/.exec(lines[i])
        if (inline && !fs.existsSync(path.resolve(baseDir, inline[1]))) {
            removed++
            continue
        }

        const isKey = /^\s*"@activepieces\/piece-[^"]+"\s*:\s*\[\s*$/.test(lines[i])
        const target = isKey ? /^\s*"([^"]+)"\s*,?\s*$/.exec(lines[i + 1] ?? '') : null
        const closes = target !== null && /^\s*\],?\s*$/.test(lines[i + 2] ?? '')
        // 路径按各自 tsconfig 所在目录解析：根文件写 packages/…，web 那两份写 ../../packages/…
        if (closes && !fs.existsSync(path.resolve(baseDir, target[1]))) {
            i += 2
            removed++
            continue
        }
        kept.push(lines[i])
    }
    if (removed === 0) return 0

    // 摘掉的若是 paths 里最后一条，前一条的尾逗号会变成悬空逗号 —— JSON 不允许。
    for (let i = 0; i < kept.length - 1; i++) {
        if (/,\s*$/.test(kept[i]) && /^\s*[}\]]/.test(kept[i + 1])) {
            kept[i] = kept[i].replace(/,(\s*)$/, '$1')
        }
    }

    const next = kept.join('\n')
    if (parsedBefore !== null && tryParseJson(next) === null) {
        console.error(`FATAL: 清理 ${relPath} 后 JSON 解析失败，未写回。请手工处理。`)
        process.exit(1)
    }
    fs.writeFileSync(abs, next)
    console.log(`  ${relPath}: 摘掉 ${removed} 条`)
    return removed
}

function tryReadJson(abs) {
    try {
        return JSON.parse(fs.readFileSync(abs, 'utf8'))
    }
    catch {
        return null
    }
}

function tryParseJson(text) {
    try {
        return JSON.parse(text)
    }
    catch {
        return null
    }
}
