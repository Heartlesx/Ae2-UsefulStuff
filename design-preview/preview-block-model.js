// Offline isometric previewer for 1.12.2 block models (cuboid elements only).
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const { blank, px, set, encode, decode } = require('./png-tool');

const ASSETS = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets');
const JAR = path.join(__dirname, '..', 'libs', 'ae2-uel-v0.56.7.jar');
const CACHE = path.join(__dirname, '.cache');

function readJson(p) { return JSON.parse(fs.readFileSync(p, 'utf8')); }

function resolveModel(ref) {
  const [ns, rest] = ref.includes(':') ? ref.split(':') : ['minecraft', ref];
  if (ns === 'ae2us') return readJson(path.join(ASSETS, 'ae2us/models', rest + '.json'));
  if (ns === 'appliedenergistics2') {
    const tmp = path.join(CACHE, 'ae2-' + rest.replace(/\//g, '_') + '.json');
    if (!fs.existsSync(tmp)) {
      fs.mkdirSync(CACHE, { recursive: true });
      fs.writeFileSync(tmp, execSync(`unzip -p "${JAR}" "assets/appliedenergistics2/models/${rest}.json"`, { maxBuffer: 1e7 }));
    }
    return readJson(tmp);
  }
  throw new Error('unknown model namespace ' + ns);
}

function loadModel(ref) {
  const merged = { textures: {}, elements: null };
  const chain = [];
  let cur = ref;
  while (cur && cur !== 'block/block' && cur !== 'builtin/generated') {
    const m = resolveModel(cur);
    chain.unshift(m);
    cur = m.parent;
  }
  for (const m of chain) {
    if (m.textures) Object.assign(merged.textures, m.textures);
    if (m.elements) merged.elements = m.elements;
  }
  merged.textures = resolveTextureRefs(merged.textures, new Set());
  return merged;
}

function resolveTextureRefs(textures, seen) {
  const out = {};
  for (const [k, v] of Object.entries(textures)) {
    let val = v;
    while (typeof val === 'string' && val.startsWith('#')) val = textures[val.slice(1)];
    out[k] = val;
  }
  return out;
}

const texCache = new Map();
function loadTexture(ref) {
  if (texCache.has(ref)) return texCache.get(ref);
  const [ns, rest] = ref.includes(':') ? ref.split(':') : ['minecraft', ref];
  let file = null;
  if (ns === 'ae2us') file = path.join(ASSETS, 'ae2us/textures', rest + '.png');
  if (file && fs.existsSync(file)) {
    const img = decode(file);
    texCache.set(ref, img);
    return img;
  }
  if (ns === 'appliedenergistics2') {
    const tmp = path.join(CACHE, 'ae2tex-' + rest.replace(/\//g, '_') + '.png');
    if (!fs.existsSync(tmp)) {
      fs.mkdirSync(CACHE, { recursive: true });
      fs.writeFileSync(tmp, execSync(`unzip -p "${JAR}" "assets/appliedenergistics2/textures/${rest}.png"`, { maxBuffer: 1e7 }));
    }
    const img = decode(tmp);
    texCache.set(ref, img);
    return img;
  }
  const missing = blank(16, 16);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) set(missing, x, y, ((x >> 2) + (y >> 2)) & 1 ? [255, 0, 255, 255] : [30, 30, 30, 255]);
  texCache.set(ref, missing);
  return missing;
}

// MC model space (y down, +z south) -> render space (y up), centred on the model's bounds
let CENTRE = [8, 8, 8];
const toWorld = (x, y, z) => [x - CENTRE[0], CENTRE[1] - y, z - CENTRE[2]];

const FACE_DEF = {
  north: { n: [0, 0, -1], o: (a) => [a.x0, a.y0, a.z0], u: [1, 0, 0], v: [0, 1, 0] },
  south: { n: [0, 0, 1], o: (a) => [a.x1, a.y0, a.z1], u: [-1, 0, 0], v: [0, 1, 0] },
  east:  { n: [1, 0, 0], o: (a) => [a.x1, a.y0, a.z1], u: [0, 0, -1], v: [0, 1, 0] },
  west:  { n: [-1, 0, 0], o: (a) => [a.x0, a.y0, a.z0], u: [0, 0, 1], v: [0, 1, 0] },
  up:    { n: [0, 1, 0], o: (a) => [a.x0, a.y0, a.z0], u: [1, 0, 0], v: [0, 0, 1] },
  down:  { n: [0, -1, 0], o: (a) => [a.x0, a.y1, a.z1], u: [1, 0, 0], v: [0, 0, -1] }
};
const SHADE = { up: 1.0, down: 0.5, north: 0.8, south: 0.8, east: 0.62, west: 0.62 };

function norm(v) { const l = Math.hypot(...v) || 1; return v.map(c => c / l); }
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];

function render(modelRef, opts = {}) {
  const size = opts.size || 384;
  const scale = opts.scale || 18;      // pixels per block unit
  const camDir = norm(opts.dir || [1, 1, -1]);
  const right = norm(cross(camDir, [0, 1, 0]));
  const up = cross(right, camDir);
  const model = loadModel(modelRef);
  const bb = model.elements.reduce((acc, e) => ({
    x0: Math.min(acc.x0, e.from[0]), y0: Math.min(acc.y0, e.from[1]), z0: Math.min(acc.z0, e.from[2]),
    x1: Math.max(acc.x1, e.to[0]), y1: Math.max(acc.y1, e.to[1]), z1: Math.max(acc.z1, e.to[2])
  }), { x0: 16, y0: 16, z0: 16, x1: 0, y1: 0, z1: 0 });
  CENTRE = [(bb.x0 + bb.x1) / 2, (bb.y0 + bb.y1) / 2, (bb.z0 + bb.z1) / 2];
  const img = blank(size, size);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) set(img, x, y, [24, 24, 28, 255]);
  const zbuf = new Float32Array(size * size).fill(Infinity);

  const project = (p) => {
    const w = toWorld(p[0], p[1], p[2]);
    return [size / 2 + dot(w, right) * scale, size / 2 - dot(w, up) * scale, dot(w, camDir)];
  };

  const tri = (a, b, c, uvA, uvB, uvC, tex, shade) => {
    const minX = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0])));
    const maxX = Math.min(size - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
    const minY = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1])));
    const maxY = Math.min(size - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
    const d = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
    if (Math.abs(d) < 1e-9) return;
    for (let y = minY; y <= maxY; y++) {
      for (let x = minX; x <= maxX; x++) {
        const px0 = x + 0.5 - a[0], py0 = y + 0.5 - a[1];
        const l1 = (px0 * (c[1] - a[1]) - (c[0] - a[0]) * py0) / d;
        const l2 = ((b[0] - a[0]) * py0 - px0 * (b[1] - a[1])) / d;
        const l0 = 1 - l1 - l2;
        if (l0 < -0.001 || l1 < -0.001 || l2 < -0.001) continue;
        const z = l0 * a[2] + l1 * b[2] + l2 * c[2];
        const idx = y * size + x;
        if (z >= zbuf[idx]) continue;
        const u = l0 * uvA[0] + l1 * uvB[0] + l2 * uvC[0];
        const v = l0 * uvA[1] + l1 * uvB[1] + l2 * uvC[1];
        const tx = Math.min(tex.width - 1, Math.max(0, Math.floor(u * tex.width)));
        const ty = Math.min(tex.height - 1, Math.max(0, Math.floor(v * tex.height)));
        const col = px(tex, tx, ty);
        if (col[3] < 8) continue;
        set(img, x, y, [col[0] * shade, col[1] * shade, col[2] * shade, 255]);
        zbuf[idx] = z;
      }
    }
  };

  for (const el of model.elements) {
    const a = { x0: el.from[0], y0: el.from[1], z0: el.from[2], x1: el.to[0], y1: el.to[1], z1: el.to[2] };
    for (const [dir, def] of Object.entries(FACE_DEF)) {
      const face = el.faces[dir];
      if (!face) continue;
      const ref = model.textures[face.texture.replace(/^#/, '')];
      if (!ref) continue;
      const tex = loadTexture(ref);
      const o = def.o(a);
      const axisSize = (axis) => axis === 0 ? a.x1 - a.x0 : axis === 1 ? a.y1 - a.y0 : a.z1 - a.z0;
      const u = def.u.map((c, i) => c * axisSize(i));
      const v = def.v.map((c, i) => c * axisSize(i));
      const p00 = o;
      const p10 = [o[0] + u[0], o[1] + u[1], o[2] + u[2]];
      const p01 = [o[0] + v[0], o[1] + v[1], o[2] + v[2]];
      const p11 = [o[0] + u[0] + v[0], o[1] + u[1] + v[1], o[2] + u[2] + v[2]];
      let uv = face.uv ? [face.uv[0] / 16, face.uv[1] / 16, face.uv[2] / 16, face.uv[3] / 16] : null;
      const [u0, v0, u1, v1] = uv || [0, 0, 1, 1];
      const A = project(p00), B = project(p10), C = project(p11), D = project(p01);
      const shade = SHADE[dir];
      tri(A, B, C, [u0, v0], [u1, v0], [u1, v1], tex, shade);
      tri(A, C, D, [u0, v0], [u1, v1], [u0, v1], tex, shade);
    }
  }
  return img;
}

module.exports = { render };

if (require.main === module) {
  const out = process.argv[2];
  const modelRef = process.argv[3];
  const dir = process.argv[4] ? process.argv[4].split(',').map(Number) : undefined;
  encode(out, render(modelRef, dir ? { dir } : {}));
  console.log('rendered', out);
}
