# 正确且安全的部署机流程
# 保持 .env.prod 被 gitignore，部署机自己持有真实文件：
# cd <部署机 X-box 目录>
# 1) 确保 .env.prod / .env.prod.backend 里是安全密码
#    —— 直接把开发机那两文件里的明文串拷过来填进去（可抄我们生成的几串），
#       或者 cp .env.prod.example .env.prod 后手动填。总之：别进 git。
# 2) 清卷（当前卷里还是旧的坏密码，必须清）
#docker compose -f docker-compose.base.yml -f docker-compose.prod.yml --env-file .env.prod down -v
# 3) 部署
#powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -Env prod





# 运行：powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 [-Env dev|prod|staging] [-SkipOllama] [-DryRun] [-Proxy <url>]
# deploy.ps1 — X-box 统一部署脚本（Windows），支持通过 -Env 指定目标环境
#
# 阶段 0  解析参数 + 前置检查（Docker 守护进程 / docker compose）
# 阶段 1  按所选环境准备 env 文件（缺失则从 .example 复制；ChangeMe_* 占位符自动生成强随机值；仅处理该环境需要的文件，绝不触碰其它环境真实值）
# 阶段 2  校验凭证契约（不同环境不同规则）
# 阶段 3  准备宿主 Ollama（除非 -SkipOllama）
# 阶段 4  按所选环境启动栈（docker compose ... up -d --build）
# 阶段 5  等待关键服务健康并探测入口
# 阶段 6  -DryRun 时仅执行阶段 0/1/2
#
# 参数：-Env <dev|prod|staging>  目标环境（默认 prod，保持向后兼容）
#       -SkipOllama               跳过 Ollama 准备
#       -DryRun                   只检查/准备不启动
#       -Proxy <url>             透传给内联 Ollama 准备函数 Invoke-OllamaSetup
#       -ResetMysql               删除 MySQL 数据卷并重建空库（需二次确认；非交互环境须 -Force）
#       -Force                    非交互环境下强制确认删除 MySQL 数据卷（仅配合 -ResetMysql）
#
# 安全红线：绝不硬编码真实密码；绝不覆盖非占位符值；不打印密码明文；不改 docker-compose/网关/源码。
# 日志红线：每条日志带 ISO 时间戳前缀 [yyyy-MM-dd HH:mm:ss] [deploy]；env 文件以 UTF-8 无 BOM + LF 写入。

[CmdletBinding()]
param(
    [ValidateSet('dev', 'prod', 'staging')]
    [string]$Env = 'prod',
    [switch]$SkipOllama,
    [switch]$DryRun,
    [string]$Proxy = '',
    [switch]$ResetMysql,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false  # 原生命令(stderr 警告，如 compose 变量未设置)不视为终止错误，避免误杀已成功的部署（PS7.3+ 生效；PS5.1 无害）

# 切换到项目根目录（脚本位于 scripts/）
$RootDir = Resolve-Path (Join-Path $PSScriptRoot '..')
Set-Location $RootDir

# ---------- 环境 → 配置映射 ----------
$Configs = @{
    dev = @{
        ComposeFiles     = @('docker-compose.base.yml', 'docker-compose.business-prj.dev.yml')
        EnvFile          = '.env.dev'
        EnvFiles         = @('.env.dev', '.env.backend')
        EnvExamples      = @('.env.dev.example', '.env.backend.example')
        WaitServices     = @('mysql', 'redis', 'prj-backend-c', 'prj-frontend', 'prj-php')
        CriticalServices = @('mysql', 'redis', 'prj-backend-c')
    }
    prod = @{
        ComposeFiles     = @('docker-compose.base.yml', 'docker-compose.prod.yml')
        EnvFile          = '.env.prod'
        EnvFiles         = @('.env.dev', '.env.prod', '.env.prod.backend')
        EnvExamples      = @('.env.dev.example', '.env.prod.example', '.env.prod.backend.example')
        WaitServices     = @('mysql', 'redis', 'prj-backend-c', 'prj-frontend', 'prj-php')
        CriticalServices = @('mysql', 'redis', 'prj-backend-c')
    }
    staging = @{
        ComposeFiles     = @('docker-compose.base.yml', 'docker-compose.staging.yml')
        EnvFile          = '.env.staging'
        EnvFiles         = @('.env.staging', '.env.staging.backend')
        EnvExamples      = @('.env.staging.example', '.env.staging.backend.example')
        WaitServices     = @('mysql', 'redis', 'prj-backend-c', 'prj-frontend', 'prj-php')
        CriticalServices = @('mysql', 'redis', 'prj-backend-c')
    }
}

function Log($msg, $ForegroundColor) {
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    if ($ForegroundColor) {
        Write-Host "[$ts] [deploy] $msg" -ForegroundColor $ForegroundColor
    } else {
        Write-Host "[$ts] [deploy] $msg"
    }
}

# 生成 32 位 [A-Za-z0-9] 强随机串（密码学安全）
function New-RandStr {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $b64 = [Convert]::ToBase64String($bytes)
    $clean = $b64 -replace '[^A-Za-z0-9]', ''
    if ($clean.Length -gt 32) { $clean = $clean.Substring(0, 32) }
    return $clean
}

# 读取 env 文件中某 key 的当前值
function Get-EnvVal {
    param([string]$File, [string]$Key)
    if (-not (Test-Path $File)) { return '' }
    $line = Get-Content -Encoding UTF8 $File | Where-Object { $_ -match "^\s*$Key=" } | Select-Object -First 1
    if (-not $line) { return '' }
    $parts = $line -split '=', 2
    return $parts[1]
}

# 若 key 当前值为 ChangeMe 占位符则写入新值，否则保留（不覆盖真实值）；不存在则追加
function Set-EnvValIfPlaceholder {
    param([string]$File, [string]$Key, [string]$NewVal)
    $cur = Get-EnvVal -File $File -Key $Key
    if ($cur -and $cur -notmatch 'ChangeMe') { return }
    $lines = Get-Content -Encoding UTF8 $File
    $out = @()
    $replaced = $false
    foreach ($l in $lines) {
        if ($l -match "^\s*$Key=") {
            $out += "$Key=$NewVal"
            $replaced = $true
        } else {
            $out += $l
        }
    }
    if (-not $replaced) { $out += "$Key=$NewVal" }
    $text = ($out -join "`n") + "`n"
    [System.IO.File]::WriteAllText($File, $text, [System.Text.UTF8Encoding]::new($false))
}

# 从若干 (File, Key) 中取第一个真实（非 ChangeMe）值作为主口令；都没有则返回新随机值
# 入参为多个 hashtable：@{File=...; Key=...}
function Get-MasterPassword {
    param([array]$Pairs)
    foreach ($p in $Pairs) {
        $v = Get-EnvVal -File $p.File -Key $p.Key
        if ($v -and $v -notmatch 'ChangeMe') { return $v }
    }
    return New-RandStr
}

function Test-Prereqs {
    Log "===== 阶段 0：前置检查 ====="
    & docker info > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Log "错误：Docker 守护进程未运行，请先启动 Docker Desktop。" Red
        throw "Docker 守护进程未运行"
    }
    & docker compose version > $null 2>&1
    if ($LASTEXITCODE -ne 0) {
        Log "错误：docker compose 不可用（需要 Docker Compose V2）。" Red
        throw "docker compose 不可用"
    }
    Log "Docker 守护进程与 docker compose 就绪。"
}

