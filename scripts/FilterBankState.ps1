. (Join-Path $PSScriptRoot 'FilterBankCheckpoint.ps1')

function Write-FilterAtomicJson([string]$Path, $Value) {
    $temporary = $Path + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 12))
    $stream = [IO.File]::Open($temporary,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    if ([IO.File]::Exists($Path)) { [IO.File]::Replace($temporary,$Path,[NullString]::Value) }
    else { [IO.File]::Move($temporary,$Path) }
}

function Read-FilterJson([string]$Path) {
    try { return [IO.File]::ReadAllText($Path) | ConvertFrom-Json }
    catch { throw 'Invalid or unreadable filter state. No ranges or seeds have been silently discarded.' }
}

function Reserve-FilterRange([long]$Count, [string]$StatePath) {
    if ($Count -lt 1 -or $Count -gt 281474976710655L) { throw 'Invalid reservation size.' }
    $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $StatePath)
    $lock = [IO.File]::Open($StatePath+'.lock',[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try {
        $offset = 1000000000000L
        if ([IO.File]::Exists($StatePath)) {
            $state = Read-FilterJson $StatePath
            if ($state.version -ne 1 -or $null -eq $state.nextOffset) { throw 'Invalid range reservation state.' }
            if (-not [long]::TryParse([string]$state.nextOffset,[Globalization.NumberStyles]::None,
                    [Globalization.CultureInfo]::InvariantCulture,[ref]$offset)) { throw 'Invalid range cursor integer.' }
        }
        if ($offset -lt 0 -or $offset -gt 281474976710656L-$Count) { throw 'The lower48 range is exhausted.' }
        Write-FilterAtomicJson $StatePath @{version=1;nextOffset=($offset+$Count).ToString()}
        return $offset
    } finally { $lock.Dispose() }
}

function Get-FilterModelFingerprint([string]$Root, [string]$Java, [string]$Finder, [string]$ModelDirectory) {
    $finder = if ($Finder) { $Finder } else { Join-Path $Root 'run/filter-worker/seed-finder.exe' }
    $installed = if ($ModelDirectory) { $ModelDirectory } else { Join-Path $Root 'run/model-finder' }
    foreach ($required in @($finder,(Join-Path $installed 'classes/zsgrooms/model/ModelFinder.class'),
            (Join-Path $installed 'nether/classes/zsgrooms/model/nether/NetherModel.class'))) {
        if (-not [IO.File]::Exists($required)) { throw 'Build the standalone model finder before starting an overnight run.' }
    }
    $javaPath = (Get-Command $Java -ErrorAction Stop).Source
    $entries = [Collections.Generic.List[string]]::new()
    $entries.Add('finder='+(Get-FileHash -LiteralPath $finder).Hash)
    $entries.Add('java='+(Get-FileHash -LiteralPath $javaPath).Hash)
    foreach ($file in (Get-ChildItem -LiteralPath $installed -Recurse -File | Where-Object {$_.Extension -in @('.class','.jar')} | Sort-Object FullName)) {
        $entries.Add($file.FullName.Substring($installed.Length)+'='+(Get-FileHash -LiteralPath $file.FullName).Hash)
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($entries -join "`n")))).Replace('-','') }
    finally { $sha.Dispose() }
}

