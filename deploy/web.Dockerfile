FROM node:22-alpine AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
# 使用锁文件安装，确保检查机与部署构建解析同一组依赖。
RUN npm ci
COPY frontend/ ./
COPY api /workspace/api
COPY deploy/stage1 /workspace/deploy/stage1
RUN npm run build

FROM nginx:stable-alpine
# Vue 原型、一期真实后端入口和 API 契约共同发布，但原型的内存模拟数据不会变为后端数据。
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
COPY api/openapi.yaml /usr/share/nginx/html/api-design/openapi.yaml
COPY deploy/stage1 /usr/share/nginx/html/stage1
EXPOSE 80
# 此探针只检查静态服务，真实后端与 Cookie 场景另由 smoke_http.py 核验。
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 CMD wget -q -O /dev/null http://127.0.0.1/nginx-health || exit 1