function Prepare-EnvFiles {
    param([hashtable]$Cfg)
    Log "===== 阶段 1：检查并准备 env 文件（环境：$Env） ====="
    for ($i = 0; $i -lt $Cfg.EnvFiles.Count; $i++) {
        $f = $Cfg.EnvFiles[$i]; $ex = $Cfg.EnvExamples[$i]
        if (-not (Test-Path $f)) {
            if (-not (Test-Path $ex)) {
                Log "错误：模板 $ex 不存在，无法创建 $f" Red
                exit 1
            }
            Copy-Item $ex $f
            Log "已基于 $ex 创建 $f"
        }
    }

    # 仅基于本环境 map 中的文件推导/对齐主数据源口令（其它环境文件绝不触碰）
    $master = ''
    switch ($Env) {
        'dev' {
            $master = Get-MasterPassword @(
                @{File = '.env.dev'; Key = 'SPRING_DATASOURCE_PASSWORD' },
                @{File = '.env.dev'; Key = 'PRJ_DB_PWD' },
                @{File = '.env.backend'; Key = 'SPRING_DATASOURCE_PASSWORD' }
            )
            Set-EnvValIfPlaceholder -File '.env.dev' -Key 'SPRING_DATASOURCE_PASSWORD' -NewVal $master
            Set-EnvValIfPlaceholder -File '.env.dev' -Key 'PRJ_DB_PWD' -NewVal $master
            Set-EnvValIfPlaceholder -File '.env.backend' -Key 'SPRING_DATASOURCE_PASSWORD' -NewVal $master
        }
        'prod' {
            $master = Get-MasterPassword @(
                @{File = '.env.dev'; Key = 'SPRING_DATASOURCE_PASSWORD' },
                @{File = '.env.prod.backend'; Key = 'SPRING_DATASOURCE_PASSWORD' },
                @{File = '.env.prod'; Key = 'PRJ_DB_PWD' },
                @{File = '.env.dev'; Key = 'PRJ_DB_PWD' }
            )
            Set-EnvValIfPlaceholder -File '.env.dev' -Key 'SPRING_DATASOURCE_PASSWORD' -NewVal $master
            Set-EnvValIfPlaceholder -File '.env.dev' -Key 'PRJ_DB_PWD' -NewVal $master
            Set-EnvValIfPlaceholder -File '.env.prod.backend' -Key 'SPRING_DATASOURCE_PASSWORD' -NewVal $master
            Set-EnvValIfPlaceholder -File '.env.prod' -Key 'PRJ_DB_PWD' -NewVal $master
        }
        'staging' {
            $master = Get-MasterPassword @(
                @{File = '.env.staging'; Key = 'PRJ_DB_PWD' },
                @{File = '.env.staging.backend'; Key = 'SPRING_DATASOURCE_PASSWORD' },
                @{File = '.env.dev'; Key = 'SPRING_DATASOURCE_PASSWORD' }
            )
            Set-EnvValIfPlaceholder -File '.env.staging' -Key 'PRJ_DB_PWD' -NewVal $master
            Set-EnvValIfPlaceholder -File '.env.staging.backend' -Key 'SPRING_DATASOURCE_PASSWORD' -NewVal $master
        }
    }

    # 逐文件替换其余 ChangeMe 占位符（仅本环境 map 中的文件；UTF-8 无 BOM + LF）
    foreach ($f in $Cfg.EnvFiles) {
        $lines = Get-Content -Encoding UTF8 $f
        $out = @()
        foreach ($l in $lines) {
            if ($l -match '^([A-Za-z0-9_]+)=(.*)$') {
                $k = $Matches[1]; $v = $Matches[2]
                if ($v -match 'ChangeMe') {
                    $out += "$k=$(New-RandStr)"
                    continue
                }
            }
            $out += $l
        }
        $text = ($out -join "`n") + "`n"
        [System.IO.File]::WriteAllText($f, $text, [System.Text.UTF8Encoding]::new($false))
    }

    # 【2026-07-23 安全/健壮性】确保 ADMIN_INIT_PWD 存在：未设置时 ensure_admin_hash.php 会回退弱默认口令，
    # 且 compose 会把“变量未设置”警告打到 stderr，在严格错误偏好下被误判为致命错误。此处自动补强随机值。
    $adminEnv = Join-Path $RootDir '.env.prod'
    if (Test-Path $adminEnv) {
        $adminLines = Get-Content -Encoding UTF8 $adminEnv
        if (-not ($adminLines -match '^ADMIN_INIT_PWD=')) {
            $adminLines += "ADMIN_INIT_PWD=$(New-RandStr)"
            $adminText = ($adminLines -join "`n") + "`n"
            [System.IO.File]::WriteAllText($adminEnv, $adminText, [System.Text.UTF8Encoding]::new($false))
            Log "已为 .env.prod 自动生成 ADMIN_INIT_PWD（强随机），避免弱默认口令与 compose 变量警告。" Yellow
        }
    }

    Log "env 文件准备完成（仅处理环境 $Env 所需文件；占位符已替换；已有真实值已保留）。"
}

