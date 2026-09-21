function Get-FilterTextHash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))).Replace('-','') }
    finally { $sha.Dispose() }
}

function Get-OvernightPlanHash($Plan) {
    return Get-FilterTextHash ($Plan | ConvertTo-Json -Depth 12 -Compress)
}

function Read-OvernightCheckpoint([string]$Directory, $Plan) {
    $path = Join-Path $Directory 'checkpoint.json'
    if (-not [IO.File]::Exists($path)) { return $null }
    try {
        $envelope = Read-FilterJson $path
        if ($envelope.version -ne 1 -or (Get-FilterTextHash $envelope.payload) -cne $envelope.hash) { throw 'Checkpoint checksum mismatch.' }
        $data = $envelope.payload | ConvertFrom-Json
        if ($data.planHash -cne (Get-OvernightPlanHash $Plan) -or
            $data.bankHash -cne (Get-FileHash -LiteralPath (Join-Path $Directory 'bank.jsonl')).Hash) { throw 'Checkpoint does not match this bank.' }
        $entries = @{}
        $rows = @{}
        $progress = [Diagnostics.Stopwatch]::StartNew()
        $reader = [IO.File]::OpenText((Join-Path $Directory 'bank.jsonl'))
        try {
            foreach ($entry in $data.entries) {
                $job = $entry.job
                if ($job.state -cne 'complete' -or $job.id -notmatch '^\d{8}$' -or $entries.ContainsKey($job.id) -or
                    $job.accepted -lt 0 -or $entry.length -le 0 -or $entry.ticks -le 0) { throw 'Invalid checkpoint entry.' }
                $entries[$job.id] = $entry
                $batch = [Collections.Generic.List[object]]::new()
                for ($i=0; $i -lt $job.accepted; $i++) {
                    $line = $reader.ReadLine()
                    if ($null -eq $line) { throw 'Checkpoint bank is truncated.' }
                    $row = $line | ConvertFrom-Json
                    $batch.Add([pscustomobject]@{seed=[string]$row.seed;line=$line})
                }
                $rows[$job.id] = $batch
                if ($progress.Elapsed.TotalSeconds -ge 5) {
                    Write-Host "Loading validated checkpoint: $($entries.Count) completed batches..."
                    $progress.Restart()
                }
            }
            if ($null -ne $reader.ReadLine()) { throw 'Checkpoint bank has extra records.' }
        } finally { $reader.Dispose() }
        return [pscustomobject]@{entries=$entries;rows=$rows;reusable=@{}}
    } catch {
        Write-Host 'Checkpoint unavailable or mismatched; verifying the full journal instead.'
        return $null
    }
}

function Write-OvernightCheckpoint([string]$Directory, $Plan, $Entries) {
    $data = [ordered]@{planHash=(Get-OvernightPlanHash $Plan);
        bankHash=(Get-FileHash -LiteralPath (Join-Path $Directory 'bank.jsonl')).Hash;entries=@($Entries)}
    $payload = $data | ConvertTo-Json -Depth 12 -Compress
    # A crash between replacing the bank and checkpoint takes full recovery,
    # never a partially published cache.
    Write-FilterAtomicJson (Join-Path $Directory 'checkpoint.json') @{version=1;hash=(Get-FilterTextHash $payload);payload=$payload}
}
