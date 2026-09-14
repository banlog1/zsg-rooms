param([Parameter(Mandatory = $true)][string]$ConfigPath)
$ErrorActionPreference = 'Stop'
try {
    $config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
    $deadline = [datetime]::UtcNow.AddSeconds(60)
    # Parent assigns the kill-on-close job before allowing any Java/native child to start.
    while (-not (Test-Path -LiteralPath ($ConfigPath + '.ready'))) {
        if ([datetime]::UtcNow -gt $deadline) { throw 'Parent did not start this worker.' }
        Start-Sleep -Milliseconds 50
    }
    $env:ZSG_MODEL_TUNE = '0'
    $env:ZSG_MODEL_TRACE = '0'
    & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type $config.type -Families $config.families `
        -Sisters $config.sisters -FamilyCap $config.familyCap -Target 1000000 -Seconds $config.seconds `
        -Stream $config.startOffset -OutputFile $config.bank -Java $config.java -Finder $config.finder -ModelDirectory $config.modelDirectory
    exit 0
} catch {
    Write-Error 'Parallel worker failed; its private bank may be partial.'
    exit 1
}
