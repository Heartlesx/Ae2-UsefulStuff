Add-Type -AssemblyName System.Drawing

# 六档高级合成存储器的贴图：全部基于原版合成存储器贴图。
# - 未成型：storage_64k.png 逐像素换色（灰件饱和 <= 0.15 保留）；
# - 成型（暗/亮）：原版 light_base.png 底盘 + 换色后的亮层（storage_64k_light.png 第 0 帧，
#   帧 0 在原动画里停留 100 tick，就是常态观感）；未通电 = 亮层像素乘 0.65 压暗。
# 颜色 = 冻结版冷色渐变（避开 NAE2 的红橙黄绿）。

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

function Convert-ToTierColor([System.Drawing.Color]$color, [double]$hue, [double]$dim) {
    if ($color.A -eq 0) { return $color }
    if ($color.GetSaturation() -le 0.15) {
        if ($dim -eq 1.0) { return $color }
        return [System.Drawing.Color]::FromArgb(255,
            [int][Math]::Round($color.R * $dim), [int][Math]::Round($color.G * $dim), [int][Math]::Round($color.B * $dim))
    }
    return Convert-HsbToColor $hue $color.GetSaturation() ([Math]::Min(1.0, $color.GetBrightness() * $dim))
}

$sourceDir = Join-Path $PSScriptRoot '..\.ref\AE2-UEL\src\main\resources\assets\appliedenergistics2\textures\blocks\crafting'
$textureDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\ae2us\textures\blocks'
New-Item -ItemType Directory -Force -Path $textureDir | Out-Null

$unformedSource = [System.Drawing.Bitmap]::new((Join-Path $sourceDir 'storage_64k.png'))
$lightBase = [System.Drawing.Bitmap]::new((Join-Path $sourceDir 'light_base.png'))
$lightStrip = [System.Drawing.Bitmap]::new((Join-Path $sourceDir 'storage_64k_light.png'))

$tiers = @(
    @{ k = '256';   hue = ([System.Drawing.ColorTranslator]::FromHtml('#2FD0A5')).GetHue() },
    @{ k = '512';   hue = ([System.Drawing.ColorTranslator]::FromHtml('#3FC9F0')).GetHue() },
    @{ k = '1024';  hue = ([System.Drawing.ColorTranslator]::FromHtml('#4FA3E8')).GetHue() },
    @{ k = '4096';  hue = ([System.Drawing.ColorTranslator]::FromHtml('#5B7FE0')).GetHue() },
    @{ k = '8192';  hue = ([System.Drawing.ColorTranslator]::FromHtml('#8A6BE8')).GetHue() },
    @{ k = '16384'; hue = ([System.Drawing.ColorTranslator]::FromHtml('#C05CE8')).GetHue() }
)

$states = @('unformed', 'formed_off', 'formed_on')
$dim = @(1.0, 0.65, 1.0)

$scale = 12
$gap = 12
$preview = [System.Drawing.Bitmap]::new((6 * 16 * $scale + 5 * $gap), (3 * 16 * $scale + 2 * $gap))
$graphics = [System.Drawing.Graphics]::FromImage($preview)
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#293033'))

$partialAlpha = 0

for ($column = 0; $column -lt 6; $column++) {
    for ($row = 0; $row -lt 3; $row++) {
        $tile = [System.Drawing.Bitmap]::new(16, 16)
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                if ($row -eq 0) {
                    $tile.SetPixel($x, $y, (Convert-ToTierColor $unformedSource.GetPixel($x, $y) $tiers[$column].hue 1.0))
                } else {
                    $lightPixel = $lightStrip.GetPixel($x, $y)   # 第 0 帧 = 贴图最上面 16 行
                    if ($lightPixel.A -gt 0) {
                        if ($lightPixel.A -lt 255) { $script:partialAlpha++ }
                        $tile.SetPixel($x, $y, (Convert-ToTierColor $lightPixel $tiers[$column].hue $dim[$row]))
                    } else {
                        $tile.SetPixel($x, $y, $lightBase.GetPixel($x, $y))
                    }
                }
            }
        }
        $name = "crafting_storage_$($tiers[$column].k)k_$($states[$row])"
        $tile.Save((Join-Path $textureDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $brush = [System.Drawing.SolidBrush]::new($tile.GetPixel($x, $y))
                $graphics.FillRectangle($brush, ($column * (16 * $scale + $gap) + $x * $scale),
                    ($row * (16 * $scale + $gap) + $y * $scale), $scale, $scale)
                $brush.Dispose()
            }
        }
        $tile.Dispose()
    }
}

$graphics.Dispose()
$preview.Save((Join-Path $PSScriptRoot 'crafting-storage-textures.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$unformedSource.Dispose()
$lightBase.Dispose()
$lightStrip.Dispose()
Write-Host "crafting storage textures done, partial-alpha light pixels: $partialAlpha"
