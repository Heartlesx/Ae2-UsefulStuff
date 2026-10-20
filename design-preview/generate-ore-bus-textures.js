// Generates AE2US ore-bus part textures (16x16) and model JSONs.
const fs = require('fs');
const path = require('path');
const { blank, set, encode } = require('./png-tool');

const ROOT = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'ae2us');
const TEX = path.join(ROOT, 'textures/part');
const MODEL = path.join(ROOT, 'models');

const BG = [78, 78, 90, 255];
const BG2 = [92, 92, 106, 255];
const BG3 = [66, 66, 77, 255];
const EDGE_L = [148, 148, 166, 255];
const EDGE_D = [34, 34, 41, 255];

// emblem palette per power state: [rim, body, highlight]
const STATE = {
  off: [[46, 46, 52, 255], [86, 86, 96, 255], [112, 112, 124, 255]],
  on: [[20, 84, 80, 255], [36, 140, 130, 255], [86, 196, 182, 255]],
  channel: [[26, 110, 104, 255], [52, 182, 168, 255], [150, 250, 232, 255]]
};

const fill = (img, c) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(img, x, y, c); };
const rect = (img, x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) set(img, x, y, c); };
const frame = (img, x0, y0, x1, y1, c) => {
  for (let x = x0; x <= x1; x++) { set(img, x, y0, c); set(img, x, y1, c); }
  for (let y = y0; y <= y1; y++) { set(img, x0, y, c); set(img, x1, y, c); }
};

// flat plate: dark rim, lit top edge, vertical grain
function panel(img) {
  fill(img, BG);
  frame(img, 0, 0, 15, 15, EDGE_D);
  for (let x = 1; x < 15; x++) set(img, x, 0, EDGE_L);
  for (let x = 1; x < 15; x++) set(img, x, 1, BG2);
  for (const c of [5, 10]) for (let y = 3; y < 14; y++) set(img, c, y, BG2);
  for (const c of [1, 5, 9, 13]) for (let y = 3; y < 14; y++) set(img, c, y, BG3);
}

// diamond: rows [x0, y, x1]
const SHAPE = [[7, 4, 8], [6, 5, 9], [5, 6, 10], [4, 7, 11], [4, 8, 11], [5, 9, 10], [6, 10, 9], [7, 11, 8]];

// solid crystal = export bus, hollow crystal with grille = storage bus
function emblem(img, storage, state) {
  const [rim, body, hi] = STATE[state];
  for (const [x0, y, x1] of SHAPE) for (let x = x0; x <= x1; x++) set(img, x, y, rim);
  const inset = (rows) => rows.map(([x0, y, x1]) => [x0 + 1, y, x1 - 1]).filter(([a, , b]) => a <= b);
  for (const [x0, y, x1] of inset(SHAPE)) for (let x = x0; x <= x1; x++) set(img, x, y, body);
  if (!storage) {
    for (const [x, y] of [[6, 6], [7, 5], [5, 7], [6, 7], [7, 6]]) set(img, x, y, hi);
    for (const [x, y] of [[8, 10], [9, 9], [9, 10], [8, 9]]) set(img, x, y, rim);
  } else {
    for (const [x0, y, x1] of inset(inset(SHAPE))) for (let x = x0; x <= x1; x++) set(img, x, y, BG3);
    for (const y of [6, 8, 10]) for (let x = 6; x <= 9; x++) set(img, x, y, rim);
    for (const [x, y] of [[6, 6], [7, 5], [5, 7]]) set(img, x, y, hi);
  }
}

function frontTex(storage, state) {
  const img = blank(16, 16); panel(img); emblem(img, storage, state); return img;
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
  if (state === 'off') { rect(img, 4, 4, 11, 11, [52, 52, 60, 255]); frame(img, 4, 4, 11, 11, [40, 40, 47, 255]); return img; }
  const [rim, body, hi] = STATE[state];
  rect(img, 4, 4, 11, 11, body);
  frame(img, 4, 4, 11, 11, rim);
  rect(img, 6, 6, 9, 9, state === 'channel' ? hi : rim);
  return img;
}

fs.mkdirSync(TEX, { recursive: true });
const textures = { 'ore_bus_sides': sidesTex(), 'ore_bus_back': backTex() };
for (const state of ['off', 'on', 'channel']) {
  textures[`ore_export_bus_front_${state}`] = frontTex(false, state);
  textures[`ore_storage_bus_front_${state}`] = frontTex(true, state);
  textures[`ore_bus_status_${state}`] = statusTex(state);
}
for (const [name, img] of Object.entries(textures)) encode(path.join(TEX, name + '.png'), img);