function Ensure-SslCert {
    $sslDir = Join-Path $RootDir 'gateway/nginx/ssl'
    $crt = Join-Path $sslDir 'prj.crt'
    $key = Join-Path $sslDir 'prj.key'
    if ((Test-Path $crt) -and (Test-Path $key)) {
        Log "SSL 证书已存在，跳过生成。"
        return
    }
    Log "SSL 证书缺失，尝试自动生成自签证书（prj.crt / prj.key）..." Yellow
    $sslDir = Split-Path $crt -Parent
    if (-not (Test-Path $sslDir)) { New-Item -ItemType Directory -Path $sslDir -Force | Out-Null }
    # 定位 openssl：优先 PATH，其次常见 Windows 安装位置（Git for Windows 自带）
    $opensslExe = $null
    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { $opensslExe = $cmd.Source }
    if (-not $opensslExe) {
        $candidates = @(
            'C:\Program Files\Git\usr\bin\openssl.exe',
            'C:\Program Files (x86)\Git\usr\bin\openssl.exe',
            "$env:LOCALAPPDATA\Programs\Git\usr\bin\openssl.exe",
            'C:\Program Files\OpenSSL-Win64\bin\openssl.exe',
            'C:\Program Files\OpenSSL-Win32\bin\openssl.exe'
        )
        foreach ($c in $candidates) {
            if ((Test-Path $c)) { $opensslExe = $c; break }
        }
    }
    if (-not $opensslExe) {
        Log "错误：未找到 openssl，无法自动生成证书。请手动生成后重试：" Red
        Log "  cd gateway/nginx/ssl" Red
        Log "  openssl req -x509 -newkey rsa:2048 -nodes -keyout prj.key -out prj.crt -days 3650 -subj /CN=localhost" Red
        Log "（Windows 可安装 Git for Windows 后重跑；或用 winget install GnuWin32.OpenSSL）" Yellow
        exit 1
    }
    # Git 自带 openssl 常因找不到 openssl.cnf 而失败，显式指定其配置（若存在）
    if (-not $env:OPENSSL_CONF) {
        $binDir = Split-Path $opensslExe
        $cnfCandidates = @(
            (Join-Path $binDir '..\ssl\openssl.cnf'),
            (Join-Path $binDir '..\etc\ssl\openssl.cnf')
        )
        foreach ($cc in $cnfCandidates) {
            if (Test-Path $cc) { $env:OPENSSL_CONF = (Resolve-Path $cc).Path; break }
        }
    }
    # 生成证书：openssl 进度/警告走 stderr，本地降级 ErrorActionPreference 防止被顶层 catch 当成致命错误
    $opensslLog = Join-Path $sslDir 'openssl_gen.log'
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $opensslExe req -x509 -newkey rsa:2048 -nodes -keyout $key -out $crt -days 3650 -subj /CN=localhost > $opensslLog 2>&1
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    if ((Test-Path $crt) -and (Test-Path $key)) {
        Log "SSL 自签证书已生成：$sslDir"
        Remove-Item $opensslLog -ErrorAction SilentlyContinue
    } else {
        Log "错误：证书生成失败（openssl 退出码 $LASTEXITCODE）。openssl 输出：" Red
        if (Test-Path $opensslLog) { Get-Content $opensslLog | ForEach-Object { Log "  $_" Red } }
        Log "提示：若报找不到 openssl.cnf，可手动 `set OPENSSL_CONF=<path>` 后重跑；或直接在 gateway/nginx/ssl 放入 prj.crt/prj.key。" Yellow
        exit 1
    }
}

function Test-Contract {
    Log "===== 阶段 2：校验凭证契约（环境：$Env） ====="
    switch ($Env) {
        'dev' {
            $devDs = Get-EnvVal -File '.env.dev' -Key 'SPRING_DATASOURCE_PASSWORD'
            $beDs = Get-EnvVal -File '.env.backend' -Key 'SPRING_DATASOURCE_PASSWORD'
            if (-not $devDs -or -not $beDs) {
                throw "dev 凭证契约校验失败：SPRING_DATASOURCE_PASSWORD 存在空值。"
            }
            if ($devDs -ne $beDs) {
                throw "dev 凭证契约校验失败：.env.dev 与 .env.backend 的 SPRING_DATASOURCE_PASSWORD 不一致。"
            }
        }
        'prod' {
            $devDs = Get-EnvVal -File '.env.dev' -Key 'SPRING_DATASOURCE_PASSWORD'
            $prodBeDs = Get-EnvVal -File '.env.prod.backend' -Key 'SPRING_DATASOURCE_PASSWORD'
            $prodDb = Get-EnvVal -File '.env.prod' -Key 'PRJ_DB_PWD'
            if (-not $devDs -or -not $prodBeDs -or -not $prodDb) {
                throw "prod 凭证契约校验失败：SPRING_DATASOURCE_PASSWORD / PRJ_DB_PWD 存在空值。"
            }
            if ($devDs -ne $prodBeDs) {
                throw "prod 凭证契约校验失败：.env.dev 的 SPRING_DATASOURCE_PASSWORD 与 .env.prod.backend 的不一致。"
            }
            if ($prodDb -ne $prodBeDs) {
                throw "prod 凭证契约校验失败：.env.prod 的 PRJ_DB_PWD 与 .env.prod.backend 的 SPRING_DATASOURCE_PASSWORD 不一致。"
            }
        }
        'staging' {
            $stDb = Get-EnvVal -File '.env.staging' -Key 'PRJ_DB_PWD'
            $stBeDs = Get-EnvVal -File '.env.staging.backend' -Key 'SPRING_DATASOURCE_PASSWORD'
            if (-not $stDb -or -not $stBeDs) {
                throw "staging 凭证契约校验失败：PRJ_DB_PWD / SPRING_DATASOURCE_PASSWORD 存在空值。"
            }
            if ($stDb -ne $stBeDs) {
                throw "staging 凭证契约校验失败：.env.staging 的 PRJ_DB_PWD 与 .env.staging.backend 的 SPRING_DATASOURCE_PASSWORD 不一致。"
            }
            if (Test-Path '.env.dev') {
                $devDs = Get-EnvVal -File '.env.dev' -Key 'SPRING_DATASOURCE_PASSWORD'
                if ($devDs -and $devDs -notmatch 'ChangeMe' -and $devDs -ne $stBeDs) {
                    Log "提示：.env.dev 的 SPRING_DATASOURCE_PASSWORD 与 staging 不同，如需跨环境复用数据库请手动对齐（脚本不修改 .env.dev）。" Yellow
                }
            }
        }
    }
    Log "凭证契约校验通过：$Env 环境三处/两处数据源口令一致。"
}

