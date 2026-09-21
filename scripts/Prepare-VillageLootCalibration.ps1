param(
    [Parameter(Mandatory=$true)][string]$Bank,
    [Parameter(Mandatory=$true)][string]$ExperimentalModel,
    [string]$BaselineModel = 'run/model-finder',
    [Parameter(Mandatory=$true)][string]$Directory,
    [int]$Samples = 12,
    [switch]$AllSeeds,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if ($Samples -lt 1 -or $Samples -gt 68) { throw 'Use 1-68 calibration samples' }
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$rows = @(Get-Content -LiteralPath $Bank | ForEach-Object { $_ | ConvertFrom-Json })
if (@($rows | Where-Object { $_.type -ne 'village' }).Count) { throw 'Village samples only' }
New-Item -ItemType Directory -Force -Path $Directory | Out-Null
$directoryPath = (Resolve-Path $Directory).Path
$classes = Join-Path $directoryPath 'export-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$base = (Resolve-Path $BaselineModel).Path
$experiment = (Resolve-Path $ExperimentalModel).Path
& (Join-Path $JavaHome 'bin/javac.exe') --release 16 -encoding UTF-8 -cp "$base/classes;$base/lib/*" -d $classes tools/model-finder/src/main/java/zsgrooms/model/VillageCalibrationExport.java
if ($LASTEXITCODE -ne 0) { throw 'Calibration exporter compilation failed' }
$requests = for ($i = 0; $i -lt $rows.Count; $i++) {
    $row = $rows[$i]
    '{0} {1} {2} {3}' -f ($i+1), $row.seed, ([int]$row.structure[0] -shr 4), ([int]$row.structure[1] -shr 4)
}
foreach ($runtime in @(@{Name='baseline'; Path=$base}, @{Name='experimental'; Path=$experiment})) {
    $output = Join-Path $directoryPath ($runtime.Name + '.tsv')
    $requests | & (Join-Path $JavaHome 'bin/java.exe') -Xmx768m -cp "$classes;$($runtime.Path)/classes;$($runtime.Path)/lib/*" zsgrooms.model.VillageCalibrationExport | Out-File -LiteralPath $output -Encoding ascii
    if ($LASTEXITCODE -ne 0) { throw "Model export failed: $($runtime.Name)" }
}
$old = @{}
Import-Csv -LiteralPath (Join-Path $directoryPath 'baseline.tsv') -Delimiter "`t" | Where-Object kind -eq summary | ForEach-Object { $old[$_.id] = $_ }
$new = @(Import-Csv -LiteralPath (Join-Path $directoryPath 'experimental.tsv') -Delimiter "`t")
$candidates = foreach ($row in ($new | Where-Object kind -eq summary)) {
    $prior = $old[$row.id]
    if (!$prior) { throw 'Missing baseline row' }
    $source = $rows[[int]$row.id-1]
    if ([int]$row.iron -ne [int]$source.chestIron -or [int]$row.pickaxes -ne [int]$source.ironPickaxes -or [int]$row.diamonds -ne [int]$source.diamonds) { throw 'Export disagrees with benchmark acceptance' }
    $recovered = !([int]$prior.iron -ge 4 -or ([int]$prior.iron -ge 1 -and ([int]$prior.pickaxes -gt 0 -or [int]$prior.diamonds -ge 3)))
    [pscustomobject]@{id=[int]$row.id; biome=$row.biome; recovered=$recovered; family=[string]$source.family; summary=$row; baseline=$prior; source=$source}
}
$candidates | Group-Object biome,recovered | Select-Object Name,Count | Format-Table -AutoSize
$selected = [Collections.Generic.List[object]]::new()
$families = [Collections.Generic.HashSet[string]]::new()
$groups = @($candidates | Group-Object biome,recovered | Sort-Object Name)
if ($AllSeeds) {
    if ($candidates.Count -gt 68) { throw 'Whole-batch calibration is limited to 68 seeds' }
    foreach ($candidate in $candidates) { $selected.Add($candidate) }
}
while (!$AllSeeds -and $selected.Count -lt $Samples) {
    $added = $false
    foreach ($group in $groups) {
        $candidate = $group.Group | Where-Object { !$families.Contains($_.family) } | Sort-Object id | Select-Object -First 1
        if (!$candidate) { continue }
        [void]$families.Add($candidate.family)
        $selected.Add($candidate)
        $added = $true
        if ($selected.Count -eq $Samples) { break }
    }
    if (!$added) { break }
}
$samplesOut = @($selected | ForEach-Object {
    $candidate = $_
    [ordered]@{
        id=$candidate.id; seed=[string]$candidate.source.seed; biome=$candidate.biome; recovered=$candidate.recovered
        chunkX=[int]$candidate.summary.x; chunkZ=[int]$candidate.summary.z
        predictedIron=[int]$candidate.summary.iron; predictedPickaxes=[int]$candidate.summary.pickaxes; predictedDiamonds=[int]$candidate.summary.diamonds
        chests=@($new | Where-Object { [int]$_.id -eq $candidate.id -and $_.kind -eq 'chest' } | ForEach-Object {
            [ordered]@{x=[int]$_.x;y=[int]$_.y;z=[int]$_.z;template=$_.template;modeled=([int]$_.modeled -eq 1);iron=[int]$_.iron;pickaxes=[int]$_.pickaxes;diamonds=[int]$_.diamonds}
        })
    }
})
[ordered]@{purpose='OFFLINE_CALIBRATION_ONLY'; samples=$samplesOut} | ConvertTo-Json -Depth 8 | Out-File -LiteralPath (Join-Path $directoryPath 'samples.json') -Encoding utf8
$selected | Select-Object id,biome,recovered | Format-Table -AutoSize
Write-Output "Prepared $($selected.Count) samples (all seeds=$AllSeeds); no production bank changes."
