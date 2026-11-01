# =====================================================================
#  前端构建镜像：Node 构建三端 → 交给 Nginx 托管
#  构建上下文是项目根目录：docker build -f deploy/Dockerfile -t xinji-erp-web .
# =====================================================================

FROM node:20-alpine AS build

WORKDIR /src/web

# 先只拷 package.json，把依赖层缓存下来，改业务代码不用重装依赖
COPY web/package.json ./
COPY web/shared/package.json ./shared/
COPY web/mall/package.json ./mall/
COPY web/admin/package.json ./admin/
COPY web/mobile/package.json ./mobile/

RUN npm install --no-audit --no-fund --registry=https://registry.npmmirror.com

COPY web/ ./

# 三个应用依次构建，产物输出到 /src/deploy/html
RUN npm run build

FROM nginx:1.27-alpine

RUN rm -f /etc/nginx/conf.d/default.conf
COPY deploy/nginx.conf /etc/nginx/conf.d/xinji.conf
COPY --from=build /src/deploy/html /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