# ---------- 3. 宿主 Ollama 准备（原 setup-host-ollama.ps1 已内联）----------
# 安装/启动/绑定 0.0.0.0:11434/拉取 bge-m3/健康检查。
# 支持 -SkipInstall（跳过安装但仍启动+拉取+校验）与 -Proxy <url>（透传到安装与拉取）。
function Invoke-OllamaSetup {
    [CmdletBinding()]
    param(
        [switch]$PullOnly,
        [switch]$SkipInstall,
        [string]$InstallerUrl = 'https://ghproxy.net/https://github.com/ollama/ollama/releases/download/v0.32.0/OllamaSetup.exe',
        [string]$Proxy = ''
    )

    $OLLAMA_HOST_VALUE = '0.0.0.0:11434'
    $MODEL_NAME = 'bge-m3:latest'

    # ---------- proxy resolution (from -Proxy, else HTTPS_PROXY/HTTP_PROXY) ----------
    if (-not $Proxy) {
        $Proxy = $env:HTTPS_PROXY
        if (-not $Proxy) { $Proxy = $env:HTTP_PROXY }
    }
    if ($Proxy) {
        Log "Proxy enabled: $Proxy"
        $env:HTTPS_PROXY = $Proxy
        $env:HTTP_PROXY = $Proxy
    }

    # Refresh PATH from system + user so a just-installed ollama is discoverable in this session
    $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')

    # ---------- 0. Detect existing ready Ollama + bge-m3 (reuse fast path) ----------
    # 已安装 + 服务就绪(:11434 可探测) + bge-m3 模型已存在 → 直接复用，跳过安装与拉取。
    function Test-OllamaHealthy {
        $installed = [bool](Get-Command ollama -ErrorAction SilentlyContinue)
        if (-not $installed) { return $false }
        $r = $null
        try {
            $r = Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 5 -ErrorAction SilentlyContinue
        } catch { $r = $null }
        if (-not $r -or -not $r.models) { return $false }
        if ($r.models | Where-Object { $_.name -eq $MODEL_NAME }) { return $true }
        # fallback: 某些版本 ollama list 输出与 API 字段不一致，再查一次
        try {
            $lst = & ollama list 2>$null
            if ($lst -match [regex]::Escape($MODEL_NAME)) { return $true }
        } catch { }
        return $false
    }

    if (-not $PullOnly) {
        if (Test-OllamaHealthy) {
            Log "[成功] Ollama + bge-m3 就绪"
            Log "（已检测就绪，复用，跳过安装与拉取）"
            Log "Backend can call http://host.docker.internal:11434/api/embed"
            return
        }
        Log "Ollama 未就绪或 bge-m3 缺失，开始自动安装 / 启动 / 拉取流程 ..."
    }

    # ---------- 1. Install Ollama (download from fast source, silent install) ----------
    function Get-Aria2Exe {
        # Prefer aria2c on PATH. If missing, bootstrap it via winget/choco (NOT a GitHub zip download):
        # winget's package source is reachable here, and the aria2 binary is only ~2.5MB, so installing
        # it is quick even on a throttled network. A 16-connection download is the fastest channel when
        # the CDN throttles per connection, so it is worth a one-time install.
        $aria2 = Get-Command aria2c -ErrorAction SilentlyContinue
        if ($aria2) { return $aria2.Source }
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            try {
                Log "aria2c not found; installing via winget (one-time, ~2.5MB)，进度实时显示 ..."
                $p = Start-Process -FilePath 'winget' -ArgumentList @('install','--exact','--id','aria2.aria2','-e','--accept-package-agreements','--accept-source-agreements') -Wait -NoNewWindow -PassThru
                if ($p.ExitCode -eq 0) {
                    $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
                    $aria2 = Get-Command aria2c -ErrorAction SilentlyContinue
                    if ($aria2) { return $aria2.Source }
                } else {
                    Log "winget install aria2 返回非零退出码 $($p.ExitCode)，将继续尝试其它方式。" -ForegroundColor Yellow
                }
            } catch {
                Log "winget install aria2 失败。" -ForegroundColor Yellow
            }
        }
        if (Get-Command choco -ErrorAction SilentlyContinue) {
            try {
                Log "aria2c not found; installing via choco，进度实时显示 ..."
                $p = Start-Process -FilePath 'choco' -ArgumentList @('install','aria2','-y') -Wait -NoNewWindow -PassThru
                if ($p.ExitCode -eq 0) {
                    $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
                    $aria2 = Get-Command aria2c -ErrorAction SilentlyContinue
                    if ($aria2) { return $aria2.Source }
                } else {
                    Log "choco install aria2 返回非零退出码 $($p.ExitCode)。" -ForegroundColor Yellow
                }
            } catch {
                Log "choco install aria2 失败。" -ForegroundColor Yellow
            }
        }
        return $null
    }

    function Find-LocalInstaller {
        $cands = @(
            (Join-Path $PSScriptRoot 'OllamaSetup.exe'),
            (Join-Path $PSScriptRoot 'bin\OllamaSetup.exe'),
            (Join-Path $env:TEMP 'OllamaSetup.exe')
        )
        foreach ($p in $cands) {
            if (Test-Path $p) {
                $sz = (Get-Item $p).Length
                if ($sz -gt 100MB) { return $p }
                Log "Local OllamaSetup.exe too small ($sz bytes) at $p, ignoring." -ForegroundColor Yellow
            }
        }
        return $null
    }

    function Install-OllamaFromMirror {
        param(
            [string]$OutFile = (Join-Path $env:TEMP 'OllamaSetup.exe')
        )
        # OFFLINE shortcut: use a local installer if present, skip ALL network downloads.
        $local = Find-LocalInstaller
        if ($local) {
            Log "Using local installer (offline mode): $local"
            Copy-Item -Path $local -Destination $OutFile -Force
            return $OutFile
        }
        # 进度可见性：明确告知用户开始下载安装包，并提示可能较慢。
        Log "开始下载 OllamaSetup.exe 安装包（将依次尝试多个镜像源，下载进度实时显示，请耐心等待；境外 CDN 可能较慢，可用 -Proxy <url> 加速）..."
        $candidates = @(
            'https://ollama.com/download/OllamaSetup.exe',
            $InstallerUrl,
            'https://mirror.ghproxy.com/https://github.com/ollama/ollama/releases/download/v0.32.0/OllamaSetup.exe',
            'https://github.moeyy.xyz/https://github.com/ollama/ollama/releases/download/v0.32.0/OllamaSetup.exe',
            'https://hub.gitmirror.com/https://github.com/ollama/ollama/releases/download/v0.32.0/OllamaSetup.exe'
        )
        $aria2 = Get-Aria2Exe
        foreach ($url in $candidates) {
            # Remove any partial file from a previous (slow/failed) attempt
            if (Test-Path $OutFile) { Remove-Item $OutFile -Force -ErrorAction SilentlyContinue }
            try {
                if ($aria2) {
                    Log "aria2c multi-thread download: $url"
                    $ariaArgs = @('-x', '16', '-s', '16', '-k', '1M', '--connect-timeout=15', '--timeout=30', '--max-tries=1', '-o', (Split-Path $OutFile -Leaf), $url, '--dir', (Split-Path $OutFile -Parent))
                    if ($Proxy) { $ariaArgs += '--all-proxy'; $ariaArgs += $Proxy }
                    # 关键修复：移除 -RedirectStandard*，让 aria2 多线程下载进度条实时显示到终端，避免「长时间无输出」误以为卡死。
                    $p = Start-Process -FilePath $aria2 -ArgumentList $ariaArgs -Wait -NoNewWindow -PassThru
                    if ($p.ExitCode -ne 0) { throw "aria2c exit code $($p.ExitCode)" }
                } else {
                    Log "Downloading installer via curl: $url"
                    # -# 显示下载进度条；移除 -RedirectStandard*，让 curl 下载进度实时显示到终端。
                    $curlArgs = @('-#', '-L', '-f', '-S', '--connect-timeout', '15', '--max-time', '90', '--retry', '2', '--speed-time', '90', '--speed-limit', '100000', '-o', $OutFile, $url)
                    if ($Proxy) { $curlArgs = @('-x', $Proxy) + $curlArgs }
                    $p = Start-Process -FilePath 'curl.exe' -ArgumentList $curlArgs -Wait -NoNewWindow -PassThru
                    if ($p.ExitCode -ne 0) { throw "curl exit code $($p.ExitCode)" }
                }
                if (Test-Path $OutFile) {
                    $sz = (Get-Item $OutFile).Length
                    if ($sz -gt 10MB) { return $OutFile }
                    Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
                }
            } catch {
                Log "镜像下载失败：$url（网络不可达或被限速，跳过该源）" -ForegroundColor Yellow
                if (Test-Path $OutFile) { Remove-Item $OutFile -Force -ErrorAction SilentlyContinue }
            }
        }
        Log "All download sources failed or are too slow (your network appears to throttle foreign CDNs)." -ForegroundColor Yellow
        Log "Options: (1) place OllamaSetup.exe beside this script for offline install;" -ForegroundColor Yellow
        Log "         (2) run with -Proxy <url>; (3) install aria2 first (winget install aria2) for multi-thread." -ForegroundColor Yellow
        return $null
    }

    function Install-Ollama {
        # 1) 优先 winget 安装（官方渠道，无需手动下载安装包）
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            try {
                # 关键修复：移除 -RedirectStandard*，让 winget 安装进度实时打到终端，用户可看到下载/安装进度，不再「长时间无输出」误以为卡死。-NoNewWindow 让子进程继承控制台输出。
                Log "开始用 winget 安装 Ollama（Ollama.Ollama），安装进度实时显示，请稍候 ..."
                $p = Start-Process -FilePath 'winget' -ArgumentList @('install','--exact','--id','Ollama.Ollama','-e','--accept-package-agreements','--accept-source-agreements') -Wait -NoNewWindow -PassThru
                if ($p.ExitCode -eq 0) {
                    $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
                    return
                }
                Log "winget 安装 Ollama 返回非零退出码 $($p.ExitCode)，回退到下载安装包。" -ForegroundColor Yellow
            } catch {
                Log "winget 执行异常（$(($_.Exception.Message -split "`n")[0])），回退到下载安装包。" -ForegroundColor Yellow
            }
        } else {
            Log "winget 不可用，回退到下载 OllamaSetup.exe 静默安装。" -ForegroundColor Yellow
        }

        # 2) 回退：下载 OllamaSetup.exe 并静默安装（/S）；下载失败必须被 catch。
        try {
            $dest = Install-OllamaFromMirror
        } catch {
            $dest = $null
            Log "下载 OllamaSetup.exe 异常：$(($_.Exception.Message -split "`n")[0])" -ForegroundColor Yellow
        }
        if (-not $dest) {
            Log "错误：Ollama 自动安装失败（winget 不可用且本机网络无法下载安装包；境外 CDN 可能被限速/不可达）。" -ForegroundColor Red
            Log "请任选其一后重跑本脚本：" -ForegroundColor Red
            Log "  1) 手动安装 Ollama（winget install Ollama.Ollama 或你本机可用渠道），再用 -SkipOllama 跳过安装：" -ForegroundColor Red
            Log "       powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -Env <环境> -SkipOllama" -ForegroundColor Red
            Log "  2) 用代理重跑：... -Env <环境> -Proxy <http://代理:端口>" -ForegroundColor Red
            Log "  3) 把 OllamaSetup.exe 放到 scripts/ 目录旁（离线安装模式，脚本会自动识别）" -ForegroundColor Red
            throw "Ollama 自动安装失败（网络受限），请按上方提示手动处理或用 -SkipOllama / -Proxy。"
        }
        Log "开始静默安装 OllamaSetup.exe（/S，通常需要 10~30 秒，请稍候）..."
        Start-Process -FilePath $dest -ArgumentList '/S' -Wait
        # Refresh PATH so ollama is usable immediately in this session
        $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
    }

    if (-not $PullOnly -and -not $SkipInstall) {
        if (Get-Command ollama -ErrorAction SilentlyContinue) {
            Log "ollama already installed: $(Get-Command ollama | Select-Object -ExpandProperty Source)"
        } else {
            Install-Ollama
        }
    } elseif ($SkipInstall) {
        Log "SkipInstall set: assuming Ollama already installed, skipping installer."
    }

    # ---------- 1.5 Admin check (Machine-scope OLLAMA_HOST requires Administrator) ----------
    $isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Log "WARNING: not running as Administrator. Machine-scope OLLAMA_HOST cannot be set; relying on any existing Machine value. If Ollama binds 127.0.0.1, re-run this script as Administrator." Yellow
    }

    # ---------- 2. Persist OLLAMA_HOST = 0.0.0.0:11434 ----------
    # IMPORTANT: on Windows Ollama runs as a SERVICE that reads MACHINE-scope env only.
    # A User-scope OLLAMA_HOST is ignored by the service (it keeps binding 127.0.0.1),
    # so we must write to Machine scope. Running this script as Administrator is REQUIRED.
    $envScope = 'Machine'
    $existing = [System.Environment]::GetEnvironmentVariable('OLLAMA_HOST', $envScope)
    if ($existing -ne $OLLAMA_HOST_VALUE) {
        Log "Setting system(Machine) env OLLAMA_HOST=$OLLAMA_HOST_VALUE (admin required)"
        [System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', $OLLAMA_HOST_VALUE, $envScope)
        $verify = [System.Environment]::GetEnvironmentVariable('OLLAMA_HOST', $envScope)
        if ($verify -ne $OLLAMA_HOST_VALUE) {
            Log "ERROR: failed to persist Machine OLLAMA_HOST (need Administrator). Ollama may fall back to 127.0.0.1." Red
        }
        $env:OLLAMA_HOST = $OLLAMA_HOST_VALUE
    } else {
        Log "System env OLLAMA_HOST already $OLLAMA_HOST_VALUE, skip."
        $env:OLLAMA_HOST = $OLLAMA_HOST_VALUE
    }

    # ---------- 3. Start Ollama so it binds 0.0.0.0:11434 ----------
    function Stop-AllOllama {
        try { Stop-Service -Name 'ollama' -Force -ErrorAction SilentlyContinue } catch { }
        $procs = Get-Process -Name 'ollama' -ErrorAction SilentlyContinue
        if ($procs) { $procs | Stop-Process -Force -ErrorAction SilentlyContinue }
    }
    function Get-OllamaListeners {
        (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalAddress)
    }

    if (-not $PullOnly) {
        # (a) Disable the service so its recovery policy cannot restart it and re-grab the port.
        try { Set-Service -Name 'ollama' -StartupType Disabled -ErrorAction SilentlyContinue } catch { }
        # (b) Kill every ollama process, a few rounds, until none remain.
        for ($k = 1; $k -le 5; $k++) {
            Stop-AllOllama
            if (-not (Get-Process -Name 'ollama' -ErrorAction SilentlyContinue)) { break }
            Start-Sleep -Seconds 2
        }
        # (c) Wait until port 11434 is TRULY free (no listener at all). Kill again if it reappears.
        $freed = $false
        for ($i = 1; $i -le 30; $i++) {
            $still = Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue
            if (-not $still) { $freed = $true; break }
            Stop-AllOllama
            Start-Sleep -Seconds 1
        }
        if (-not $freed) {
            Log "WARNING: port 11434 still held after 30s. Another Ollama instance keeps restarting it." -ForegroundColor Yellow
            Log "Manual fix: Stop-Service ollama; Get-Process ollama | Stop-Process -Force" -ForegroundColor Yellow
        }
        # (d) Launch our instance. On Windows, Ollama 'serve' honors the OLLAMA_HOST
        #     env var (Machine scope), NOT the --host CLI flag (unreliable on Windows).
        #     We rely on the Machine OLLAMA_HOST set above to bind 0.0.0.0:11434.
        Log "安装完成，正在启动 Ollama 服务（serve，通过 OLLAMA_HOST=$OLLAMA_HOST_VALUE 绑定，请稍候）..."
        Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WindowStyle Hidden
        # (e) Poll until WE are listening on 0.0.0.0.
        $bound = $false
        $stallCount = 0
        $machineHost = [System.Environment]::GetEnvironmentVariable('OLLAMA_HOST', 'Machine')
        for ($i = 1; $i -le 40; $i++) {
            Start-Sleep -Seconds 1
            $addrs = Get-OllamaListeners
            # On Windows a dual-stack bind to 0.0.0.0:11434 shows as '::' in
            # Get-NetTCPConnection; treat BOTH '0.0.0.0' and '::' (case-insensitive) as success.
            $lowerAddrs = $addrs | ForEach-Object { $_.ToLower() }
            if (($addrs -contains '0.0.0.0') -or ($lowerAddrs -contains '::')) { $bound = $true; break }
            if ($addrs -contains '127.0.0.1') {
                $stallCount++
                if ($stallCount -ge 3 -and $machineHost -eq $OLLAMA_HOST_VALUE) {
                    # Machine env already targets 0.0.0.0 but Ollama still binds 127.0.0.1:
                    # restarting the same command won't help. Stop and diagnose.
                    Log "Ollama keeps binding 127.0.0.1 although Machine OLLAMA_HOST=$machineHost. Stopping retry loop." Yellow
                    break
                }
                Log "Listener on 127.0.0.1 (not 0.0.0.0); evicting and retrying bind..." -ForegroundColor Yellow
                Stop-AllOllama
                Start-Sleep -Seconds 2
                Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WindowStyle Hidden
            }
        }
        $finalArr = @(Get-OllamaListeners)
        $finalAddrs = $finalArr -join ', '
        $finalLower = $finalArr | ForEach-Object { $_.ToLower() }

        # ----- Self-heal: if binding is bad AND we are Administrator, restart Ollama once
        # so it re-reads the Machine-scope OLLAMA_HOST (which targets 0.0.0.0). -----
        if (-not $bound -and $isAdmin) {
            Log "Self-heal: Ollama binding is wrong (admin detected). Restarting Ollama once to re-read Machine OLLAMA_HOST=$OLLAMA_HOST_VALUE..." Yellow
            Stop-AllOllama
            # Prefer restarting the Windows service (it reads Machine OLLAMA_HOST -> binds 0.0.0.0).
            # Fall back to a foreground 'ollama serve' (current process env has $env:OLLAMA_HOST set).
            $healOk = $false
            try { Start-Service -Name 'ollama' -ErrorAction SilentlyContinue; $healOk = $? } catch { $healOk = $false }
            if (-not $healOk) {
                Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WindowStyle Hidden
            }
            # Re-poll up to 20s for 0.0.0.0 or :: (dual-stack).
            for ($h = 1; $h -le 20; $h++) {
                Start-Sleep -Seconds 1
                $hAddrs = Get-OllamaListeners
                $hLower = $hAddrs | ForEach-Object { $_.ToLower() }
                if (($hAddrs -contains '0.0.0.0') -or ($hLower -contains '::')) {
                    $bound = $true
                    break
                }
            }
            $finalArr = @(Get-OllamaListeners)
            $finalAddrs = $finalArr -join ', '
            $finalLower = $finalArr | ForEach-Object { $_.ToLower() }
        }

        if ($bound) {
            if ($finalLower -contains '::' -and ($finalArr -notcontains '0.0.0.0')) {
                # Windows dual-stack bind: '::' accepts IPv4 too; containers reach it via host.docker.internal.
                Log "Ollama listening on dual-stack [::]:11434 (Windows dual-stack accepts IPv4; containers reach it via host.docker.internal:11434)." Yellow
            } else {
                Log "Ollama listening on: 0.0.0.0 :11434 (all listeners: $finalAddrs)"
            }
        } else {
            Log "WARNING: could not bind 0.0.0.0:11434; current listener(s): $finalAddrs. Containers may NOT reach Ollama." -ForegroundColor Yellow
            Log "Diagnostics: isAdmin=$isAdmin; Machine OLLAMA_HOST='$machineHost'; expected='$OLLAMA_HOST_VALUE'." Yellow
            Log "Manual fix: as Administrator run: [System.Environment]::SetEnvironmentVariable('OLLAMA_HOST','$OLLAMA_HOST_VALUE','Machine'); Stop-Service ollama; Get-Process ollama | Stop-Process -Force; Start-Process ollama -ArgumentList 'serve'" Yellow
        }
    }

    # ---------- 4. Pull bge-m3 (skip if already present locally) ----------
    $modelPresent = $false
    try {
        $list = & ollama list 2>$null
        if ($list -match [regex]::Escape($MODEL_NAME)) { $modelPresent = $true }
    } catch { }
    if ($modelPresent) {
        Log "模型 $MODEL_NAME 已在本地，跳过拉取（约 1.2GB 已就绪）。"
    } else {
        # 进度可见性：明确告知用户开始拉取大模型，并提示体积与耗时。
        Log "开始拉取 $MODEL_NAME（约 1.2GB 模型权重，首次下载可能较慢，进度实时显示，请耐心等待）..."
        # 关键修复：直接执行 ollama pull，让进度条实时输出到终端；不使用 $(...) 捕获或重定向吞掉输出。
        & ollama pull $MODEL_NAME
        Log "拉取完成：$MODEL_NAME 已就绪。"
    }

    # ---------- 5. Health check ----------
    Log "Waiting for Ollama to be ready ..."
    $ready = $false
    for ($i = 1; $i -le 30; $i++) {
        try {
            $tags = Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 5
            if ($tags.models -and ($tags.models | Where-Object { $_.name -eq $MODEL_NAME })) {
                $ready = $true
                break
            }
        } catch {
            # not ready yet, keep waiting
        }
        Start-Sleep -Seconds 2
    }

    if (-not $ready) {
        Log "ERROR: Ollama not ready or model $MODEL_NAME failed to pull." -ForegroundColor Red
        throw "Ollama health check failed ($MODEL_NAME)."
    }

    # Report the ACTUAL bind address (not a hardcoded claim).
    $actual = @(Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalAddress)
    Log "[成功] Ollama + bge-m3 就绪"
    if ($actual -contains '0.0.0.0') {
        Log "Host Ollama listening on 0.0.0.0:11434 (containers reach via host.docker.internal:11434)."
    } elseif ($actual -contains '::') {
        Log "Host Ollama listening on dual-stack [::]:11434 (Windows dual-stack accepts IPv4; containers reach it via host.docker.internal:11434)." -ForegroundColor Yellow
    } else {
        $addrStr = if ($actual) { $actual -join ', ' } else { 'none' }
        Log "Host Ollama ready but listening on $addrStr (containers may NOT reach it; 建议以管理员重设 Machine OLLAMA_HOST=0.0.0.0:11434 后重启)." -ForegroundColor Yellow
    }
    Log "Backend can call http://host.docker.internal:11434/api/embed"
}

