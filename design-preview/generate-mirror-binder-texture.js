// Generates the mirror binder item texture (16x16).
// A dark card with a split crystal stamped on it, matching the provider's emblem.
const fs = require('fs');
const path = require('path');
const { blank, set, encode } = require('./png-tool');

const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'ae2us');
const TEX = path.join(ROOT, 'textures/items');

const CARD = [58, 54, 72, 255];
const CARD_L = [86, 80, 106, 255];
const CARD_D = [30, 28, 38, 255];
const CARD_D2 = [44, 41, 55, 255];
const RIM = [86, 42, 132, 255];
const BODY = [166, 104, 230, 255];
const HI = [224, 190, 255, 255];

const fill = (img, c) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(img, x, y, c); };
const rect = (img, x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(img, x, y, c); };
const frame = (img, x0, y0, x1, y1, c) => {
  for (let x = x0; x <= x1; x++) { set(img, x, y0, c); set(img, x, y1, c); }
  for (let y = y0; y <= y1; y++) { set(img, x0, y, c); set(img, x1, y, c); }
};

const img = blank(16, 16);

// card body
rect(img, 2, 3, 13, 13, CARD);
frame(img, 2, 3, 13, 13, CARD_D);
// top-left light, bottom-right shadow
for (let x = 3; x <= 12; x++) set(img, x, 4, CARD_L);
for (let y = 4; y <= 12; y++) set(img, 3, y, CARD_L);
for (let x = 3; x <= 12; x++) set(img, x, 12, CARD_D2);
for (let y = 5; y <= 12; y++) set(img, 12, y, CARD_D2);
// contact strip along the bottom, like a card's gold fingers
rect(img, 4, 14, 11, 14, CARD_D);
for (const x of [5, 7, 9]) { set(img, x, 14, RIM); set(img, x + 1, 14, RIM); }

// split crystal stamped in the middle
const SHAPE = [[7, 5, 8], [6, 6, 9], [5, 7, 10], [5, 8, 10], [6, 9, 9], [7, 10, 8]];
for (const [x0, y, x1] of SHAPE) for (let x = x0; x <= x1; x++) set(img, x, y, RIM);
for (const [x0, y, x1] of SHAPE.map(([a, y, b]) => [a + 1, y, b - 1]).filter(([a, , b]) => a <= b)) {
  for (let x = x0; x <= x1; x++) set(img, x, y, BODY);
}
for (const [x0, y, x1] of SHAPE.map(([a, y, b]) => [a + 1, y, b - 1]).filter(([a, , b]) => a <= b)) {
  if (x0 <= 7 && 7 <= x1) set(img, 7, y, CARD_D);
}
for (const [x, y] of [[6, 7], [6, 8], [9, 7], [8, 6]]) set(img, x, y, HI);

fs.mkdirSync(TEX, { recursive: true });
encode(path.join(TEX, 'mirror_binder.png'), img);
console.log('written mirror_binder.png');
