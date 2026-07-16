# setup-host-ollama.ps1 — Windows dev host native Ollama + bge-m3 (X-box)
#
# Purpose: install and init Ollama on a Windows 11 + WSL2 + Docker Desktop dev machine,
#          bind OLLAMA_HOST=0.0.0.0:11434, and pull the bge-m3 model.
#          The backend container (prj-backend-c) reaches host Ollama via host.docker.internal:11434.
#
# Prereqs: Windows 10/11; Docker Desktop with WSL2 backend.
#          Ollama is downloaded from a GitHub mirror (ghproxy) inside the script;
#          winget/choco are only used as last-resort fallbacks.
# Usage (PowerShell 7 OR Windows PowerShell 5.1 — script uses no PS7-only syntax):
#       NOTE: MUST be run as Administrator. The script disables the auto-starting Ollama SERVICE
#             (it ignores OLLAMA_HOST and binds 127.0.0.1 with a restart-on-failure policy) and
#             runs its own "ollama serve --host 0.0.0.0:11434" so containers can reach it.
#       powershell -ExecutionPolicy Bypass -File scripts/setup-host-ollama.ps1
#       powershell -ExecutionPolicy Bypass -File scripts/setup-host-ollama.ps1 -SkipInstall   # Ollama already installed
#       powershell -ExecutionPolicy Bypass -File scripts/setup-host-ollama.ps1 -PullOnly       # only pull model (Ollama running)
#       powershell -ExecutionPolicy Bypass -File scripts/setup-host-ollama.ps1 -InstallerUrl "https://your-mirror/OllamaSetup.exe"
#       powershell -ExecutionPolicy Bypass -File scripts/setup-host-ollama.ps1 -Proxy http://127.0.0.1:7890   # downloads + pull go through proxy
#       (or set env HTTPS_PROXY=http://127.0.0.1:7890 before running; or drop OllamaSetup.exe beside this script for offline)
#
# Notes:
#   - OLLAMA_HOST must be 0.0.0.0 (not 127.0.0.1) or containers cannot reach it via the gateway IP.
#   - host.docker.internal is resolved natively by Docker Desktop; no extra_hosts needed.
#   - FASTEST CHANNEL: the installer is pulled with aria2c multi-thread (-x16 -s16) whenever possible.
#     If aria2c is not on PATH, the script bootstraps it via winget/choco (a ~2.5MB download that is
#     quick even on throttled networks), then uses 16 concurrent connections to aggregate bandwidth and
#     break per-connection throttling. If aria2 is unavailable, it falls back to curl.exe single-thread
#     from ollama.com (Cloudflare CDN) first, then GitHub mirrors. curl.exe is far faster and more
#     reliable than PowerShell's Invoke-WebRequest for large files.
#   - OFFLINE: if OllamaSetup.exe is present next to this script, in .\bin\, or in %TEMP%, the script
#     uses it directly and skips ALL downloads. Download it on any reachable network, copy it here, rerun.
#   - PROXY (OPTIONAL): pass -Proxy http://127.0.0.1:7890 (or set HTTPS_PROXY/HTTP_PROXY) only if a
#     specific CDN is throttled on your network. The default path uses curl.exe and needs no proxy
#     on a normal connection. Proxy, when set, also applies to "ollama pull".
#   - Override the download source with -InstallerUrl if a mirror is blocked/unavailable.
#   - All user-facing string literals are kept ASCII on purpose: PowerShell 5.1 reads .ps1 files
#     using the system ANSI codepage (GBK on zh-CN) by default, so non-ASCII literals would
#     mojibake and break string parsing. Keep log/error messages ASCII.

[CmdletBinding()]
param(
    [switch]$PullOnly,
    [switch]$SkipInstall,
    [string]$InstallerUrl = 'https://ghproxy.net/https://github.com/ollama/ollama/releases/download/v0.32.0/OllamaSetup.exe',
    [string]$Proxy = ''
)

$ErrorActionPreference = 'Stop'
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

function Log($msg, $ForegroundColor) {
    if ($ForegroundColor) {
        Write-Host "[setup-host-ollama] $msg" -ForegroundColor $ForegroundColor
    } else {
        Write-Host "[setup-host-ollama] $msg"
    }
}

