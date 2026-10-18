// Rebuild only public synthetic fixtures. No private workbook is read here.
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { departments, roleRows, roleGrants, createUsers } from '../../frontend/src/api/fixtures/demo-data.js'
import { systems, functions, actions } from '../../frontend/src/api/fixtures/catalog.js'

const output = new URL('../src/main/resources/demo-data.json', import.meta.url)
const contents = JSON.stringify({ departments, roleRows, roleGrants, users: createUsers(), systems, functions, actions }, null, 2)
if (process.argv.includes('--check')) {
  if (fs.readFileSync(output, 'utf8') !== contents) {
    throw new Error('Backend synthetic fixture differs from frontend fixtures; run node backend-procedural/scripts/refresh-demo.mjs')
  }
} else {
  fs.writeFileSync(output, contents)
}
console.log(`Synthetic fixture verified: ${fileURLToPath(output)}`)
