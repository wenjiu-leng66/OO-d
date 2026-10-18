FROM node:22-alpine AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
COPY api /workspace/api
COPY deploy/stage1 /workspace/deploy/stage1
RUN npm run build

FROM nginx:stable-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
COPY api/openapi.yaml /usr/share/nginx/html/api-design/openapi.yaml
COPY deploy/stage1 /usr/share/nginx/html/stage1
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 CMD wget -q -O /dev/null http://127.0.0.1/nginx-health || exit 1