# ---------- 1. Install Ollama (download from fast source, silent install) ----------
function Get-Aria2Exe {
    # Prefer aria2c on PATH. If missing, bootstrap it via winget/choco (NOT a GitHub zip download):
    # winget's package source is reachable here, and the aria2 binary is only ~2.5MB, so installing
    # it is quick even on a throttled network. A 16-connection download is the fastest channel when
    # the CDN throttles per connection, so it is worth a one-time install.
    $aria2 = Get-Command aria2c -ErrorAction SilentlyContinue
    if ($aria2) { return $aria2.Source }
    $installed = $false
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        try {
            Log "aria2c not found; installing via winget (one-time, ~2.5MB) ..."
            winget install --exact --id aria2.aria2 -e --accept-package-agreements --accept-source-agreements
            $installed = $true
        } catch {
            Log "winget install aria2 failed: $_" -ForegroundColor Yellow
            try { winget install aria2 --accept-package-agreements --accept-source-agreements } catch { }
        }
    }
    if (-not $installed -and (Get-Command choco -ErrorAction SilentlyContinue)) {
        try {
            Log "aria2c not found; installing via choco ..."
            choco install aria2 -y
            $installed = $true
        } catch {
            Log "choco install aria2 failed: $_" -ForegroundColor Yellow
        }
    }
    if ($installed) {
        $env:Path = [System.Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [System.Environment]::GetEnvironmentVariable('Path', 'User')
        $aria2 = Get-Command aria2c -ErrorAction SilentlyContinue
        if ($aria2) { return $aria2.Source }
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
                $ariaArgs = @('-x','16','-s','16','-k','1M','--connect-timeout=20','-o',(Split-Path $OutFile -Leaf),$url,'--dir',(Split-Path $OutFile -Parent))
                if ($Proxy) { $ariaArgs += '--all-proxy'; $ariaArgs += $Proxy }
                & $aria2 @ariaArgs
                if ($LASTEXITCODE -ne 0) { throw "aria2 exit code $LASTEXITCODE" }
            } else {
                Log "Downloading installer via curl: $url"
                $curlArgs = @('-L', '-f', '-S', '--connect-timeout', '15', '--max-time', '600', '--retry', '2', '--speed-time', '90', '--speed-limit', '100000', '-o', $OutFile, $url)
                if ($Proxy) { $curlArgs = @('-x', $Proxy) + $curlArgs }
                & curl.exe @curlArgs
                if ($LASTEXITCODE -ne 0) { throw "curl exited $LASTEXITCODE (low speed or connection reset)" }
            }
            if (Test-Path $OutFile) {
                $sz = (Get-Item $OutFile).Length
                if ($sz -gt 10MB) { return $OutFile }
                Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
            }
        } catch {
            Log "Source failed: $url ($_)" -ForegroundColor Yellow
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
            Log "All mirrors failed; falling back to winget ..." -ForegroundColor Yellow
            winget install --exact --id Ollama.Ollama -e --accept-package-agreements --accept-source-agreements
            return
        } elseif (Get-Command choco -ErrorAction SilentlyContinue) {
            Log "All mirrors failed; falling back to choco ..." -ForegroundColor Yellow
            choco install ollama -y
            return
        } else {
            Log "ERROR: all install methods failed. Recommended: download OllamaSetup.exe via a faster channel (phone hotspot / another machine), place it next to this script, and re-run (offline mode). Or run with -Proxy. Or 'winget install aria2' then re-run for multi-thread." -ForegroundColor Red
            exit 1
        }
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
# The Windows Ollama SERVICE (and the tray app) grab port 11434 on 127.0.0.1 by default and
# the service has a "restart on failure" recovery policy, so a plain Stop-Service is NOT enough:
# it respawns and races our instance for the port. We must (a) disable the service so it cannot
# restart, (b) kill every ollama process repeatedly until none remain, (c) confirm the port is
# TRULY free, then (d) launch our own instance that binds 0.0.0.0 via --host, and (e) verify it
# is actually listening on 0.0.0.0 (retry if the stale one sneaks back).
if (-not $PullOnly) {
    function Stop-AllOllama {
        try { Stop-Service -Name 'ollama' -Force -ErrorAction SilentlyContinue } catch { }
        $procs = Get-Process -Name 'ollama' -ErrorAction SilentlyContinue
        if ($procs) { $procs | Stop-Process -Force -ErrorAction SilentlyContinue }
    }
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
    # (e) Poll until WE are listening on 0.0.0.0. Ollama may take a few seconds to bind (model
    # store scan / IPv6+IPv4 dual bind), so allow up to 40s. Only evict+retry if a STALE
    # 127.0.0.1 listener reappears (no watchdog = tray app was quit, so this should not happen).
    function Get-OllamaListeners {
        (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty LocalAddress)
    }
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
        # no listener yet -> server still starting up; keep waiting
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
    exit 1
}

# Report the ACTUAL bind address (not a hardcoded claim). localhost health check above passes
# regardless of bind, so we re-query the listener to tell the truth about 0.0.0.0 vs 127.0.0.1.
# Use array membership (-contains) so an IPv6 [::] listener does not mask a present 0.0.0.0.
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
