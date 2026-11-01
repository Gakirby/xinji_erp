#Requires -Version 5.1
<#
    XinJi ERP - start everything: backend jar (8080) + nginx (8090)

    Usage:
        powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
#>
param(
    [int]$FrontPort = 8090
)
$ErrorActionPreference = 'Stop'

$Root    = Split-Path -Parent $PSScriptRoot
$JarPath = Join-Path $Root 'server\target\xinji-erp.jar'

function Test-TcpPort([int]$p) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $p)
        $ok = $c.Connected; $c.Close(); return $ok
    } catch { return $false }
}

Write-Host ''
Write-Host '=============================================================' -ForegroundColor Cyan
Write-Host '  鑫基矿业 ERP · 一键启动（后端 + nginx 前端）' -ForegroundColor Cyan
Write-Host '=============================================================' -ForegroundColor Cyan
Write-Host ''

# ---------- 1. backend ----------
if (Test-TcpPort 8080) {
    Write-Host '[ok] 后端已在运行：http://127.0.0.1:8080（IDEA 或其它）' -ForegroundColor Green
} elseif (Test-TcpPort 8081) {
    Write-Host '[ok] 后端已在运行：http://127.0.0.1:8081' -ForegroundColor Green
} else {
    if (-not (Test-Path $JarPath)) {
        Write-Host "[x] 找不到后端 jar：$JarPath" -ForegroundColor Red
        Write-Host '    两种办法：' -ForegroundColor Yellow
        Write-Host '      a) IDEA 里运行 ErpApplication（8080）后重跑本脚本' -ForegroundColor Yellow
        Write-Host '      b) cd server && mvn clean package -DskipTests 生成 jar 后重跑' -ForegroundColor Yellow
        Write-Host ''
        Write-Host '    先只启动前端（页面能开，接口会 502）...' -ForegroundColor Yellow
    } else {
        # find java
        $java = $null
        $candidates = @(
            $env:JAVA_HOME,
            'C:\Program Files\Java\jdk1.8.0_241',
            'C:\Program Files\Java\jdk1.8.0_281',
            'C:\Program Files\Java\jdk-8',
            'C:\Program Files\Java\jre1.8.0_241'
        ) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) }
        if ($candidates) { $java = Join-Path $candidates[0] 'bin\java.exe' }
        elseif (Get-Command java -ErrorAction SilentlyContinue) { $java = 'java' }

        if (-not $java) {
            Write-Host '[x] 未找到 java，请安装 JDK 8 或用 IDEA 启动后端' -ForegroundColor Red
        } else {
            Write-Host "[..] 启动后端 jar（8080）..." -ForegroundColor Yellow
            Start-Process -FilePath $java `
                -ArgumentList "-Dfile.encoding=UTF-8 -jar `"$JarPath`" --server.port=8080" `
                -WorkingDirectory (Join-Path $Root 'server') `
                -WindowStyle Minimized
            $ready = $false
            for ($i = 0; $i -lt 45; $i++) {
                Start-Sleep -Seconds 1
                if (Test-TcpPort 8080) { $ready = $true; break }
            }
            if ($ready) { Write-Host "[ok] 后端已就绪（等待 ${i}s）：http://127.0.0.1:8080" -ForegroundColor Green }
            else { Write-Host '[!] 后端 45 秒内未就绪，继续启动前端（稍后接口会自动通）' -ForegroundColor Yellow }
        }
    }
}

# ---------- 2. nginx ----------
& (Join-Path $PSScriptRoot 'start-nginx.ps1') -Port $FrontPort
