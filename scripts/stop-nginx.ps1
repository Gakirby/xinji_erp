#Requires -Version 5.1
<#
    XinJi ERP - stop nginx
#>
$ErrorActionPreference = 'SilentlyContinue'

$Root    = Split-Path -Parent $PSScriptRoot
$Runtime = Join-Path $Root 'deploy\nginx-runtime'
$NginxExe = Join-Path $Runtime 'nginx.exe'

Write-Host ''
Write-Host '  停止 nginx ...' -ForegroundColor Cyan

$procs = Get-Process nginx
if (-not $procs) {
    Write-Host '  [ok] nginx 未在运行' -ForegroundColor Green
    Write-Host ''
    exit 0
}

# 优雅退出：给 nginx 自己收尾的机会（会等现有请求处理完）
if (Test-Path $NginxExe) {
    Push-Location $Runtime
    & $NginxExe -s quit 2>&1 | Out-Null
    Pop-Location
    Start-Sleep -Seconds 2
}

# 兜底：还有残留就强杀
$left = Get-Process nginx
if ($left) {
    $left | Stop-Process -Force
    Start-Sleep -Seconds 1
}

$still = Get-Process nginx
if ($still) {
    Write-Host '  [x] 仍有 nginx 进程未能结束，请以管理员身份重试' -ForegroundColor Red
    exit 1
}

Write-Host '  [ok] nginx 已停止' -ForegroundColor Green
Write-Host ''
