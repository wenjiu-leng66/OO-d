import { copyFile, mkdir } from 'node:fs/promises'

const copies = [
  ['../../api/openapi.yaml', '../dist/api-design/openapi.yaml'],
  ['../../deploy/stage1/index.html', '../dist/stage1/index.html']
]
for (const [source, target] of copies) {
  const destination = new URL(target, import.meta.url)
  await mkdir(new URL('.', destination), { recursive: true })
  await copyFile(new URL(source, import.meta.url), destination)
}
