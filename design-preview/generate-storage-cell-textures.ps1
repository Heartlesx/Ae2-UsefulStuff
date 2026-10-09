Add-Type -AssemblyName System.Drawing

# 2048M 存储元件的图标：拿原版 64k 元件 / 64k 流体元件贴图做像素级换色（同 NAE2 法）。
# 规则：透明像素原样保留；灰件（饱和度 <= 0.15）逐像素不动；彩色像素只换色相，饱和度/明度保留。
# 金色取并行单元 2048 档的金色（#F2B33A），青色取 64 档的青色（#3FC9F0），与家族配色一致。

function Convert-HsbToColor([double]$hue, [double]$saturation, [double]$brightness) {
    if ($saturation -le 0) {
        $v = [int][Math]::Round($brightness * 255)
        return [System.Drawing.Color]::FromArgb(255, $v, $v, $v)
    }
    $h = $hue / 360.0
    $i = [Math]::Floor($h * 6.0)
    $f = $h * 6.0 - $i
    $p = $brightness * (1.0 - $saturation)
    $q = $brightness * (1.0 - $f * $saturation)
    $t = $brightness * (1.0 - (1.0 - $f) * $saturation)
    switch ([int]($i % 6)) {
        0 { $r = $brightness; $g = $t; $b = $p }
        1 { $r = $q; $g = $brightness; $b = $p }
        2 { $r = $p; $g = $brightness; $b = $t }
        3 { $r = $p; $g = $q; $b = $brightness }
        4 { $r = $t; $g = $p; $b = $brightness }
        default { $r = $brightness; $g = $p; $b = $q }
    }
    return [System.Drawing.Color]::FromArgb(255,
        [int][Math]::Round($r * 255), [int][Math]::Round($g * 255), [int][Math]::Round($b * 255))
}

$gold = [System.Drawing.ColorTranslator]::FromHtml('#F2B33A')
$cyan = [System.Drawing.ColorTranslator]::FromHtml('#3FC9F0')

$sourceDir = Join-Path $PSScriptRoot '..\.ref\AE2-UEL\src\main\resources\assets\appliedenergistics2\textures\items'
$textureDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\ae2us\textures\items'
New-Item -ItemType Directory -Force -Path $textureDir | Out-Null

$jobs = @(
    @{ source = 'storage_cell_64k.png';       target = 'storage_cell_2048m.png';       hue = $gold.GetHue() },
    @{ source = 'fluid_storage_cell_64k.png'; target = 'fluid_storage_cell_2048m.png'; hue = $cyan.GetHue() }
)

$scale = 12
$gap = 12
$preview = [System.Drawing.Bitmap]::new((2 * 16 * $scale + $gap), (16 * $scale))
$graphics = [System.Drawing.Graphics]::FromImage($preview)
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#293033'))

$index = 0
foreach ($job in $jobs) {
    $source = [System.Drawing.Bitmap]::new((Join-Path $sourceDir $job.source))
    $tile = [System.Drawing.Bitmap]::new(16, 16)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $color = $source.GetPixel($x, $y)
            if ($color.A -eq 0 -or $color.GetSaturation() -le 0.15) {
                $tile.SetPixel($x, $y, $color)
            } else {
                $tile.SetPixel($x, $y, (Convert-HsbToColor $job.hue $color.GetSaturation() $color.GetBrightness()))
            }
        }
    }
    $tile.Save((Join-Path $textureDir $job.target), [System.Drawing.Imaging.ImageFormat]::Png)
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $brush = [System.Drawing.SolidBrush]::new($tile.GetPixel($x, $y))
            $graphics.FillRectangle($brush, ($index * (16 * $scale + $gap) + $x * $scale), ($y * $scale), $scale, $scale)
            $brush.Dispose()
        }
    }
    $tile.Dispose()
    $source.Dispose()
    $index++
}

$graphics.Dispose()
$preview.Save((Join-Path $PSScriptRoot 'storage-cell-textures.png'), [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "storage cell textures done"
