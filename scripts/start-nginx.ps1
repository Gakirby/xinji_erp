#Requires -Version 5.1
<#
    XinJi ERP - start nginx (real nginx, not a node fake)

    Usage:
        powershell -ExecutionPolicy Bypass -File scripts\start-nginx.ps1
        powershell -ExecutionPolicy Bypass -File scripts\start-nginx.ps1 -Port 8091
#>
param(
    [int]$Port = 8090,
    [string]$Backend = ''
)

$ErrorActionPreference = 'Stop'

# ---------- paths ----------
$Root       = Split-Path -Parent $PSScriptRoot
$Runtime    = Join-Path $Root 'deploy\nginx-runtime'
$NginxExe   = Join-Path $Runtime 'nginx.exe'
$HtmlDir    = Join-Path $Root 'deploy\html'
$Tpl        = Join-Path $Root 'deploy\nginx.local.conf.tpl'
$ConfOut    = Join-Path $Runtime 'conf\nginx.conf'

function Write-Line($t, $c = 'Gray') { Write-Host $t -ForegroundColor $c }

Write-Host ''
Write-Line '=============================================================' Cyan
Write-Line '  鑫基矿业 ERP · 启动 nginx 前端服务' Cyan
Write-Line '=============================================================' Cyan
Write-Host ''

# ---------- 1. checks ----------
if (-not (Test-Path $NginxExe)) {
    Write-Line "[x] 找不到 nginx.exe：$NginxExe" Red
    Write-Line '    请确认 deploy\nginx-runtime 目录完整（包里自带，勿删）' Yellow
    exit 1
}

if (-not (Test-Path (Join-Path $HtmlDir 'index.html'))) {
    Write-Line "[!] 前端产物不存在：$HtmlDir" Yellow
    Write-Line '    正在构建三端前端（首次约 20 秒）...' Yellow
    Push-Location (Join-Path $Root 'web')
    try {
        cmd /c "npm install --registry=https://registry.npmmirror.com" 2>&1 | Out-Null
        cmd /c "npm run build" 2>&1 | Out-Null
    } finally { Pop-Location }
    if (-not (Test-Path (Join-Path $HtmlDir 'index.html'))) {
        Write-Line '[x] 构建失败，请先在 web 目录执行 npm install && npm run build' Red
        exit 1
    }
    Write-Line '    构建完成' Green
}

# nginx on Windows 对中文路径支持很差，提前警告
if ($Root -match '[^\x20-\x7E]') {
    Write-Line "[!] 项目路径含非 ASCII 字符：$Root" Yellow
    Write-Line '    nginx 在 Windows 下可能无法启动，建议移到纯英文路径（如 D:\erp）' Yellow
}

# ---------- 2. detect backend ----------
function Test-TcpPort([int]$p) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.ReceiveTimeout = 800
        $c.SendTimeout = 800
        $c.Connect('127.0.0.1', $p)
        $ok = $c.Connected
        $c.Close()
        return $ok
    } catch { return $false }
}

if ([string]::IsNullOrWhiteSpace($Backend)) {
    $backendPort = 0
    foreach ($p in 8080, 8081, 8082, 8083) {
        if (Test-TcpPort $p) { $backendPort = $p; break }
    }
    if ($backendPort -eq 0) {
        Write-Line '[!] 未检测到后端服务（8080/8081 都没开）' Yellow
        Write-Line '    -> IDEA 里运行 ErpApplication，或双击 scripts\start-backend.bat' Yellow
        Write-Line '    页面能打开，但接口会 502（没数据）' Yellow
        $Backend = '127.0.0.1:8080'
    } else {
        $Backend = "127.0.0.1:$backendPort"
        Write-Line "[ok] 后端已连通：$Backend" Green
    }
} else {
    Write-Line "[ok] 使用指定后端：$Backend" Green
}

# ---------- 3. pick a free port ----------
$tries = 0
while ((Test-TcpPort $Port) -and $tries -lt 10) {
    Write-Line "[!] 端口 $Port 已被占用，尝试 $($Port + 1)" Yellow
    $Port++
    $tries++
}
if (Test-TcpPort $Port) {
    Write-Line "[x] 端口 $Port 仍被占用，请手动指定：start-nginx.ps1 -Port 8100" Red
    exit 1
}

# ---------- 4. render nginx.conf ----------
# 目录一律用相对路径（相对 nginx 所在目录），这样配置不绑定某台机器
$conf = (Get-Content $Tpl -Raw -Encoding UTF8)
$conf = $conf.Replace('{{PORT}}', "$Port")
$conf = $conf.Replace('{{BACKEND}}', $Backend)
[System.IO.File]::WriteAllText($ConfOut, $conf, (New-Object System.Text.UTF8Encoding($false)))
Write-Line "[ok] 已生成配置（端口 $Port，后端 $Backend）" Green

# ---------- 5. stop old nginx (if any) ----------
$running = Get-Process nginx -ErrorAction SilentlyContinue
if ($running) {
    Write-Line '[..] 检测到已运行的 nginx，先停止' Yellow
    Push-Location $Runtime
    try { & $NginxExe -s stop 2>&1 | Out-Null } catch {}
    Pop-Location
    Start-Sleep -Seconds 1
    Get-Process nginx -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

# ---------- 6. start ----------
Push-Location $Runtime
try {
    $p = Start-Process -FilePath $NginxExe -WorkingDirectory $Runtime -PassThru -WindowStyle Hidden
    $p.WaitForExit(8000) | Out-Null
} finally { Pop-Location }

Start-Sleep -Seconds 2

# ---------- 7. verify ----------
$ok = $false
try {
    $r = Invoke-WebRequest -Uri "http://localhost:$Port/" -UseBasicParsing -TimeoutSec 6
    $ok = ($r.StatusCode -eq 200)
} catch { $ok = $false }

if (-not $ok) {
    Write-Line "[x] nginx 启动后无法访问 http://localhost:$Port/" Red
    Write-Line "    查看日志：$Runtime\logs\error.log" Yellow
    if (Test-Path (Join-Path $Runtime 'logs\error.log')) {
        Get-Content (Join-Path $Runtime 'logs\error.log') -Tail 10 | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }
    }
    exit 1
}

Write-Host ''
Write-Line '=============================================================' Green
Write-Line '  nginx 已启动（独立进程，关掉本窗口不会停）' Green
Write-Line '=============================================================' Green
Write-Host ''
Write-Host ("  网页商城    http://localhost:$Port/")
Write-Host ("  运营后台    http://localhost:$Port/admin/")
Write-Host ("  手机端      http://localhost:$Port/m/")
Write-Host ("  手机注册    http://localhost:$Port/register")
Write-Host ''
Write-Host ("  静态目录    $HtmlDir")
Write-Host ("  接口代理    http://$Backend       (nginx -> 后端)")
Write-Host ("  配置文件    $ConfOut")
Write-Host ("  日志文件    $Runtime\logs\")
Write-Host ''
Write-Host '  停止服务：scripts\stop-nginx.bat'
Write-Host ''

Start-Process "http://localhost:$Port/"
