# =============================================================
#  鑫基矿业 ERP · 本机 nginx 配置（Windows 运行时使用）
# -------------------------------------------------------------
#  直接双击 nginx.exe 也能用：所有路径都是相对 nginx 所在目录的
#  （root ../html 就是 deploy/html），不含任何机器绝对路径，
#  所以整目录拷到别的电脑照样能跑。
#
#  scripts/start-nginx.ps1 启动时只会替换下面 2 个占位符并覆盖本文件：
#      {{PORT}}        本机访问端口（默认 8090）
#      {{BACKEND}}     后端 Spring Boot 地址（默认 127.0.0.1:8080）
#
#  完整链路：浏览器 -> nginx({{PORT}}) -> 静态页面
#                          └─ /api/** -> 后端({{BACKEND}}) -> 数据库
# =============================================================

worker_processes  1;

error_log  logs/error.log  warn;
pid        logs/nginx.pid;

events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;

    sendfile        on;
    keepalive_timeout  65;
    server_tokens   off;

    # 后端接口响应可能包含大列表，开启 gzip 省一半带宽
    gzip  on;
    gzip_min_length  1024;
    gzip_comp_level  5;
    gzip_types  text/plain text/css application/json application/javascript
                application/x-javascript text/xml application/xml image/svg+xml;

    # 上传磅单/附件时允许的请求体大小
    client_max_body_size  20m;

    # 反向代理到后端时带上真实来源，便于日志排查
    map $http_upgrade $connection_upgrade {
        default upgrade;
        ''      close;
    }

    server {
        listen       {{PORT}};
        server_name  localhost;

        # ../html = deploy/html（三端构建产物）
        # 相对路径是相对 nginx 所在目录解析的，换电脑不用改
        root  ../html;
        index index.html;

        charset utf-8;

        # ---------- 安全响应头 ----------
        add_header X-Content-Type-Options "nosniff" always;
        add_header X-Frame-Options "SAMEORIGIN" always;

        # ---------- 接口反向代理：页面 -> nginx -> 后端 ----------
        # 前端所有请求都是相对路径 /api/xxx，由这里转给 Spring Boot
        location /api/ {
            proxy_pass http://{{BACKEND}};
            proxy_http_version 1.1;
            proxy_set_header Host              $host;
            proxy_set_header X-Real-IP         $remote_addr;
            proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_set_header Upgrade           $http_upgrade;
            proxy_set_header Connection        $connection_upgrade;

            proxy_connect_timeout 5s;
            proxy_send_timeout    60s;
            proxy_read_timeout    60s;
        }

        # 健康检查 /actuator 一并转发，方便确认后端是否活着
        location /actuator/ {
            proxy_pass http://{{BACKEND}};
            proxy_set_header Host $host;
            access_log off;
        }

        # ---------- 三个前端的 history 路由回退 ----------
        # /admin/orders 这种刷新/深链，文件不存在就回退到各自的 index.html
        location /admin/ {
            try_files $uri $uri/ /admin/index.html;
        }

        location /m/ {
            try_files $uri $uri/ /m/index.html;
        }

        location / {
            try_files $uri $uri/ /index.html;
        }

        # ---------- 缓存策略 ----------
        # 带 hash 的静态资源长缓存；入口 HTML 绝不缓存，否则发版不生效
        location ~* ^/(admin|m)?/?assets/ {
            expires 30d;
            add_header Cache-Control "public, immutable";
        }

        location = /index.html        { add_header Cache-Control "no-store" always; }
        location = /admin/index.html  { add_header Cache-Control "no-store" always; }
        location = /m/index.html      { add_header Cache-Control "no-store" always; }

        # ---------- 错误页 ----------
        error_page 500 502 503 504 /50x.html;
        location = /50x.html {
            root html;
            internal;
        }
    }
}