function Get-OvernightJobs([string]$Directory, $Checkpoint = $null) {
    if ($Checkpoint) { $Checkpoint.reusable.Clear() }
    $progress = [Diagnostics.Stopwatch]::StartNew()
    $readCount = 0
    $assigned = [Collections.Generic.HashSet[string]]::new()
    $jobs = @(Get-ChildItem -LiteralPath (Join-Path $Directory 'jobs') -Filter '*.json' -File | Sort-Object Name | ForEach-Object {
        $cached = if ($Checkpoint) { $Checkpoint.entries[$_.BaseName] } else { $null }
        if ($cached -and $cached.length -eq $_.Length -and $cached.ticks -eq $_.LastWriteTimeUtc.Ticks) {
            $job = $cached.job
            $Checkpoint.reusable[$job.id] = $true
        } else { $job = Read-FilterJson $_.FullName }
        $readCount++
        $null = $assigned.Add($_.BaseName)
        if ($progress.Elapsed.TotalSeconds -ge 5) {
            Write-Host "Reading job journal: $readCount assignments checked..."
            $progress.Restart()
        }
        foreach ($field in @('startOffset','families','attempts','failures','accepted','acceptedFamilies')) {
            if (($job.$field -isnot [long] -and $job.$field -isnot [int]) -or $job.$field -lt 0) {
                throw 'Invalid integer in overnight job journal.'
            }
        }
        if ($job.id -cne $_.BaseName -or $job.id -notmatch '^\d{8}$' -or
            $job.state -notin @('pending','running','complete') -or $job.type -notin @('temple','shipwreck','village') -or
            $null -eq $job.startOffset -or $null -eq $job.families -or $job.families -lt 4 -or
            $job.startOffset -lt 0 -or $job.startOffset -gt 281474976710656L-$job.families -or
            $job.attempts -lt 0 -or $job.failures -lt 0 -or ($job.attempt -and $job.attempt -notmatch '^[a-f0-9]{32}$')) {
            throw 'Invalid overnight job journal.'
        }
        if ($job.state -ceq 'complete' -and (-not $job.attempt -or $job.bankHash -notmatch '^[A-Fa-f0-9]{64}$')) {
            throw 'Missing committed batch identity.'
        }
        $job
    })
    if ($Checkpoint) {
        foreach ($id in $Checkpoint.entries.Keys) {
            if (-not $assigned.Contains($id)) { throw 'Checkpoint references a missing job; committed history has not been discarded.' }
        }
    }
    $end = -1L
    foreach ($job in ($jobs | Sort-Object {[long]$_.startOffset})) {
        if ($job.startOffset -lt $end) { throw 'Overnight journal contains overlapping family ranges.' }
        $end = [long]$job.startOffset+[long]$job.families
    }
    for ($i=0;$i -lt $jobs.Count;$i++) {
        if ($jobs[$i].id -cne ('{0:D8}' -f $i)) { throw 'Overnight job journal has missing assignments.' }
    }
    return $jobs
}

function Get-OvernightResultDirectory([string]$Directory, $Job) {
    if ($Job.attempt -notmatch '^[a-f0-9]{32}$') { throw 'Missing overnight attempt identity.' }
    return Join-Path $Directory ('attempts/'+$Job.attempt+'/result')
}

function Read-OvernightBatch([string]$Directory, $Job, $Policy) {
    $result = Get-OvernightResultDirectory $Directory $Job
    $manifest = Read-FilterJson (Join-Path $result 'manifest.json')
    if ($manifest.state -cne 'complete' -or -not $manifest.fixedWorkComplete -or $manifest.workers -ne 1 -or
        $manifest.type -cne $Job.type -or $manifest.startOffset -ne $Job.startOffset -or
        $manifest.families -ne $Job.families -or $manifest.completedFamilies -ne $Job.families -or
        $manifest.sisters -ne $Policy.sisters -or $manifest.familyCap -ne $Policy.familyCap) {
        throw 'Batch did not complete its exact assigned work.'
    }
    $bank = Join-Path $result 'bank.jsonl'
    $hash = (Get-FileHash -LiteralPath $bank).Hash
    if ($Job.state -ceq 'complete' -and $Job.bankHash -cne $hash) { throw 'A committed overnight batch was modified.' }
    Add-Type -AssemblyName System.Numerics
    $modulus = [Numerics.BigInteger]::Pow(2,48)
    $inverse = [Numerics.BigInteger]::ModPow([Numerics.BigInteger]::Parse('11400714819323198485'),[Numerics.BigInteger]::Pow(2,47)-1,$modulus)
    $seen = [Collections.Generic.HashSet[string]]::new()
    $families = @{}
    $rows = [Collections.Generic.List[object]]::new()
    foreach ($line in [IO.File]::ReadLines($bank)) {
        try { $row = $line | ConvertFrom-Json; $seed = [long]::Parse([string]$row.seed) }
        catch { throw 'Invalid private seed record in overnight batch.' }
        if ($seed -eq 0 -or $row.seed -cne $seed.ToString() -or $row.profile -cne 'zsg-model-only-v5' -or $row.type -cne $Job.type -or
            $row.status -cne 'MODEL_ACCEPTED' -or -not $seen.Add($seed.ToString())) { throw 'Invalid or duplicate private seed record.' }
        $family = ($seed -band 281474976710655L).ToString()
        $ordinal = [long](([Numerics.BigInteger]::Parse($family)*$inverse)%$modulus)
        if ($ordinal -lt $Job.startOffset -or $ordinal -ge $Job.startOffset+$Job.families -or
            ($Policy.familyCap -gt 1 -and $row.family -cne $family)) { throw 'Private seed lies outside its assigned family range.' }
        $families[$family]++
        if ($families[$family] -gt $Policy.familyCap) { throw 'Private batch exceeded its family cap.' }
        $rows.Add([pscustomobject]@{seed=$seed.ToString();line=$line})
    }
    if ($rows.Count -ne $manifest.accepted -or $families.Count -ne $manifest.acceptedFamilies) { throw 'Private batch count mismatch.' }
    if ($Job.state -ceq 'complete' -and ($Job.accepted -ne $rows.Count -or $Job.acceptedFamilies -ne $families.Count)) {
        throw 'Committed journal counts do not match its private bank.'
    }
    return [pscustomobject]@{hash=$hash;rows=$rows;accepted=$rows.Count;acceptedFamilies=$families.Count}
}