function Start-Ollama {
    if ($SkipOllama) {
        Log "===== 阶段 3：跳过 Ollama 准备（-SkipOllama）====="
        $script:OllamaStatus = 'skipped'
        return
    }
    Log "===== 阶段 3：准备宿主 Ollama ..."
    try {
        Invoke-OllamaSetup -Proxy $Proxy
        $script:OllamaStatus = 'ok'
    } catch {
        # Ollama 仅是「向量化 / 语义检索」类功能的软依赖：后端容器启动时不强依赖它，
        # embedding 仅在请求时懒调用，缺失时按请求抛 EmbeddingException 优雅降级。
        # 因此 Ollama 准备失败不应阻断整个部署（exit code 保持成功）。
        Log "[警告] Ollama 自动安装失败，语义检索功能暂不可用，部署继续" Yellow
        Log "修复：以管理员身份设置 Machine 作用域 OLLAMA_HOST=0.0.0.0:11434，再执行 Stop-Service ollama; Get-Process ollama | Stop-Process -Force; Start-Process ollama -ArgumentList 'serve'；或重跑本脚本时加 -SkipOllama 跳过重复下载。" Yellow
        $script:OllamaStatus = 'failed'
    }
    Log "宿主 Ollama 准备阶段结束（状态：$($script:OllamaStatus)）。"
}

