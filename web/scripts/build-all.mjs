#!/usr/bin/env node
/**
 * 三端依次构建，产物统一落到 deploy/html：
 *   deploy/html          ← mall（网页商城，base=/）
 *   deploy/html/admin    ← admin（运营后台，base=/admin/）
 *   deploy/html/m        ← mobile（手机端，base=/m/）
 *
 * mall 必须最先构建，因为它的 outDir 是 deploy/html 根目录且会清空该目录。
 */
import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')

const apps = [
  { name: 'mall', note: '网页商城' },
  { name: 'admin', note: '运营后台' },
  { name: 'mobile', note: '手机端' }
]

const started = Date.now()

for (const app of apps) {
  console.log(`\n▶  构建 ${app.name}（${app.note}）...`)
  execSync(`npm run build --workspace=${app.name}`, { cwd: root, stdio: 'inherit' })
}

const sec = ((Date.now() - started) / 1000).toFixed(1)
console.log(`\n✓  三端构建完成，用时 ${sec}s`)
console.log('   产物目录：deploy/html')
console.log('   ├─ index.html        网页商城')
console.log('   ├─ admin/            运营后台')
console.log('   └─ m/                手机端')