// ---- models ----
const ALL = ['down', 'up', 'north', 'south', 'east', 'west'];
const faces = (map) => {
  const out = {};
  for (const dir of ALL) if (map[dir] !== null) out[dir] = { texture: map[dir] };
  return out;
};

function partModel(steps, statusBox, frontTexture) {
  const side = { down: '#sides', up: '#sides', east: '#sides', west: '#sides', north: '#front', south: '#back' };
  const elements = steps.map(s => ({ from: s.from, to: s.to, faces: faces(side) }));
  elements.push({
    from: statusBox.from, to: statusBox.to,
    faces: faces({ down: '#status', up: '#status', east: '#status', west: '#status', north: null, south: null })
  });
  return {
    textures: {
      sides: 'ae2us:part/ore_bus_sides',
      back: 'ae2us:part/ore_bus_back',
      front: frontTexture,
      status: 'ae2us:part/ore_bus_status_off',
      particle: 'ae2us:part/ore_bus_back'
    },
    elements
  };
}

function writeJson(file, obj) { fs.writeFileSync(file, JSON.stringify(obj, null, 2) + '\n'); }

fs.mkdirSync(path.join(MODEL, 'part'), { recursive: true });
const exportSteps = [
  { from: [4, 4, 2], to: [12, 12, 4] },
  { from: [5, 5, 1], to: [11, 11, 2] },
  { from: [6, 6, 0], to: [10, 10, 1] }
];
const storageSteps = [
  { from: [3, 3, 0], to: [13, 13, 1] },
  { from: [2, 2, 1], to: [14, 14, 2] },
  { from: [5, 5, 3], to: [11, 11, 4] }
];

writeJson(path.join(MODEL, 'part/ore_export_bus_base.json'),
  partModel(exportSteps, { from: [6, 6, 4], to: [10, 10, 5] }, 'ae2us:part/ore_export_bus_front_off'));
writeJson(path.join(MODEL, 'part/ore_storage_bus_base.json'),
  partModel(storageSteps, { from: [5, 5, 2], to: [11, 11, 3] }, 'ae2us:part/ore_storage_bus_front_off'));

for (const state of ['off', 'on', 'channel']) {
  for (const [bus, front] of [['ore_export_bus', 'ore_export_bus'], ['ore_storage_bus', 'ore_storage_bus']]) {
    writeJson(path.join(MODEL, `part/${bus}_${state}.json`), {
      parent: `ae2us:part/${bus}_base`,
      textures: {
        front: `ae2us:part/${front}_front_${state}`,
        status: `ae2us:part/ore_bus_status_${state}`
      }
    });
  }
}

// ---- item models: same silhouette, centred, lit state ----
const display = {
  gui: { rotation: [30, 145, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
  ground: { translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
  fixed: { rotation: [0, 180, 0], scale: [0.8, 0.8, 0.8] },
  thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.5, 0.5, 0.5] },
  firstperson_righthand: { rotation: [0, 45, 0], scale: [0.5, 0.5, 0.5] }
};

function itemModel(frontTexture, steps) {
  const side = { down: '#sides', up: '#sides', east: '#sides', west: '#sides', north: '#front', south: '#sides' };
  return {
    display,
    textures: { sides: 'ae2us:part/ore_bus_sides', front: frontTexture },
    elements: steps.map(s => ({ from: s.from, to: s.to, faces: faces(side) }))
  };
}

writeJson(path.join(MODEL, 'item/ore_export_bus.json'), itemModel('ae2us:part/ore_export_bus_front_channel', [
  { from: [4, 4, 8], to: [12, 12, 10] },
  { from: [5, 5, 7], to: [11, 11, 8] },
  { from: [6, 6, 6], to: [10, 10, 7] }
]));
writeJson(path.join(MODEL, 'item/ore_storage_bus.json'), itemModel('ae2us:part/ore_storage_bus_front_channel', [
  { from: [3, 3, 6], to: [13, 13, 7] },
  { from: [2, 2, 7], to: [14, 14, 8] },
  { from: [5, 5, 9], to: [11, 11, 10] }
]));

console.log('textures:', Object.keys(textures).length, '| models written');