function Ensure-LogPaths {
    param([hashtable]$Cfg)
    Log "===== 阶段 3.5：预建单文件 bind mount 日志空文件（环境：$Env）====="
    # 单文件 bind mount 要求宿主侧对应文件预先存在，否则 Docker 会建成同名目录导致挂载失败。
    # 这些文件已被 .gitignore 屏蔽（logs/** / *.log），不进 git。
    $files = @(
        'logs/mysql/error.log',
        'logs/redis/redis.log',
        'logs/nginx/access.log',
        'logs/nginx/error.log'
    )
    switch ($Env) {
        'dev'     { $files += 'logs/prj-frontend/dev.log' }
        'prod'    { $files += @('logs/prj-frontend/access.log', 'logs/prj-frontend/error.log') }
        'staging' { $files += @('logs/prj-frontend/access.log', 'logs/prj-frontend/error.log') }
    }
    foreach ($f in $files) {
        $dir = Split-Path $f -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        if (-not (Test-Path $f)) {
            New-Item -ItemType File -Path $f -Force | Out-Null
            Log "已创建日志空文件：$f"
        }
    }
    Log "日志路径预建完成（dev/prod 对齐：均确保单文件挂载落盘点存在）。"
}

function Start-Stack {
    param([hashtable]$Cfg)
    Log "===== 阶段 4：启动 $Env 栈 ====="
    $argsList = @()
    foreach ($f in $Cfg.ComposeFiles) { $argsList += '-f'; $argsList += $f }
    $argsList += '--env-file'; $argsList += $Cfg.EnvFile
    # docker compose 的构建/启动进度与变量警告均走 stderr；在严格错误偏好下 PowerShell 7
    # 会将其包装为 NativeCommandError 终止异常，误杀已成功的部署。此处临时放宽错误偏好并合并
    # stderr 到成功流，确保任何原生输出都不会中止部署；仅以 LASTEXITCODE 判定真实失败。
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & docker compose @argsList up -d --build 2>&1
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    if ($LASTEXITCODE -ne 0) {
        Log "错误：$Env 栈启动失败，请查看上方日志（docker compose logs）。" Red
        exit 1
    }
    Log "$Env 栈已启动。"
}

