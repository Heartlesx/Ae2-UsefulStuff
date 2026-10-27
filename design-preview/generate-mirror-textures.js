// Generates AE2US mirror pattern provider part textures (16x16).
// Same plate/geometry as the ore bus series, violet palette so the two stay distinguishable.
const fs = require('fs');
const path = require('path');
const { blank, set, encode } = require('./png-tool');

const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'ae2us');
const TEX = path.join(ROOT, 'textures/part');

const BG = [72, 68, 88, 255];
const BG2 = [86, 82, 104, 255];
const BG3 = [60, 57, 74, 255];
const EDGE_L = [148, 142, 170, 255];
const EDGE_D = [32, 29, 40, 255];

// emblem palette per power state: [rim, body, highlight]
const STATE = {
  off: [[46, 43, 55, 255], [88, 84, 102, 255], [114, 110, 130, 255]],
  on: [[64, 34, 96, 255], [124, 74, 176, 255], [176, 132, 226, 255]],
  channel: [[86, 42, 132, 255], [166, 104, 230, 255], [224, 190, 255, 255]]
};

const fill = (img, c) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(img, x, y, c); };
const rect = (img, x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(img, x, y, c); };
const frame = (img, x0, y0, x1, y1, c) => {
  for (let x = x0; x <= x1; x++) { set(img, x, y0, c); set(img, x, y1, c); }
  for (let y = y0; y <= y1; y++) { set(img, x0, y, c); set(img, x1, y, c); }
};

function panel(img) {
  fill(img, BG);
  frame(img, 0, 0, 15, 15, EDGE_D);
  for (let x = 1; x < 15; x++) set(img, x, 0, EDGE_L);
  for (let x = 1; x < 15; x++) set(img, x, 1, BG2);
  for (const c of [5, 10]) for (let y = 3; y < 14; y++) set(img, c, y, BG2);
  for (const c of [1, 5, 9, 13]) for (let y = 3; y < 14; y++) set(img, c, y, BG3);
}

// A crystal split down the middle by a dark seam: the "mirrored copy" cue.
// Same diamond silhouette as the ore bus series so the parts read as one family.
const SHAPE = [[7, 4, 8], [6, 5, 9], [5, 6, 10], [4, 7, 11], [4, 8, 11], [5, 9, 10], [6, 10, 9], [7, 11, 8]];

function emblem(img, state) {
  const [rim, body, hi] = STATE[state];
  for (const [x0, y, x1] of SHAPE) for (let x = x0; x <= x1; x++) set(img, x, y, rim);
  const inset = (rows) => rows.map(([x0, y, x1]) => [x0 + 1, y, x1 - 1]).filter(([a, , b]) => a <= b);
  for (const [x0, y, x1] of inset(SHAPE)) for (let x = x0; x <= x1; x++) set(img, x, y, body);
  // the seam that makes it two halves
  for (const [x0, y, x1] of inset(SHAPE)) if (x0 <= 7 && 7 <= x1) set(img, 7, y, BG3);
  // mirrored highlight, one per half
  for (const [x, y] of [[6, 6], [5, 7], [5, 8], [9, 6], [10, 7]]) set(img, x, y, hi);
}

function frontTex(state) {
  const img = blank(16, 16); panel(img); emblem(img, state); return img;
}

function sidesTex() {
  const img = blank(16, 16); panel(img);
  frame(img, 4, 2, 11, 13, EDGE_D);
  rect(img, 3, 1, 12, 14, BG);
  frame(img, 0, 0, 15, 15, EDGE_D);
  for (let x = 1; x < 15; x++) set(img, x, 0, EDGE_L);
  return img;
}

function backTex() {
  const img = blank(16, 16); panel(img);
  for (const [x, y, sx, sy] of [[4, 4, 1, 1], [11, 4, -1, 1], [4, 11, 1, -1], [11, 11, -1, -1]]) {
    for (let i = 0; i < 3; i++) { set(img, x + sx * i, y, EDGE_L); set(img, x, y + sy * i, EDGE_L); }
  }
  rect(img, 7, 7, 8, 8, EDGE_D);
  return img;
}

function statusTex(state) {
  const img = blank(16, 16);
  fill(img, BG3);
  frame(img, 0, 0, 15, 15, EDGE_D);
  if (state === 'off') { rect(img, 4, 4, 11, 11, [52, 48, 62, 255]); frame(img, 4, 4, 11, 11, [40, 37, 48, 255]); return img; }
  const [rim, body, hi] = STATE[state];
  rect(img, 4, 4, 11, 11, body);
  frame(img, 4, 4, 11, 11, rim);
  rect(img, 6, 6, 9, 9, state === 'channel' ? hi : rim);
  return img;
}

fs.mkdirSync(TEX, { recursive: true });
const textures = { 'mirror_provider_sides': sidesTex(), 'mirror_provider_back': backTex() };
for (const state of ['off', 'on', 'channel']) {
  textures[`mirror_provider_front_${state}`] = frontTex(state);
  textures[`mirror_provider_status_${state}`] = statusTex(state);
}
for (const [name, img] of Object.entries(textures)) encode(path.join(TEX, name + '.png'), img);

console.log('textures:', Object.keys(textures).length, '| written to', TEX);
