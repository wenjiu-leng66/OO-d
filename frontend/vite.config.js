import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { readFile } from 'node:fs/promises'

const stage1Assets = {
  name: 'stage1-review-assets',
  configureServer(server) {
    server.middlewares.use(async (req, res, next) => {
      const routes = {
        '/api-design/openapi.yaml': ['../api/openapi.yaml', 'application/yaml; charset=utf-8'],
        '/stage1/': ['../deploy/stage1/index.html', 'text/html; charset=utf-8']
      }
      const asset = routes[req.url?.split('?')[0]]
      if (!asset) return next()
      try {
        res.setHeader('Content-Type', asset[1])
        res.end(await readFile(new URL(asset[0], import.meta.url)))
      } catch (error) { next(error) }
    })
  }
}
const target = process.env.RBAC_API_TARGET || 'http://127.0.0.1:8080'
const proxy = Object.fromEntries(['/api/v1', '/swagger-ui', '/v3/api-docs', '/health'].map(path => [path, { target }]))

export default defineConfig({ plugins: [vue(), stage1Assets], server: { host: '127.0.0.1', port: 5173, proxy } })
