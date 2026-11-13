const mc = require('minecraft-protocol');
const fs = require('fs');

const HOST = process.env.SCC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.SCC_PORT || '25565', 10);
const VERSION = process.env.SCC_VERSION || '1.21.8';
const OUT = process.env.SCC_OUT || 'capture.json';

const capture = [];
const client = mc.createClient({
  host: HOST, port: PORT, username: 'TestBot', version: VERSION,
  auth: 'offline', hideErrors: false,
});

function rec(kind, data) {
  const entry = { t: Date.now(), kind, data };
  capture.push(entry);
  console.log(`[${kind}]`, JSON.stringify(data).slice(0, 400));
}

client.on('player_chat', (p) => rec('player_chat', p));
client.on('system_chat', (p) => rec('system_chat', p));
client.on('disconnect', (p) => { rec('disconnect', p); finish(1); });
client.on('error', (e) => { console.error('CLIENT ERROR', e.message); });
client.on('end', () => finish(0));

function chat(text) {
  console.log('>>> sending:', text);
  client.chat(text);
}

function chatSafe(text) {
  try { chat(text); } catch (e) { console.error('chat send failed:', e.message); }
}

let done = false;
function finish(code) {
  if (done) return;
  done = true;
  fs.writeFileSync(OUT, JSON.stringify(capture, null, 2));
  // analysis: collect hex colors seen in chat payloads
  const blob = JSON.stringify(capture);
  const hexes = [...new Set((blob.match(/#[0-9a-fA-F]{6}/g) || []))];
  console.log('=== SUMMARY ===');
  console.log('distinct hex colors in capture:', hexes.length, hexes.slice(0, 12).join(' '));
  console.log('captured entries:', capture.length, '-> ', OUT);
  setTimeout(() => process.exit(code), 200);
}

client.on('login', async () => {
  console.log('login packet received; starting test sequence');
  client.write('settings', {
    locale: 'en_us', viewDistance: 4, chatFlags: 0, chatColors: true,
    skinParts: 127, mainHand: 1, enableTextFiltering: false, enableServerListing: true
  });
  const sleep = (ms) => new Promise(r => setTimeout(r, ms));
  await sleep(5000);
  chatSafe('/scc');                 // toggle ON
  await sleep(1500);
  chatSafe('hello gradient 12345'); // should be gradient
  await sleep(2000);
  chatSafe('&a manual green test'); // manual codes -> skip gradient
  await sleep(2000);
  chatSafe('/scc');                 // toggle OFF
  await sleep(1500);
  chatSafe('plain after off');      // should be plain
  await sleep(2500);
  client.end('test done');
  finish(0);
});

setTimeout(() => { console.error('TIMEOUT 90s'); finish(2); }, 90000);