function Wait-Service {
    param([hashtable]$Cfg, [string]$Svc)
    $argsList = @()
    foreach ($f in $Cfg.ComposeFiles) { $argsList += '-f'; $argsList += $f }
    $argsList += '--env-file'; $argsList += $Cfg.EnvFile
    $exists = & docker compose @argsList ps --format '{{.Service}}' 2>$null | Where-Object { $_ -eq $Svc }
    if (-not $exists) { return $true }
    for ($i = 1; $i -le 60; $i++) {
        $line = & docker compose @argsList ps --format '{{.Service}}|{{.State}}|{{.Health}}' 2>$null | Where-Object { $_ -match "^$Svc[|]" }
        if ($line) {
            $parts = $line -split '[|]'
            $state = $parts[1]
            $health = $parts[2]
            if ($state -eq 'running' -and ($health -eq '' -or $health -eq 'healthy')) {
                return $true
            }
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Test-Url {
    param([string]$Url)
    for ($i = 1; $i -le 30; $i++) {
        try {
            $null = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
            return $true
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-And-Probe {
    param([hashtable]$Cfg)
    Log "===== 阶段 5：等待服务就绪并探测入口（环境：$Env）====="
    foreach ($svc in $Cfg.WaitServices) {
        if (Wait-Service -Cfg $Cfg -Svc $svc) {
            Log "$svc 就绪。"
        } else {
            if ($Cfg.CriticalServices -contains $svc) {
                Log "错误：关键服务 $svc 在 120 秒内未达就绪状态，部署未成功。请排查：docker compose -f $($Cfg.ComposeFiles -join ' -f ') logs $svc" Red
                exit 1
            } else {
                Log "警告：$svc 在 120 秒内未达就绪状态，请排查：docker compose -f $($Cfg.ComposeFiles -join ' -f ') logs $svc"
            }
        }
    }

    if (Test-Url -Url 'http://127.0.0.1/') {
        Log "前端首页可访问：http://127.0.0.1/"
    } else {
        Log "警告：前端首页 http://127.0.0.1/ 暂不可达（后端可能仍在预热）。"
    }
    if (Test-Url -Url 'http://127.0.0.1/captchaImage') {
        Log "后端验证码可访问：http://127.0.0.1/captchaImage"
    } else {
        Log "警告：后端验证码 http://127.0.0.1/captchaImage 暂不可达。"
    }

    Print-Summary -Success $true
}

function Print-Summary {
    param([bool]$Success)
    $elapsed = [math]::Round(((Get-Date) - $StartTime).TotalSeconds)
    Log "===== 部署汇总 ====="
    if ($Success) {
        Log "[成功] 部署完成（目标环境：$Env）。"
    } else {
        Log "[失败] 部署未成功（目标环境：$Env）。" Red
    }
    Log "总耗时：$elapsed 秒"
    Log "访问地址清单："
    Log "  前端：http://localhost/"
    Log "  后端验证码：http://localhost/captchaImage"
    Log "  班级网站：http://localhost/607/ 、 http://localhost/902/"
    if ($Env -eq 'dev') {
        Log "  后端直连：http://localhost:8080/（dev 环境暴露）"
    } else {
        Log "  后端直连：未暴露（prod/staging 不暴露 8080）"
    }
    if ($script:OllamaStatus -eq 'skipped') {
        Log "Ollama：已跳过（-SkipOllama），向量化功能不可用。" Yellow
    } elseif ($script:OllamaStatus -eq 'failed') {
        Log "Ollama：准备失败，向量化功能不可用；其余服务已正常启动。" Yellow
    }
}

# ---------- [新增] -ResetMysql 相关函数 ----------
# 判定是否为交互终端（管道 / CI 环境视为非交互）
function Test-IsInteractive {
    # 显式标记 PS1_INTERACTIVE 时强制视为交互
    if (-not [string]::IsNullOrEmpty($env:PS1_INTERACTIVE)) { return $true }
    try {
        if ([Console]::IsInputRedirected -or [Console]::IsOutputRedirected) { return $false }
        # 常见 CI 环境变量
        if ($env:CI -eq 'true' -or $env:CI -eq '1' -or $env:TF_BUILD -or $env:GITHUB_ACTIONS) { return $false }
        # 管道 / 非交互宿主下 $Host.UI.RawUI 通常不可用
        $null = $Host.UI.RawUI
        return $true
    } catch {
        return $false
    }
}

# 二次确认（安全核心）：交互终端要求 yes/y；非交互环境（CI/管道）拒绝除非 -Force
function Confirm-ResetMysql {
    if (Test-IsInteractive) {
        Log "请在下方输入 yes 或 y 以确认删除 MySQL 数据卷；输入其它任何内容将中止：" Yellow
        $ans = Read-Host "确认删除 MySQL 数据卷?"
        $a = $ans.Trim().ToLower()
        if ($a -ne 'yes' -and $a -ne 'y') {
            Log "已取消操作（未输入 yes/y），未删除任何数据，部署中止。" Yellow
            exit 1
        }
    } else {
        if ($Force) {
            Log "非交互环境，但已显式传入 -Force，继续执行 MySQL 数据卷删除。" Yellow
        } else {
            Log "非交互环境禁止自动删除 MySQL 数据卷，请加 -Force 确认或手动执行。" Red
            exit 1
        }
    }
}

# 执行清理：停栈（不删其它卷）→ 精准删除 mysql 卷；失败仅告警不崩后续（自带 try/catch）
function Reset-MysqlVolume {
    if ($DryRun) {
        Log "[DryRun] 将执行：停栈（docker compose down）+ 删除 MySQL 数据卷（docker volume rm）；实际部署时才执行。"
        return
    }
    Log "===== 清理 MySQL 数据卷（停栈 + 精准删除 mysql 卷）====="
    $downArgs = @()
    foreach ($f in $cfg.ComposeFiles) { $downArgs += '-f'; $downArgs += $f }
    try {
        # 先停整个栈（不删其它卷），否则卷 in use 无法删除
        Log "正在停止整个栈（docker compose down，不删除其它卷）..."
        & docker compose @downArgs down 2>&1
        if ($LASTEXITCODE -ne 0) {
            Log "警告：停止栈返回非零退出码 $LASTEXITCODE，尝试继续删除卷（若卷仍被占用，下方删除可能失败）。" Yellow
        }

        # 精准定位 mysql 数据卷实际名称（仅用 -f 文件，不依赖 env 文件）
        $volKey = & docker compose @downArgs config --volumes 2>$null | Where-Object { $_ -match 'mysql' } | Select-Object -First 1
        if (-not $volKey) { $volKey = 'mysql_data' }
        $actual = docker volume ls --format '{{.Name}}' --filter "name=$volKey" 2>$null | Select-Object -First 1
        if (-not $actual) {
            Log "未找到 MySQL 数据卷（卷名推测为 $volKey），可能尚未创建，跳过删除。"
            Log "即将继续正常部署（MySQL 将全新初始化或沿用现有卷）。"
            return
        }
        Log "精准删除 MySQL 数据卷：$actual ..."
        & docker volume rm $actual 2>&1
        if ($LASTEXITCODE -eq 0) {
            Log "已删除 MySQL 数据卷，即将重新初始化空库。"
        } else {
            Log "错误：删除 MySQL 数据卷 $actual 失败（可能仍被容器占用）。请手动执行 'docker compose @downArgs down' 后重试；本脚本将继续常规部署流程。" Red
        }
    } catch {
        Log "错误：清理 MySQL 数据卷过程中出现异常：$($_.Exception.Message)。请手动清理后重试；本脚本将继续常规部署流程。" Red
    }
}

# ---------- 主流程 ----------
$StartTime = Get-Date
$cfg = $Configs[$Env]

try {
    # staging 前置配置闸门：缺少 compose / example 必须明确失败并给出指引
    if ($Env -eq 'staging') {
        if (-not (Test-Path 'docker-compose.staging.yml') -or -not (Test-Path '.env.staging.example')) {
            Log "错误：staging 环境尚未配置：请在仓库提供 docker-compose.staging.yml 与 .env.staging.example（可复制 prod 模板后改端口/镜像），再运行本命令。" Red
            throw "staging 环境尚未配置"
        }
    }

    # [新增] -ResetMysql：最先打印醒目警告（在任何前置操作之前）
    if ($ResetMysql) {
        Log "============================================================" Red
        Log "!!! 警告 !!! 即将删除 MySQL 数据卷，卷内所有数据将永久丢失且不可恢复！" Red
        Log "  操作对象：MySQL 数据卷（如 x-box_mysql_data）" Red
        Log "  卷内数据：留言、用户、账号等全部数据将被清空，且不可恢复！" Red
        Log "  仅当你确实需要「重置/重建空库」时才继续。" Red
        Log "============================================================" Red
    }

    if (-not $DryRun) { Test-Prereqs }

    # [新增] -ResetMysql：二次确认 + 停栈 + 精准删卷（确认被拒则 exit 1；删除失败仅告警不崩后续）
    if ($ResetMysql) {
        Confirm-ResetMysql
        Reset-MysqlVolume
    }

    Prepare-EnvFiles -Cfg $cfg
    Ensure-SslCert
    Test-Contract

    if ($DryRun) {
        Log "Dry run 通过，已准备的 env 文件（环境 $Env）："
        foreach ($f in $cfg.EnvFiles) {
            if (Test-Path $f) { Log "  - $f" }
        }
        Log "（未调用 Ollama 准备，未启动 compose）"
        exit 0
    }

    $script:OllamaStatus = 'unknown'
    Start-Ollama
    Ensure-LogPaths -Cfg $cfg
    Start-Stack -Cfg $cfg
    Wait-And-Probe -Cfg $cfg
}
catch {
    $elapsed = [math]::Round(((Get-Date) - $StartTime).TotalSeconds)
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [deploy] 致命错误：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [deploy] 部署失败，已中止。（耗时 ${elapsed}s）" -ForegroundColor Red
    exit 1
}
