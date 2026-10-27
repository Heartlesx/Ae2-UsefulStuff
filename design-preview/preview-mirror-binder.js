// Builds a magnified view of the binder texture for eyeballing.
const path = require('path');
const { decode, encode, blank, set, px } = require('./png-tool');

const TEX = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'ae2us', 'textures', 'items', 'mirror_binder.png');
const img = decode(TEX);
const S = 12;
const out = blank(img.width * S, img.height * S);
for (let y = 0; y < out.height; y++) {
  for (let x = 0; x < out.width; x++) {
    set(out, x, y, px(img, Math.floor(x / S), Math.floor(y / S)));
  }
}
encode(path.join(__dirname, 'mirror-binder-texture.png'), out);
console.log('written');
