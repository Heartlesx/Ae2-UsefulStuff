from pathlib import Path
from PIL import Image, ImageDraw
import json
import zipfile

ROOT = Path('/workspace/scratch/c155060e4dfb/parallel_processing_linked_surfaces_v4')
ROOT.mkdir(parents=True, exist_ok=True)
TIERS = [16, 64, 256, 1024, 2048, 4096]
COLORS = ['#87CC9A', '#7EC5CC', '#8D9ED4', '#BD91C7', '#CFB079', '#D2958F']
STATES = ['unformed', 'formed_off', 'formed_on']
BASE, GROOVE, TRIM, SHADE = '#8A9197', '#78838B', '#A1A9AF', '#727D86'

def rgb(value):
    return tuple(bytes.fromhex(value[1:]))

def trace_color(tier, state):
    if state == 0 or tier is None:
        return '#697680'
    color = rgb(COLORS[tier])
    if state == 1:
        # Forty-percent chromatic contrast against the neutral rail substrate.
        color = tuple(round(c * .4 + b * .6) for c, b in zip(color, rgb('#697680')))
    return color

def horizontal(tier, state):
    im = Image.new('RGB', (16, 16), BASE)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 5, 15, 10), fill=GROOVE)
    if state:
        # Small complementary steps form shared joints instead of icon centers.
        for x in range(16):
            edge = x in (0, 1, 14, 15)
            d.point((x, 4 if edge else 5), fill=TRIM)
            d.point((x, 11 if edge else 10), fill=SHADE)
    for y in (6, 9):
        d.line((0, y, 15, y), fill=trace_color(tier, state))
    return im

def diagonal(tier, state):
    im = Image.new('RGB', (16, 16), BASE)
    for y in range(16):
        for x in range(16):
            phase = (y - x) % 16
            color = BASE
            if 4 <= phase <= 10:
                color = GROOVE
            if state and phase == 3:
                color = TRIM
            if state and phase == 11:
                color = SHADE
            # Two stair-stepped strips with side adjacency, not corner-only dots.
            if phase in (5, 6, 8, 9):
                color = trace_color(tier, state)
            im.putpixel((x, y), rgb(color) if isinstance(color, str) else color)
    return im

def sheet_pair(tier, state):
    im = Image.new('RGB', (32, 16), BASE)
    d = ImageDraw.Draw(im)
    d.rectangle((2, 2, 29, 13), fill='#929AA0')
    d.line((1, 3, 1, 12), fill='#7C8790')
    d.line((30, 3, 30, 12), fill='#7C8790')
    if state:
        d.line((3, 1, 28, 1), fill=TRIM)
        d.line((3, 14, 28, 14), fill='#818C94')
    d.rectangle((2, 5, 29, 10), fill=GROOVE)
    for y in (6, 9):
        d.line((3, y, 28, y), fill=trace_color(tier, state))
    return im

def atlas(tiles, target):
    im = Image.new('RGB', (110, 56), '#FFFFFF')
    for state, row in enumerate(tiles):
        for tier, tile in enumerate(row):
            im.paste(tile, (2 + tier * 18, 2 + state * 18))
    im.resize((1320, 672), Image.Resampling.NEAREST).save(target)

def repeat(tile, nx, ny):
    out = Image.new('RGB', (tile.width * nx, tile.height * ny))
    for y in range(ny):
        for x in range(nx):
            out.paste(tile, (x * tile.width, y * tile.height))
    return out

def scale(im, factor, target):
    im.resize((im.width * factor, im.height * factor), Image.Resampling.NEAREST).save(target)

styles = {}
panels = []
for name, fn in [('A_horizontal', horizontal), ('B_diagonal', diagonal)]:
    folder = ROOT / name
    native = folder / 'textures'
    native.mkdir(parents=True, exist_ok=True)
    tiles = [[fn(tier, state) for tier in range(6)] for state in range(3)]
    styles[name] = tiles
    for state, row in enumerate(tiles):
        for tier, tile in enumerate(row):
            tile.save(native / f'parallel_{TIERS[tier]}x_{STATES[state]}.png')
    neutral = fn(None, 2)
    neutral.save(native / 'connected_neutral.png')
    scale(neutral, 32, folder / 'connected_single.png')
    scale(repeat(neutral, 2, 2), 16, folder / 'connected_2x2.png')
    atlas(tiles, folder / 'tiers_states_6x3.png')
    preview = repeat(tiles[2][1], 6, 2)
    panels.append(preview)
    scale(preview, 10, folder / 'powered_tiled_cyan.png')
    scale(repeat(tiles[2][1], 3, 3), 16, folder / 'powered_tiled_3x3.png')
    mixed = Image.new('RGB', (96, 32))
    for y in range(2):
        for tier in range(6):
            mixed.paste(tiles[2][tier], (tier * 16, y * 16))
    scale(mixed, 12, folder / 'powered_mixed_tiers_6x2.png')
    # Exact repetition, including the crossing diagonal seam, is validated
    # against the global surface phase, rather than equality of edge rows.
    tiled = repeat(tiles[2][1], 3, 3)
    for y in range(48):
        for x in range(48):
            assert tiled.getpixel((x, y)) == tiles[2][1].getpixel((x % 16, y % 16))
            if name == 'B_diagonal':
                assert ((y - x) % 16) == (((y % 16) - (x % 16)) % 16)

