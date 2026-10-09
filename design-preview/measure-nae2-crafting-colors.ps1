Add-Type -AssemblyName System.Drawing

# 一次性量色工具：统计 NAE2 合成存储方块贴图里的主要饱和色（灰件与透明像素不计），
# 用于给 AE2US 新方块避撞色。用法：powershell -File measure-nae2-crafting-colors.ps1

$dir = Join-Path $PSScriptRoot '..\其他仓库的源码\NAE2-master\src\main\resources\assets\nae2\textures\block\crafting'

Get-ChildItem $dir -Filter 'crafting_storage_*.png' | Sort-Object Name | ForEach-Object {
    $bmp = [System.Drawing.Bitmap]::new($_.FullName)
    $counts = @{}
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -gt 0 -and $c.GetSaturation() -gt 0.15) {
                $hex = '#{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
                if ($counts.ContainsKey($hex)) { $counts[$hex]++ } else { $counts[$hex] = 1 }
            }
        }
    }
    $top = $counts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 4
    $line = ($top | ForEach-Object { "$($_.Key)x$($_.Value)" }) -join '  '
    Write-Host ("{0,-38} {1}" -f $_.Name, $line)
    $bmp.Dispose()
}
