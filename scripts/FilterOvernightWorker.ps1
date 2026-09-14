param([Parameter(Mandatory=$true)][string]$ConfigPath)
$ErrorActionPreference = 'Stop'
try {
    $config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    $deadline = [datetime]::UtcNow.AddSeconds(60)
    while (-not (Test-Path -LiteralPath ($ConfigPath+'.ready'))) {
        if ([datetime]::UtcNow -ge $deadline) { throw 'Missing supervisor gate.' }
        Start-Sleep -Milliseconds 50
    }
    $null = & (Join-Path $PSScriptRoot 'Search-ParallelFilterBank.ps1') -Type $config.type -Workers 1 `
        -Families $config.families -StartOffset $config.startOffset -Seconds $config.seconds `
        -Sisters $config.sisters -FamilyCap $config.familyCap -Java $config.java `
        -Finder $config.finder -ModelDirectory $config.modelDirectory -ReserveGiB $config.reserveGiB `
        -WorkerStartupGiB $config.workerStartupGiB -OutputDirectory $config.output
    exit 0
} catch { Write-Error 'Overnight batch failed. See its seed-free status; private partial outputs remain separate.'; exit 1 }
