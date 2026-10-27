import { copyFile, mkdir } from 'node:fs/promises'

// Vite 生产构建后复制权威 API 契约和验收静态入口；保持与开发中间件暴露的 URL 一致。
// 路径相对本脚本定位，不依赖执行者当前目录；复制失败会令整个 build 失败。
const copies = [
  ['../../api/openapi.yaml', '../dist/api-design/openapi.yaml'],
  ['../../deploy/stage1/index.html', '../dist/stage1/index.html']
]
for (const [source, target] of copies) {
  const destination = new URL(target, import.meta.url)
  await mkdir(new URL('.', destination), { recursive: true })
  await copyFile(new URL(source, import.meta.url), destination)
}
