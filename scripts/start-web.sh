#!/usr/bin/env bash
# 前端预览启动器（Linux / macOS / Git Bash）
# 用法： ./scripts/start-web.sh [端口]
set -e
cd "$(dirname "$0")/.."

PORT=${1:-8090}

if ! command -v node >/dev/null 2>&1; then
  echo "没找到 Node.js，请先安装 Node 18 及以上"
  exit 1
fi

if [ ! -f deploy/html/index.html ]; then
  echo "没有构建产物，正在安装依赖并构建三端..."
  (cd web && npm install && npm run build)
fi

echo "启动预览服务，端口 $PORT ..."
PORT=$PORT nohup node deploy/preview.mjs > preview.log 2>&1 &

sleep 2
echo ""
echo "  网页商城    http://localhost:$PORT/"
echo "  运营后台    http://localhost:$PORT/admin/"
echo "  手机端      http://localhost:$PORT/m/"
echo ""
echo "  日志：preview.log    停止：kill %1 或 pkill -f preview.mjs"
tail -6 preview.log 2>/dev/null || true
