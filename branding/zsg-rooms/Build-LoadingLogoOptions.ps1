param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'font-options'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$glyphs = @{
    Z = @('11111','00001','00010','00100','01000','10000','11111')
    S = @('01111','10000','10000','01110','00001','00001','11110')
    G = @('01111','10000','10000','10111','10001','10001','01111')
    R = @('11110','10001','10001','11110','10100','10010','10001')
    O = @('01110','10001','10001','10001','10001','10001','01110')
    M = @('10001','11011','10101','10101','10001','10001','10001')
}
$silver = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(235, 240, 237))
$gold = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(241, 199, 107))
$shadow = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(230, 22, 28, 30))
$styles = @(
    @{ Id = 'A'; Label = 'Clean Pixel'; Font = $null },
    @{ Id = 'B'; Label = 'Modern'; Font = 'Segoe UI' },
    @{ Id = 'C'; Label = 'Condensed'; Font = 'Impact' },
    @{ Id = 'D'; Label = 'Classic Bold'; Font = 'Arial Black' }
)
function Draw-PixelWord($Graphics, [string]$Word, [int]$Y, [int]$Scale, $Brush, [int]$Expand = 0) {
    $width = ($Word.Length * 6 - 1) * $Scale
    $left = [int]((512 - $width) / 2)
    for ($letter = 0; $letter -lt $Word.Length; $letter++) {
        $rows = $glyphs[[string]$Word[$letter]]
        for ($row = 0; $row -lt 7; $row++) {
            for ($column = 0; $column -lt 5; $column++) {
                if ($rows[$row][$column] -eq '1') {
                    $Graphics.FillRectangle($Brush, $left + ($letter * 6 + $column) * $Scale - $Expand, $Y + $row * $Scale - $Expand, $Scale + 2 * $Expand, $Scale + 2 * $Expand)
                }
            }
        }
    }
}
function Draw-FontWord($Graphics, [string]$Word, [string]$Family, [float]$Y, [float]$TargetWidth, [float]$TargetHeight, $Brush) {
    $fontFamily = [Drawing.FontFamily]::new($Family)
    $style = if ($fontFamily.IsStyleAvailable([Drawing.FontStyle]::Bold)) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular }
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    $format = [Drawing.StringFormat]::GenericTypographic
    $path.AddString($Word, $fontFamily, [int]$style, 160, [Drawing.PointF]::new(0,0), $format)
    $bounds = $path.GetBounds()
    $scale = [Math]::Min($TargetWidth / $bounds.Width, $TargetHeight / $bounds.Height)
    $matrix = [Drawing.Drawing2D.Matrix]::new($scale,0,0,$scale, (512 - $bounds.Width * $scale) / 2 - $bounds.X * $scale, $Y - $bounds.Y * $scale)
    $path.Transform($matrix)
    $outline = [Drawing.Pen]::new([Drawing.Color]::FromArgb(22,28,30), 6)
    $outline.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $Graphics.DrawPath($outline, $path)
    $Graphics.FillPath($Brush, $path)
    $outline.Dispose(); $matrix.Dispose(); $path.Dispose(); $fontFamily.Dispose(); $format.Dispose()
}

$sheet = [Drawing.Bitmap]::new(1120, 800)
$canvas = [Drawing.Graphics]::FromImage($sheet)
$canvas.Clear([Drawing.Color]::FromArgb(30,34,36))
$canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$labelFont = [Drawing.Font]::new('Segoe UI',18,[Drawing.FontStyle]::Regular)
try {
    for ($i=0; $i -lt $styles.Count; $i++) {
        $style = $styles[$i]
        $bitmap = [Drawing.Bitmap]::new(512,512)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $graphics.Clear([Drawing.Color]::Transparent)
        if ($null -eq $style.Font) {
            Draw-PixelWord $graphics 'ZSG' 110 24 $shadow 3
            Draw-PixelWord $graphics 'ROOMS' 316 14 $shadow 3
            Draw-PixelWord $graphics 'ZSG' 110 24 $silver
            Draw-PixelWord $graphics 'ROOMS' 316 14 $gold
        } else {
            $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
            Draw-FontWord $graphics 'ZSG' $style.Font 110 424 170 $silver
            Draw-FontWord $graphics 'ROOMS' $style.Font 316 424 100 $gold
        }
        $bitmap.Save((Join-Path $OutputDirectory ($style.Id + '-logo.png')), [Drawing.Imaging.ImageFormat]::Png)
        $left = ($i % 2) * 560
        $top = [int][Math]::Floor($i / 2) * 400
        $canvas.DrawString(($style.Id + '  ' + $style.Label), $labelFont, $silver, $left + 32, $top + 18)
        $canvas.DrawImage($bitmap, $left + 28, $top + 48, 300, 300)
        $canvas.FillRectangle([Drawing.Brushes]::LightGray, $left + 354, $top + 124, 164, 164)
        if ($i -eq 0) { $canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor }
        $canvas.DrawImage($bitmap, $left + 354, $top + 124, 164, 164)
        $canvas.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.Dispose(); $bitmap.Dispose()
    }
    $sheet.Save((Join-Path $OutputDirectory 'comparison.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $labelFont.Dispose(); $canvas.Dispose(); $sheet.Dispose(); $silver.Dispose(); $gold.Dispose(); $shadow.Dispose()
}
Write-Output (Join-Path $OutputDirectory 'comparison.png')