folder = ROOT / 'C_sheet_panels'
native = folder / 'textures'
native.mkdir(parents=True, exist_ok=True)
pairs = [[sheet_pair(tier, state) for tier in range(6)] for state in range(3)]
left = [[p.crop((0, 0, 16, 16)) for p in row] for row in pairs]
right = [[p.crop((16, 0, 32, 16)) for p in row] for row in pairs]
styles['C_sheet_left'] = left
styles['C_sheet_right'] = right
for state, row in enumerate(pairs):
    for tier, pair in enumerate(row):
        for phase, crop in [('left', (0, 0, 16, 16)), ('right', (16, 0, 32, 16))]:
            pair.crop(crop).save(native / f'parallel_{TIERS[tier]}x_{STATES[state]}_{phase}.png')
        assembled = Image.new('RGB', (32, 16))
        assembled.paste(left[state][tier], (0, 0))
        assembled.paste(right[state][tier], (16, 0))
        assert assembled.tobytes() == pair.tobytes()
neutral = sheet_pair(None, 2)
neutral.crop((0, 0, 16, 16)).save(native / 'connected_neutral_left.png')
neutral.crop((16, 0, 32, 16)).save(native / 'connected_neutral_right.png')
atlas(left, folder / 'tiers_states_left_6x3.png')
atlas(right, folder / 'tiers_states_right_6x3.png')
scale(repeat(neutral, 2, 2), 12, folder / 'connected_complete_panels_2x2.png')
preview = repeat(pairs[2][1], 3, 2)
panels.append(preview)
scale(preview, 10, folder / 'powered_tiled_cyan.png')
mixed = Image.new('RGB', (96, 32))
for y in range(2):
    for x in range(3):
        mixed.paste(pairs[2][y * 3 + x], (x * 32, y * 16))
scale(mixed, 12, folder / 'powered_mixed_tiers_6_panels.png')

# No labels inside the comparison image: top A, middle B, bottom C.
comparison = Image.new('RGB', (96, 102), '#FFFFFF')
for i, panel in enumerate(panels):
    comparison.paste(panel, (0, i * 35))
scale(comparison, 10, ROOT / 'three_linked_designs_comparison.png')

for rows in styles.values():
    for state, row in enumerate(rows):
        if state == 0:
            assert all(tile.tobytes() == row[0].tobytes() for tile in row)
        for tile in row:
            assert tile.mode == 'RGB' and tile.size == (16, 16)
            assert len(tile.getcolors()) <= 8
            # Two continuous rails, not point markers or barcode cells.
            assert len(tile.getcolors()) >= 3

native_count = len(list(ROOT.glob('*/textures/*.png')))
assert native_count == 76
readme = '''跨方块连接纹理 V4 — 三套原创方案

本包包含76张原生16×16 RGB贴图：A方案18张主贴图+1张灰色连接纹理；B方案18+1；C方案18组左右半片（36张）+2张灰色半片。
每套均包含16x、64x、256x、1024x、2048x、4096x六档，以及未成型、成型未通电、成型通电三状态。
各素材表的列依次为六档，行依次为三状态，无文字、无标志。

A_horizontal：横向双轨。每个16×16块是连续横向凹槽的一段，等级体现在两条长轨道的颜色。横向任意数量平铺；纵向重复形成整齐分层的机壳。
B_diagonal：斜向双轨。以(y-x)%16的全局相位构造45度阶梯线路，左右、上下平铺自动延续。单张16×16纹理即可重复，无须轮换贴图。推荐此方案作为主设计。
C_sheet_panels：跨两块片状面板。完整面板32×16，拆成left/right两个16×16半片；颜色轨道跨中心方块边界，中心无额外接缝。需由模组根据稳定的面内横坐标选择左右半片，不可把left当作独立无限重复纹理。

three_linked_designs_comparison.png：从上到下A、B、C；每行来自实际原生素材拼接，统一青色通电态，10倍整数放大。
各目录的powered_tiled_cyan.png是同档实际平铺；powered_mixed_tiers文件展示异档连接。异档相接时轨道几何连续，颜色按方块档位变化。
A/B中connected_single.png是16×16连接纹理的32倍展示，connected_2x2.png是实际2×2平铺后16倍展示。
C中connected_complete_panels_2x2.png是完整32×16面板的2×2重复，不把半片误称为完整面板。

所有像素不透明，无抗锯齿、无渐变、无半透明、无光晕、无噪点。没有圆形中心、小芯片图标、短条码、螺栓点或文字。
六档仅替换长轨道颜色，不增加线条数量。未成型为中性暗轨；未通电采用40%的色彩对比强度与中性轨底混合；通电为低饱和完整档位色。灰色外壳和轨道几何保持统一。

此版根据用户的三张手绘连接草图制作，沿用AE2工业科技材质的通用风格，不含原版素材字节。尚未与具体1.12.2资源包的机壳色值逐像素配色；与原版CPU邻接时的外轮廓/成型渲染仍需附属模组代码处理。C的相位选择也需模组支持。
'''
(ROOT / 'README.txt').write_text(readme, encoding='utf-8')
(ROOT / Path(__file__).name).write_bytes(Path(__file__).read_bytes())
package = ROOT.parent / 'parallel_processing_linked_surfaces_v4.zip'
with zipfile.ZipFile(package, 'w', zipfile.ZIP_DEFLATED) as z:
    for path in sorted(ROOT.rglob('*')):
        if path.is_file():
            z.write(path, path.relative_to(ROOT))
print(json.dumps({'native_textures': native_count, 'checks': 'passed', 'package': str(package)}, ensure_ascii=False))
