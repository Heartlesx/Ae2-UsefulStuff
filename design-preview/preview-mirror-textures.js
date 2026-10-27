// Builds an 8x contact sheet of the mirror provider textures for eyeballing.
const path = require('path');
const { decode, encode, blank, set, px } = require('./png-tool');

const TEX = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'ae2us', 'textures', 'part');
const OUT = path.join(__dirname, 'mirror-provider-textures.png');
const names = [
  'mirror_provider_front_off', 'mirror_provider_front_on',
  'mirror_provider_front_channel', 'mirror_provider_sides',
  'mirror_provider_back', 'mirror_provider_status_off',
  'mirror_provider_status_on', 'mirror_provider_status_channel'
];

const S = 8, COLS = 4, PAD = 10;
const rows = Math.ceil(names.length / COLS);
const sheet = blank(COLS * (16 * S + PAD) + PAD, rows * (16 * S + PAD) + PAD);

for (let i = 0; i < names.length; i++) {
  const img = decode(path.join(TEX, names[i] + '.png'));
  const ox = PAD + (i % COLS) * (16 * S + PAD);
  const oy = PAD + Math.floor(i / COLS) * (16 * S + PAD);
  for (let y = 0; y < 16 * S; y++) {
    for (let x = 0; x < 16 * S; x++) {
      set(sheet, ox + x, oy + y, px(img, Math.floor(x / S), Math.floor(y / S)));
    }
  }
}
encode(OUT, sheet);
console.log('written', OUT, sheet.width + 'x' + sheet.height);
