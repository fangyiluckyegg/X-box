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
#
# 安全红线：绝不硬编码真实密码；绝不覆盖非占位符值；不打印密码明文；不改 docker-compose/网关/源码。
# 日志红线：每条日志带 ISO 时间戳前缀 [yyyy-MM-dd HH:mm:ss] [deploy]；env 文件以 UTF-8 无 BOM + LF 写入。

[CmdletBinding()]
param(
    [ValidateSet('dev', 'prod', 'staging')]
    [string]$Env = 'prod',
    [switch]$SkipOllama,
    [switch]$DryRun,
    [string]$Proxy = ''
)

$ErrorActionPreference = 'Stop'

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
        WaitServices     = @('mysql', 'redis', 'prj-redis', 'prj-backend-c', 'prj-frontend', 'prj-php')
        CriticalServices = @('mysql', 'redis', 'prj-redis', 'prj-backend-c')
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
    Log "env 文件准备完成（仅处理环境 $Env 所需文件；占位符已替换；已有真实值已保留）。"
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
                Log "aria2c not found; installing via winget (one-time, ~2.5MB) ..."
                $p = Start-Process -FilePath 'winget' -ArgumentList @('install','--exact','--id','aria2.aria2','-e','--accept-package-agreements','--accept-source-agreements') -Wait -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\winget_aria2.out" -RedirectStandardError "$env:TEMP\winget_aria2.err"
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
                Log "aria2c not found; installing via choco ..."
                $p = Start-Process -FilePath 'choco' -ArgumentList @('install','aria2','-y') -Wait -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\choco_aria2.out" -RedirectStandardError "$env:TEMP\choco_aria2.err"
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
                    $p = Start-Process -FilePath $aria2 -ArgumentList $ariaArgs -Wait -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\ollama_dl.out" -RedirectStandardError "$env:TEMP\ollama_dl.err"
                    if ($p.ExitCode -ne 0) { throw "aria2c exit code $($p.ExitCode)" }
                } else {
                    Log "Downloading installer via curl: $url"
                    $curlArgs = @('-L', '-f', '-S', '--connect-timeout', '15', '--max-time', '90', '--retry', '2', '--speed-time', '90', '--speed-limit', '100000', '-o', $OutFile, $url)
                    if ($Proxy) { $curlArgs = @('-x', $Proxy) + $curlArgs }
                    $p = Start-Process -FilePath 'curl.exe' -ArgumentList $curlArgs -Wait -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\ollama_dl.out" -RedirectStandardError "$env:TEMP\ollama_dl.err"
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
        $dest = Install-OllamaFromMirror
        if (-not $dest) {
            if (Get-Command winget -ErrorAction SilentlyContinue) {
                Log "所有镜像下载失败；尝试用 winget 安装 Ollama ..." -ForegroundColor Yellow
                $p = Start-Process -FilePath 'winget' -ArgumentList @('install','--exact','--id','Ollama.Ollama','-e','--accept-package-agreements','--accept-source-agreements') -Wait -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\winget_ollama.out" -RedirectStandardError "$env:TEMP\winget_ollama.err"
                if ($p.ExitCode -eq 0) {
                    $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
                    return
                }
                Log "winget 安装 Ollama 失败（退出码 $($p.ExitCode)），本机网络可能访问不了境外 CDN。" -ForegroundColor Yellow
            } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
                Log "所有镜像下载失败；尝试用 choco 安装 Ollama ..." -ForegroundColor Yellow
                $p = Start-Process -FilePath 'choco' -ArgumentList @('install','ollama','-y') -Wait -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\choco_ollama.out" -RedirectStandardError "$env:TEMP\choco_ollama.err"
                if ($p.ExitCode -eq 0) {
                    $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
                    return
                }
                Log "choco 安装 Ollama 失败（退出码 $($p.ExitCode)）。" -ForegroundColor Yellow
            }
            Log "错误：Ollama 自动安装失败（本机网络无法访问 ollama.com / GitHub 等境外 CDN）。" -ForegroundColor Red
            Log "请任选其一后重跑本脚本：" -ForegroundColor Red
            Log "  1) 手动安装 Ollama（winget install Ollama.Ollama 或你本机可用渠道），再用 -SkipOllama 跳过安装：" -ForegroundColor Red
            Log "       powershell -ExecutionPolicy Bypass -File scripts/deploy.ps1 -Env <环境> -SkipOllama" -ForegroundColor Red
            Log "  2) 用代理重跑：... -Env <环境> -Proxy <http://代理:端口>" -ForegroundColor Red
            Log "  3) 把 OllamaSetup.exe 放到 scripts/ 目录旁（离线安装模式，脚本会自动识别）" -ForegroundColor Red
            throw "Ollama 自动安装失败（网络受限），请按上方提示手动处理或用 -SkipOllama / -Proxy。"
        }
        Log "Running silent install (OllamaSetup.exe /S) ..."
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

    # ---------- 2. Persist OLLAMA_HOST = 0.0.0.0:11434 ----------
    # IMPORTANT: on Windows Ollama runs as a SERVICE that reads MACHINE-scope env only.
    # A User-scope OLLAMA_HOST is ignored by the service (it keeps binding 127.0.0.1),
    # so we must write to Machine scope. Running this script as Administrator is REQUIRED.
    $envScope = 'Machine'
    $existing = [System.Environment]::GetEnvironmentVariable('OLLAMA_HOST', $envScope)
    if ($existing -ne $OLLAMA_HOST_VALUE) {
        Log "Setting system(Machine) env OLLAMA_HOST=$OLLAMA_HOST_VALUE (admin required)"
        [System.Environment]::SetEnvironmentVariable('OLLAMA_HOST', $OLLAMA_HOST_VALUE, $envScope)
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
        # (d) Launch our instance that explicitly binds 0.0.0.0 via --host.
        Log "Starting Ollama (serve --host $OLLAMA_HOST_VALUE) in background..."
        Start-Process -FilePath 'ollama' -ArgumentList 'serve', '--host', $OLLAMA_HOST_VALUE -WindowStyle Hidden
        # (e) Poll until WE are listening on 0.0.0.0.
        $bound = $false
        for ($i = 1; $i -le 40; $i++) {
            Start-Sleep -Seconds 1
            $addrs = Get-OllamaListeners
            if ($addrs -contains '0.0.0.0') { $bound = $true; break }
            if ($addrs -contains '127.0.0.1') {
                Log "Listener on 127.0.0.1 (not 0.0.0.0); evicting and retrying bind..." -ForegroundColor Yellow
                Stop-AllOllama
                Start-Sleep -Seconds 2
                Start-Process -FilePath 'ollama' -ArgumentList 'serve', '--host', $OLLAMA_HOST_VALUE -WindowStyle Hidden
            }
        }
        $finalAddrs = (Get-OllamaListeners) -join ', '
        if ($bound) {
            Log "Ollama listening on: 0.0.0.0 :11434 (all listeners: $finalAddrs)"
        } else {
            Log "WARNING: could not bind 0.0.0.0:11434; current listener(s): $finalAddrs. Containers may NOT reach Ollama." -ForegroundColor Yellow
            Log "Manual fix: Stop-Service ollama; Get-Process ollama | Stop-Process -Force; then: ollama serve --host 0.0.0.0:11434" -ForegroundColor Yellow
        }
    }

    # ---------- 4. Pull bge-m3 (skip if already present locally) ----------
    $modelPresent = $false
    try {
        $list = & ollama list 2>$null
        if ($list -match [regex]::Escape($MODEL_NAME)) { $modelPresent = $true }
    } catch { }
    if ($modelPresent) {
        Log "Model $MODEL_NAME already present locally; skipping pull."
    } else {
        if ($Proxy) {
            Log "Pulling model $MODEL_NAME via proxy $Proxy ..."
        } else {
            Log "Pulling model $MODEL_NAME (first download may be slow)..."
        }
        & ollama pull $MODEL_NAME
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
    if ($actual -contains '0.0.0.0') {
        Log "[OK] Host Ollama ready: $MODEL_NAME loaded, listening on 0.0.0.0:11434"
    } elseif ($actual -contains '::') {
        Log "[OK] Host Ollama ready: $MODEL_NAME loaded, but only on IPv6 [::] (Docker containers reach the host via IPv4 host.docker.internal and may NOT reach it)." -ForegroundColor Yellow
    } else {
        $addrStr = if ($actual) { $actual -join ', ' } else { 'none' }
        Log "[OK] Host Ollama ready: $MODEL_NAME loaded, but listening on $addrStr (containers may NOT reach it)." -ForegroundColor Yellow
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
        # 因此 Ollama 准备失败不应阻断整个部署。
        Log "警告：Ollama 准备失败，部署将继续；但「向量化 / 语义检索」类功能暂不可用。" Yellow
        Log "修复：手动安装 Ollama 后执行 'ollama serve --host 0.0.0.0:11434'；或重跑本脚本时加 -SkipOllama 跳过重复下载。" Yellow
        $script:OllamaStatus = 'failed'
    }
    Log "宿主 Ollama 准备阶段结束（状态：$($script:OllamaStatus)）。"
}

function Start-Stack {
    param([hashtable]$Cfg)
    Log "===== 阶段 4：启动 $Env 栈 ====="
    $argsList = @()
    foreach ($f in $Cfg.ComposeFiles) { $argsList += '-f'; $argsList += $f }
    $argsList += '--env-file'; $argsList += $Cfg.EnvFile
    & docker compose @argsList up -d --build
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

    if (-not $DryRun) { Test-Prereqs }
    Prepare-EnvFiles -Cfg $cfg
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
    Start-Stack -Cfg $cfg
    Wait-And-Probe -Cfg $cfg
}
catch {
    $elapsed = [math]::Round(((Get-Date) - $StartTime).TotalSeconds)
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [deploy] 致命错误：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] [deploy] 部署失败，已中止。（耗时 ${elapsed}s）" -ForegroundColor Red
    exit 1
}
