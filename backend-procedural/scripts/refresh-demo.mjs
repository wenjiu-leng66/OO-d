// 从公开合成夹具生成后端演示 JSON；不读取原始员工表，也不生成真实账号资料。
// 前后端共用同一合成基准，减少演示身份与后端权限口径漂移。
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { departments, roleRows, roleGrants, createUsers } from '../../frontend/src/api/fixtures/demo-data.js'
import { systems, functions, actions } from '../../frontend/src/api/fixtures/catalog.js'

const output = new URL('../src/main/resources/demo-data.json', import.meta.url)
const contents = JSON.stringify({ departments, roleRows, roleGrants, users: createUsers(), systems, functions, actions }, null, 2)
// --check 只比较序列化结果；普通模式才重写 JSON。夹具变更后应同时运行前后端权限测试。
if (process.argv.includes('--check')) {
  if (fs.readFileSync(output, 'utf8') !== contents) {
    throw new Error('Backend synthetic fixture differs from frontend fixtures; run node backend-procedural/scripts/refresh-demo.mjs')
  }
} else {
  fs.writeFileSync(output, contents)
}
console.log(`Synthetic fixture verified: ${fileURLToPath(output)}`)
