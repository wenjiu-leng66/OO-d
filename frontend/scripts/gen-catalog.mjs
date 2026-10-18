// 从成员 1 的种子 CSV 生成前端资源目录：node scripts/gen-catalog.mjs [种子目录]
// 默认读取 ../database/seeds/system_codes.csv 与 function_codes.csv，只含系统、模块、功能点名称，不含人员数据。
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const seeds = resolve(process.argv[2] || resolve(here, '../../database/seeds'))
const rows = file => readFileSync(resolve(seeds, file), 'utf8').trim().split(/\r?\n/).slice(1).map(line => line.split(','))

const systems = rows('system_codes.csv').map(([code, name]) => ({ code, name }))
const codeByName = new Map(systems.map(item => [item.name, item.code]))
const functions = rows('function_codes.csv').map(([id, system, module, name]) => {
  if (!codeByName.has(system)) throw new Error(`未知系统：${system}`)
  return [id, codeByName.get(system), module, name]
})

const out = `// 由 frontend/scripts/gen-catalog.mjs 从 database/seeds 的 system_codes.csv、function_codes.csv 生成，请勿手改。
// 18 个系统、115 个模块、359 个功能点；权限码为 系统码:fNNN:动作（需求规格 CAT-02）。
export const systems = ${JSON.stringify(systems, null, 0).replace(/},{/g, '},\n  {').replace('[{', '[\n  {').replace('}]', '}\n]')}

// [功能点编号, 系统码, 模块, 功能点]
export const functionRows = [
${functions.map(row => '  ' + JSON.stringify(row)).join(',\n')}
]
`
writeFileSync(resolve(here, '../src/api/fixtures/catalog-data.js'), out)
console.log(`systems=${systems.length} modules=${new Set(functions.map(r => r[1] + r[2])).size} functions=${functions.length}`)
