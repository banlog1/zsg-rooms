param(
    [Parameter(Mandatory = $true)]
    [string[]]$Report
)

$ErrorActionPreference = 'Stop'
foreach ($path in $Report) {
    $value = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($null -eq $value.attempts -or -not $value.type) {
        throw 'Expected a public bank report, not a private seed handoff.'
    }
    $attempts = @($value.attempts)
    $passes = @($attempts | Where-Object { $_.status -eq 'VALIDATION_PASS' })
    $seconds = [double]$value.elapsedMs / 1000
    $first = if ($passes.Count) { [math]::Round([double]$passes[0].foundAtMs / 1000, 2) } else { 'not observed' }
    $perSeed = if ($passes.Count) { [math]::Round($seconds / $passes.Count, 2) } else { 'not measurable' }
    [pscustomobject]@{
        Profile = $value.profile
        PipelineRevision = $value.pipelineRevision
        RunId = $value.runId
        Type = $value.type
        SearchMode = $value.searchMode
        SisterLimit = $value.sisterLimit
        Lower48Checked = $value.lower48Checked
        SistersChecked = $value.sistersChecked
        FamilyLootRejected = $value.familyLootRejected
        PredictBastionLayout = $value.predictBastionLayout
        FamilyLayoutsChecked = $value.familyLayoutsChecked
        FamilyLayoutsRejected = $value.familyLayoutsRejected
        FamilyLayoutMs = $value.familyLayoutMs
        LayoutPredictionsVerified = $value.layoutPredictionsVerified
        EarlyChecks = $value.earlyChecks
        VillageLayoutsChecked = $value.villageLayoutsChecked
        VillageLayoutsRejected = $value.villageLayoutsRejected
        VillageLayoutMs = $value.villageLayoutMs
        VillagePredictionsVerified = @($attempts | Where-Object { $_.villagePredictionVerified }).Count
        Status = $value.status
        SpawnSearchHint = $value.spawnSearchHint
        TempleWoodSearchHint = $value.templeWoodSearchHint
        Trials = $attempts.Count
        GeneratedWorlds = @($attempts | Where-Object { $_.worldGenerated }).Count
        Workers = $value.workers
        WorkerStartupMs = $value.workerStartupMs
        QueueCapacity = $value.queueCapacity
        InFlightHighWater = $value.inFlightHighWater
        StagedPasses = $passes.Count
        Seconds = [math]::Round($seconds, 2)
        FirstStagedPassSeconds = $first
        ObservedSecondsPerStagedPass = $perSeed
        AcceptancePercent = if ($attempts.Count) { [math]::Round(100 * $passes.Count / $attempts.Count, 3) } else { 'not measurable' }
        Note = 'Excludes coordinator bootstrap and independent verification; includes worker startup and backlog drain. Check sums are CPU-wall service time, not parallel run elapsed time.'
    } | Format-List
    $attempts.rejections | Group-Object | Sort-Object Count -Descending | Select-Object Name,Count | Format-Table -AutoSize
    $checks = @{}
    foreach ($attempt in $attempts) {
        if (-not $attempt.checks) { continue }
        foreach ($property in $attempt.checks.PSObject.Properties) {
            if (-not $checks.ContainsKey($property.Name)) {
                $checks[$property.Name] = [pscustomobject]@{ Check=$property.Name; Candidates=0; Calls=0L; FalseResults=0L; Errors=0L; InclusiveMs=0.0; ExclusiveMs=0.0; MaxCandidateMs=0.0 }
            }
            $row = $checks[$property.Name]
            $item = $property.Value
            $row.Candidates++
            $row.Calls += $item.calls
            $row.FalseResults += $item.falseResults
            $row.Errors += $item.errors
            $row.InclusiveMs += $item.inclusiveMs
            $row.ExclusiveMs += $item.exclusiveMs
            $row.MaxCandidateMs = [math]::Max($row.MaxCandidateMs, $item.inclusiveMs)
        }
    }
    $checks.Values | Sort-Object ExclusiveMs -Descending | Select-Object Check,Candidates,Calls,FalseResults,Errors,
        @{n='InclusiveMs';e={[math]::Round($_.InclusiveMs,2)}}, @{n='ExclusiveMs';e={[math]::Round($_.ExclusiveMs,2)}},
        @{n='MaxCandidateMs';e={[math]::Round($_.MaxCandidateMs,2)}} | Format-Table -AutoSize
    'Checks absent from the table were not reached; they have not been measured as cheap.'
}
