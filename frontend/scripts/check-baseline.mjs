#!/usr/bin/env node
/**
 * 存量基线闸门（ratchet gate）—— 只禁止变差，不要求一次清零。
 *
 * 背景：typecheck 与 lint 的脚本在三个 app 里早就存在，但从未接进 CI，
 * 于是 `strict: true` 在 PR 上只是装饰。实测存量：
 *   vue-tsc  UP 358 / AC 108 / DW 113  （共 579）
 *   eslint   UP 1445 errors            （AC/DW 见 baseline.json）
 * 直接设成硬门禁会让每个 PR 都红，实际结果是没人能合代码；
 * 一次性清零又会长期阻塞主干。所以走「冻结基线 + 只降不升」：
 *
 *   实际数 >  基线  → 退出码 1（本次改动引入了新问题）
 *   实际数 <  基线  → 退出码 0，并提示应当调低基线（棘轮往下拧）
 *   实际数 == 基线  → 退出码 0
 *
 * 基线文件 baseline.json 是唯一真源；修好问题后把数字调下去，不允许调上去。
 *
 * 用法：
 *   node scripts/check-baseline.mjs typecheck <app>
 *   node scripts/check-baseline.mjs lint <app>
 */

import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const BASELINE_PATH = resolve(HERE, 'baseline.json')

const [, , kind, app] = process.argv

if (!kind || !app) {
  console.error('用法: node scripts/check-baseline.mjs <typecheck|lint> <app>')
  process.exit(2)
}
if (kind !== 'typecheck' && kind !== 'lint') {
  console.error(`未知检查类型: ${kind}（应为 typecheck 或 lint）`)
  process.exit(2)
}

const baseline = JSON.parse(readFileSync(BASELINE_PATH, 'utf8'))
const expected = baseline?.[kind]?.[app]

if (typeof expected !== 'number') {
  console.error(`baseline.json 缺少 ${kind}.${app} 的基线值。`)
  console.error('新增 app 时必须先测量并登记基线，不能默认放行。')
  process.exit(2)
}

const appDir = resolve(HERE, '..', app)

/** 运行命令并返回 stdout+stderr；这些工具在有问题时以非 0 退出，属预期。 */
function run(cmd, args) {
  try {
    return execFileSync(cmd, args, {
      cwd: appDir,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      maxBuffer: 64 * 1024 * 1024,
      shell: process.platform === 'win32',
    })
  } catch (err) {
    // 工具报告问题时退出码非 0，输出仍在 stdout/stderr 里
    if (err.stdout != null || err.stderr != null) {
      return `${err.stdout ?? ''}${err.stderr ?? ''}`
    }
    console.error(`无法执行 ${cmd}：${err.message}`)
    process.exit(2)
  }
}

function countTypecheck() {
  const out = run('pnpm', ['exec', 'vue-tsc', '--noEmit'])
  // vue-tsc 每个错误一行，形如 "src/foo.vue(12,3): error TS2345: ..."
  const matches = out.match(/error TS\d+/g)
  return matches ? matches.length : 0
}

function countLint() {
  const out = run('pnpm', [
    'exec', 'eslint', '.',
    '--ext', '.vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts',
    '-f', 'json',
  ])
  // eslint 可能在 JSON 前后混入其它输出，取最外层数组
  const start = out.indexOf('[')
  const end = out.lastIndexOf(']')
  if (start === -1 || end === -1) {
    console.error('无法解析 eslint JSON 输出：')
    console.error(out.slice(0, 2000))
    process.exit(2)
  }
  const results = JSON.parse(out.slice(start, end + 1))
  // 只盯 error，不含 warning —— warning 噪音大且不阻断构建
  return results.reduce((sum, f) => sum + f.errorCount, 0)
}

const actual = kind === 'typecheck' ? countTypecheck() : countLint()
const label = `${app} ${kind}`

if (actual > expected) {
  console.error(`✗ ${label}: ${actual} 个问题，超出基线 ${expected}（新增 ${actual - expected} 个）`)
  console.error('')
  console.error('本次改动引入了新的类型/lint 问题。请修复后再提交。')
  console.error(`如果确认是既有问题的重新归类而非新增，再考虑调整 frontend/scripts/baseline.json 的 ${kind}.${app}。`)
  process.exit(1)
}

if (actual < expected) {
  console.log(`✓ ${label}: ${actual} 个问题，低于基线 ${expected}（减少 ${expected - actual} 个）`)
  console.log('')
  console.log(`请把 frontend/scripts/baseline.json 的 ${kind}.${app} 调整为 ${actual}，锁住这次改进。`)
  process.exit(0)
}

console.log(`✓ ${label}: ${actual} 个问题，与基线持平`)
