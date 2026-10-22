import { deflateSync } from 'node:zlib';

export const metadataMarker = 'sluice-synthetic-listing-private-note';
function crc32(bytes: Buffer) {
  let value = 0xffffffff;
  for (const byte of bytes) {
    value ^= byte;
    for (let bit = 0; bit < 8; bit++) value = (value >>> 1) ^ (value & 1 ? 0xedb88320 : 0);
  }
  return (value ^ 0xffffffff) >>> 0;
}
function chunk(type: string, data: Buffer) {
  const name = Buffer.from(type);
  const result = Buffer.alloc(data.length + 12);
  result.writeUInt32BE(data.length); name.copy(result, 4); data.copy(result, 8);
  result.writeUInt32BE(crc32(Buffer.concat([name, data])), data.length + 8);
  return result;
}
/** Original synthetic listing photograph substitute: shaded product on a textured backdrop. */
export function listingFixture(): Buffer {
  const width = 1400, height = 900;
  const pixels = Buffer.alloc((width * 3 + 1) * height);
  let seed = 19;
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    const noise = (seed >>> 28) - 8;
    const product = x > 420 && x < 980 && y > 150 && y < 750;
    const at = y * (width * 3 + 1) + 1 + x * 3;
    const color = product ? [40 + x / 60, 105 + y / 50, 190] : [227, 224, 215];
    for (let channel = 0; channel < 3; channel++) pixels[at + channel] = color[channel] + noise;
  }
  const header = Buffer.alloc(13); header.writeUInt32BE(width); header.writeUInt32BE(height, 4);
  header[8] = 8; header[9] = 2;
  // Level zero intentionally emulates a large unoptimized source, not a compression benchmark.
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header),
    chunk('tEXt', Buffer.from(`Comment\0${metadataMarker}`)),
    chunk('IDAT', deflateSync(pixels, { level: 0 })), chunk('IEND', Buffer.alloc(0))]);
}

export function inspectWebp(bytes: Buffer) {
  if (bytes.toString('ascii', 0, 4) !== 'RIFF' || bytes.toString('ascii', 8, 12) !== 'WEBP'
      || bytes.readUInt32LE(4) + 8 !== bytes.length) throw new Error('Output is not a complete WebP file');
  const chunks: string[] = [];
  for (let offset = 12; offset < bytes.length;) {
    if (offset + 8 > bytes.length) throw new Error('Truncated WebP chunk');
    const type = bytes.toString('ascii', offset, offset + 4), size = bytes.readUInt32LE(offset + 4);
    chunks.push(type); offset += 8 + size + (size % 2);
    if (offset > bytes.length) throw new Error('Truncated WebP payload');
  }
  if (!chunks.some(type => ['VP8 ', 'VP8L'].includes(type))) throw new Error('WebP has no image payload');
  return { chunks, metadataAbsent: !chunks.some(type => ['EXIF', 'XMP ', 'ICCP'].includes(type))
    && !bytes.includes(Buffer.from(metadataMarker)) };
}
