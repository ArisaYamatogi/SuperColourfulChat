const fs = require('fs');
const file = process.argv[2] || 'capture.json';
const capture = JSON.parse(fs.readFileSync(file, 'utf8'));

function extractText(n, out) {
  if (!n || typeof n !== 'object') return;
  if (Array.isArray(n)) { n.forEach(x => extractText(x, out)); return; }
  if (n.type === 'string') {
    if (Array.isArray(n.value)) n.value.forEach(s => out.push(s));
    return;
  }
  for (const [k, v] of Object.entries(n)) {
    if ((k === 'text' || k === '') && v && v.type === 'string' && typeof v.value === 'string') out.push(v.value);
    else extractText(v, out);
  }
}
function plain(entry) {
  const out = [];
  extractText(entry.data, out);
  return out.join('');
}
function hexes(blob) {
  return [...new Set(blob.match(/#[0-9a-fA-F]{6}/g) || [])];
}
function find(marker) {
  return capture.filter(e => (e.kind === 'player_chat' || e.kind === 'system_chat') && plain(e).includes(marker));
}

let fail = 0;
function assert(name, cond, detail) {
  console.log((cond ? 'PASS ' : 'FAIL ') + name + (detail ? '  [' + detail + ']' : ''));
  if (!cond) fail++;
}

const grad = find('hello gradient');
const gradBlob = grad.map(e => JSON.stringify(e.data)).join(' ');
const gradHex = hexes(gradBlob);
assert('gradient message captured', grad.length > 0, grad.length + ' entries, text=' + (grad[0] ? plain(grad[0]).slice(0, 60) : ''));
assert('gradient has >=2 distinct hex colors', gradHex.length >= 2, gradHex.length + ' colors: ' + gradHex.slice(0, 5).join(' '));

const manual = find('manual green');
const manualHex = hexes(manual.map(e => JSON.stringify(e.data)).join(' '));
assert('manual-code message captured', manual.length > 0, manual.length + ' entries');
assert('manual-code message has NO hex gradient', manualHex.length === 0, manualHex.join(' '));

const plainMsgs = find('plain after off');
const plainBlob = plainMsgs.map(e => JSON.stringify(e.data)).join(' ');
assert('after-off message captured', plainMsgs.length > 0, plainMsgs.length + ' entries');
assert('after-off message has NO hex', hexes(plainBlob).length === 0);

console.log(fail === 0 ? '=== ALL PASS ===' : '=== ' + fail + ' FAILURES ===');
process.exit(fail === 0 ? 0 : 1);