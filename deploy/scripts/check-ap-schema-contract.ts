/**
 * 发布门禁：admin-center 直查的 AP 表列，是否仍存在于当前 AP schema。
 *
 * 为什么需要它 —— 2026-08 UAT 事故：AP 0.88 的迁移 DropPlatformPieceFilters1809000000000
 * 删掉了 platform.filteredPieceNames，而 admin-center 的 AutomationPieceServiceImpl 仍在
 * SELECT 它，Automation Pieces 整页 500。那条迁移**自带 breaking = true 标记**，但当时没有
 * 任何环节消费它。本脚本就是消费方。
 *
 * 权威来源用 TypeORM 的 EntitySchema 本体，不做正则解析源码：
 *   - 正则解析会漏掉 `...BaseColumnSchemaPart` 这类展开（id/created/updated 就在里面），
 *     而那正是最容易误判「列不存在」的地方；
 *   - EntitySchema 是 AP 自己建表和查询用的同一个对象，读它就是读真相。
 *
 * 实体等价于数据库这一点，由 AP 既有的 `npm run check-migrations` 保证（migration:generate
 * --check 无漂移）。所以「契约 ⊆ 实体」加上「实体 ≡ 数据库」，传递出「契约 ⊆ 数据库」。
 * 两者要一起跑才成立 —— 见 build-and-push-k8s.ps1 的 preflight。
 *
 * 用法（在 automation/packages/server/api 下）：
 *   ts-node --transpile-only -r tsconfig-paths/register -P tsconfig.app.json \
 *     ../../../../deploy/scripts/check-ap-schema-contract.ts
 */
import { readFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { EntitySchema } from 'typeorm'

const REPO_ROOT = resolve(__dirname, '../..')
const CONTRACT = join(REPO_ROOT, 'deploy/contracts/ap-schema-contract.json')
const MIGRATIONS_DIR = join(
    REPO_ROOT,
    'automation/packages/server/api/src/app/database/migration/postgres',
)

type ContractTable = { usedBy: string[], columns: string[] }
type Contract = { tables: Record<string, ContractTable> }

/**
 * 扫描 api 包里所有 EntitySchema，建 表名 -> 列集合 的映射。
 *
 * 不用手维护「表 -> 实体文件」映射：那张表一旦漏登记，校验器会把「实体在但没登记」报成
 * 「表不存在」——两种情况都 fail-closed，但错误信息会把人引向错误的修复方向。首次自测就
 * 撞上了这一点（把 platform 加进契约却忘了登记，报成整张表被删）。自动发现没有这个失配面。
 */
const APP_DIR = join(REPO_ROOT, 'automation/packages/server/api/src/app')

function collectEntityFiles(dir: string, out: string[] = []): string[] {
    for (const e of readdirSync(dir, { withFileTypes: true })) {
        const p = join(dir, e.name)
        if (e.isDirectory()) {
            if (e.name !== 'node_modules' && e.name !== 'migration') {
                collectEntityFiles(p, out)
            }
        }
        else if (/entity\.ts$|-entity\.ts$/.test(e.name)) {
            out.push(p)
        }
    }
    return out
}

function buildSchemaMap(): Map<string, Set<string>> {
    const map = new Map<string, Set<string>>()
    for (const file of collectEntityFiles(APP_DIR)) {
        let loaded: Record<string, unknown>
        try {
            loaded = require(file) as Record<string, unknown>
        }
        catch {
            continue   // 非实体的同名文件/导入副作用失败：跳过，不让它影响整体判定
        }
        for (const v of Object.values(loaded)) {
            const opts = (v as { options?: { name?: string, columns?: Record<string, unknown> } })?.options
            if (!opts?.name || !opts.columns) {
                continue
            }
            map.set(opts.name, new Set(Object.keys(opts.columns)))
        }
    }
    return map
}

/** 消费上游的 breaking 标记：列出所有 breaking = true 的迁移，供发布评审。 */
function breakingMigrations(): string[] {
    return readdirSync(MIGRATIONS_DIR)
        .filter(f => f.endsWith('.ts'))
        .filter(f => /\bbreaking\s*=\s*true\b/.test(readFileSync(join(MIGRATIONS_DIR, f), 'utf-8')))
        .sort()
}

function main(): void {
    const contract = JSON.parse(readFileSync(CONTRACT, 'utf-8')) as Contract
    const schema = buildSchemaMap()
    if (schema.size === 0) {
        console.error('❌ 没有从 AP 实体里读出任何表 —— 校验器本身坏了，不要当成「通过」。')
        process.exit(2)
    }
    const failures: string[] = []

    for (const [table, spec] of Object.entries(contract.tables)) {
        const actual = schema.get(table)
        if (actual === undefined) {
            failures.push(
                `表 ${table}：AP 实体里已不存在这张表（整表被删）\n`
                + `    被这些 SQL 依赖：${spec.usedBy.join(', ')}\n`
                + `    当前 AP 共 ${schema.size} 张表`,
            )
            continue
        }
        const missing = spec.columns.filter(c => !actual.has(c))
        if (missing.length > 0) {
            failures.push(
                `表 ${table}：缺少 ${missing.length} 列 -> ${missing.join(', ')}\n`
                + `    被这些 SQL 依赖：${spec.usedBy.join(', ')}\n`
                + `    实体现有列：${[...actual].sort().join(', ')}`,
            )
        }
    }

    const breaking = breakingMigrations()
    if (breaking.length > 0) {
        console.log(`ℹ️  AP 迁移链中标记 breaking = true 的共 ${breaking.length} 条：`)
        for (const f of breaking) {
            console.log(`     ${f}`)
        }
        console.log('   （标记本身不阻断发布；阻断与否由下面的契约比对决定。'
            + '升级 AP 版本时应逐条确认它们不触及上面契约里的表。）\n')
    }

    if (failures.length > 0) {
        console.error('❌ AP schema 契约校验失败 —— admin-center 会在运行时崩，不是编译期。\n')
        for (const f of failures) {
            console.error(`  ${f}\n`)
        }
        console.error('修复方式二选一：')
        console.error('  a) admin-center 不再依赖该列（改 SQL + 同步 deploy/contracts/ap-schema-contract.json）；')
        console.error('  b) 该列确实还需要 —— 补一条 HERMES 迁移把它加回来，或改用 AP 提供的替代字段。')
        console.error('\n不要仅仅从契约里删掉这行来「修好」它：那只是把运行时崩溃藏回去。')
        process.exit(1)
    }

    const tables = Object.keys(contract.tables).length
    const cols = Object.values(contract.tables).reduce((n, t) => n + t.columns.length, 0)
    console.log(`✅ AP schema 契约校验通过：${tables} 张表 / ${cols} 列，admin-center 依赖的列全部存在。`)
}

main()