function Export-OvernightBank([string]$Directory, $Plan, $Checkpoint = $null, $Jobs = $null) {
    $jobs = if ($null -ne $Jobs) { @($Jobs) } else { @(Get-OvernightJobs $Directory $Checkpoint) }
    $temporary = Join-Path $Directory ('bank.'+[guid]::NewGuid().ToString('N')+'.tmp')
    $seen = [Collections.Generic.HashSet[string]]::new()
    $counts = @{temple=0;shipwreck=0;village=0}
    $entries = [Collections.Generic.List[object]]::new()
    $next = [pscustomobject]@{entries=@{};rows=@{};reusable=@{}}
    $verified = 0
    $reused = 0
    $progress = [Diagnostics.Stopwatch]::StartNew()
    $stream = [IO.File]::Open($temporary,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
    $writer = [IO.StreamWriter]::new($stream,[Text.UTF8Encoding]::new($false))
    try {
        foreach ($job in $jobs) {
            if ($job.state -cne 'complete') { continue }
            if ($Checkpoint -and $Checkpoint.reusable[$job.id]) {
                $rows = $Checkpoint.rows[$job.id]
                $entry = $Checkpoint.entries[$job.id]
                $reused++
            } else {
                $rows = (Read-OvernightBatch $Directory $job $Plan.policies.($job.type)).rows
                $file = Get-Item -LiteralPath (Join-Path $Directory ('jobs/'+$job.id+'.json'))
                $entry = [pscustomobject]@{job=$job;length=$file.Length;ticks=$file.LastWriteTimeUtc.Ticks}
                $verified++
            }
            foreach ($row in $rows) {
                if (-not $seen.Add($row.seed)) { throw 'Duplicate seed across committed overnight batches.' }
                $writer.WriteLine($row.line)
                $counts[$job.type]++
            }
            $entries.Add($entry)
            $next.entries[$job.id]=$entry
            $next.rows[$job.id]=$rows
            if ($progress.Elapsed.TotalSeconds -ge 5) {
                Write-Host "Preparing bank: $reused checkpoint batches reused; $verified batches verified..."
                $progress.Restart()
            }
        }
        $writer.Flush(); $stream.Flush($true)
    } finally { $writer.Dispose(); $stream.Dispose() }
    $bank = Join-Path $Directory 'bank.jsonl'
    if ([IO.File]::Exists($bank)) { [IO.File]::Replace($temporary,$bank,[NullString]::Value) }
    else { [IO.File]::Move($temporary,$bank) }
    Write-OvernightCheckpoint $Directory $Plan $entries.ToArray()
    return [pscustomobject]@{accepted=$seen.Count;counts=$counts;bank=$bank;seen=$seen;checkpoint=$next;verified=$verified;reused=$reused}
}
