#!/usr/bin/env node
/**
 * 本地预览服务 —— 用同一套路由规则模拟 deploy/nginx.conf，不依赖 nginx / Docker。
 *
 * 规则与 nginx 保持一致：
 *   /              → html/index.html（网页商城，找不到就回退到它）
 *   /admin/*       → html/admin/*（找不到回退 html/admin/index.html）
 *   /m/*           → html/m/*（找不到回退 html/m/index.html）
 *   /api/*         → 反向代理到后端（默认 http://127.0.0.1:8080）
 *   /actuator/*    → 同上
 *
 * 用法：
 *   node deploy/preview.mjs                 # 默认 8090 端口
 *   PORT=8088 node deploy/preview.mjs       # 换端口
 *   BACKEND=http://127.0.0.1:8080 node deploy/preview.mjs
 */
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.join(HERE, 'html')
const BACKEND = process.env.BACKEND || 'http://127.0.0.1:8080'
const PORT = Number(process.env.PORT || 8090)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.map': 'application/json'
}

function sendFile(res, file, status) {
  const ext = path.extname(file).toLowerCase()
  res.writeHead(status || 200, {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'X-Content-Type-Options': 'nosniff'
  })
  fs.createReadStream(file).pipe(res)
}

function tryFile(p) {
  const target = path.join(ROOT, p)
  if (!target.startsWith(ROOT)) return null
  try {
    const st = fs.statSync(target)
    if (st.isFile()) return target
    if (st.isDirectory()) {
      const idx = path.join(target, 'index.html')
      if (fs.existsSync(idx)) return idx
    }
  } catch (e) {
    return null
  }
  return null
}

function proxy(req, res) {
  const target = new URL(req.url, BACKEND)
  const opt = {
    hostname: target.hostname,
    port: target.port || 80,
    path: target.pathname + target.search,
    method: req.method,
    headers: Object.assign({}, req.headers, { host: target.host })
  }
  const p = http.request(opt, (r) => {
    res.writeHead(r.statusCode || 502, r.headers)
    r.pipe(res)
  })
  p.on('error', (e) => {
    res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ code: 503, msg: '后端未启动（' + BACKEND + '）：' + e.message }))
  })
  req.pipe(p)
}

const server = http.createServer((req, res) => {
  if (!fs.existsSync(ROOT)) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('找不到 deploy/html，请先在 web 目录执行 npm run build')
    return
  }

  const pathname = decodeURIComponent(new URL(req.url, 'http://x').pathname)

  if (pathname.startsWith('/api/') || pathname.startsWith('/actuator/')) {
    proxy(req, res)
    return
  }

  let file = tryFile(pathname)

  if (!file) {
    if (pathname.startsWith('/admin')) file = tryFile('/admin/index.html')
    else if (pathname.startsWith('/m')) file = tryFile('/m/index.html')
    else file = tryFile('/index.html')
  }

  if (!file) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
    res.end('404 Not Found: ' + pathname)
    return
  }

  const noCache = file.endsWith('index.html')
  if (noCache) res.setHeader('Cache-Control', 'no-store')
  else if (file.includes('/assets/')) res.setHeader('Cache-Control', 'public, max-age=2592000, immutable')

  sendFile(res, file)
})

server.listen(PORT, () => {
  console.log('')
  console.log('  鑫基矿业 ERP · 本地预览')
  console.log('  ─────────────────────────────────────────────')
  console.log('  网页商城    http://localhost:' + PORT + '/')
  console.log('  运营后台    http://localhost:' + PORT + '/admin/')
  console.log('  手机端      http://localhost:' + PORT + '/m/')
  console.log('  接口代理    ' + BACKEND)
  console.log('  ─────────────────────────────────────────────')
  console.log('  静态目录    ' + ROOT)
  console.log('')
})
