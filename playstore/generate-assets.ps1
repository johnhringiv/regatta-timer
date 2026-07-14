# Regenerates all derived marketing assets from committed sources
# (docs/logo.svg + docs/screenshots/countdown.png):
#   docs/og-image.png              1200x630  (committed — the live site serves it)
#   playstore/feature-graphic.png  1024x500  (gitignored — regenerate for Play upload)
#   playstore/icon-512.png         512x512   (gitignored — regenerate for Play upload)
# The two banner sizes are hard external requirements (OG standard vs Play's exact spec).
# Requires Microsoft Edge (SVG rasterization) — no other dependencies.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repo = Split-Path $PSScriptRoot -Parent

# 1. Rasterize the SVG logo to a transparent PNG via headless Edge
$tmp = Join-Path $env:TEMP "regatta-banner"
New-Item -ItemType Directory -Force $tmp | Out-Null
$logoSvg = (Join-Path $repo 'docs\logo.svg') -replace '\\','/'
@"
<!doctype html><html><head><style>
html,body{margin:0;padding:0;width:400px;height:400px;background:transparent;overflow:hidden}
div{width:400px;height:400px;display:flex;align-items:center;justify-content:center}
img{width:400px}
</style></head><body><div><img src="file:///$logoSvg"></div></body></html>
"@ | Out-File -Encoding utf8 (Join-Path $tmp 'logo.html')
$edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
if (-not (Test-Path $edge)) { $edge = 'C:\Program Files\Microsoft\Edge\Application\msedge.exe' }
$logoPng = Join-Path $tmp 'logo-400.png'
& $edge --headless=new --disable-gpu --screenshot="$logoPng" --window-size=400,400 `
    --default-background-color=00000000 "file:///$($tmp -replace '\\','/')/logo.html" 2>$null
Start-Sleep 2

# 2. Shared banner painter
function New-Banner($W, $H, $outPath, $titleSize, $tagSize, $subSize, $titleY, $tagY, $subY,
                    $logoSize, $logoX, $logoY, $shotSize, $shotX, $shotY) {
    $bmp = New-Object System.Drawing.Bitmap($W,$H)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle(0,0,$W,$H)),
        [System.Drawing.Color]::FromArgb(11,107,58), [System.Drawing.Color]::FromArgb(6,73,38), 90)
    $g.FillRectangle($grad, 0, 0, $W, $H)
    $gold = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(245,197,24))
    $white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(250,250,245))
    $g.DrawString('Regatta Timer', (New-Object System.Drawing.Font('Segoe UI', $titleSize, [System.Drawing.FontStyle]::Bold)), $white, 55, $titleY)
    $g.DrawString('Race start timer for Wear OS', (New-Object System.Drawing.Font('Segoe UI', $tagSize)), $gold, 62, $tagY)
    $g.DrawString("5 / 3 minute sequences - sync to the gun`nworks wet - no phone required", (New-Object System.Drawing.Font('Segoe UI', $subSize)), $white, 62, $subY)
    $logo = [System.Drawing.Image]::FromFile($logoPng)
    $g.DrawImage($logo, $logoX, $logoY, $logoSize, $logoSize)
    $logo.Dispose()
    $shot = [System.Drawing.Image]::FromFile((Join-Path $repo 'docs\screenshots\countdown.png'))
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($shotX, $shotY, $shotSize, $shotSize)
    $g.SetClip($path); $g.DrawImage($shot, $shotX, $shotY, $shotSize, $shotSize); $g.ResetClip()
    $g.DrawEllipse((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245,197,24), 9)), $shotX, $shotY, $shotSize, $shotSize)
    $shot.Dispose()
    $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Output "wrote $outPath"
}

# 3. Both banners from the shared layout
New-Banner 1200 630 (Join-Path $repo 'docs\og-image.png')            68 30 24 130 265 330 190 48 415 430 700 100
New-Banner 1024 500 (Join-Path $repo 'playstore\feature-graphic.png') 56 26 20  96 205 262 150 44 330 380 590  60

# 4. Play icon: logo on the launcher background color, 512x512
@"
<!doctype html><html><head><style>
html,body{margin:0;padding:0;width:512px;height:512px;background:#FAFAF5;overflow:hidden}
div{width:512px;height:512px;display:flex;align-items:center;justify-content:center}
img{width:420px;height:420px}
</style></head><body><div><img src="file:///$logoSvg"></div></body></html>
"@ | Out-File -Encoding utf8 (Join-Path $tmp 'icon.html')
$iconOut = Join-Path $repo 'playstore\icon-512.png'
& $edge --headless=new --disable-gpu --screenshot="$iconOut" --window-size=512,512 `
    --default-background-color=FFFAFAF5 "file:///$($tmp -replace '\\','/')/icon.html" 2>$null
Start-Sleep 2
Write-Output "wrote $iconOut"
