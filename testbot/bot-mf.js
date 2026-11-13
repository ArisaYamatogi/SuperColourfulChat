const mineflayer = require('mineflayer');
const fs = require('fs');

const HOST = process.env.SCC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.SCC_PORT || '25565', 10);
const VERSION = process.env.SCC_VERSION || '1.21.8';
const OUT = process.env.SCC_OUT || 'capture.json';

const capture = [];
const bot = mineflayer.createBot({
  host: HOST, port: PORT, username: 'TestBot', version: VERSION,
  auth: 'offline', hideErrors: false,
});

function rec(kind, data) {
  capture.push({ t: Date.now(), kind, data });
  console.log(`[${kind}]`, JSON.stringify(data).slice(0, 300));
}

bot.on('error', (e) => console.error('BOT ERROR', e.message));
bot.on('kicked', (r) => { console.error('KICKED', r); finish(1); });
bot.on('end', () => finish(0));

let done = false;
function finish(code) {
  if (done) return;
  done = true;
  fs.writeFileSync(OUT, JSON.stringify(capture, null, 2));
  const blob = JSON.stringify(capture);
  const hexes = [...new Set(blob.match(/#[0-9a-fA-F]{6}/g) || [])];
  console.log('=== SUMMARY ===');
  console.log('distinct hex colors:', hexes.length, hexes.slice(0, 12).join(' '));
  console.log('entries:', capture.length, '->', OUT);
  setTimeout(() => process.exit(code), 200);
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

bot.once('spawn', async () => {
  console.log('spawned');
  const c = bot._client;
  c.on('player_chat', (p) => rec('player_chat', p));
  c.on('system_chat', (p) => rec('system_chat', p));
  await sleep(2000);
  console.log('>>> /scc'); bot.chat('/scc');
  await sleep(1500);
  console.log('>>> hello gradient 12345'); bot.chat('hello gradient 12345');
  await sleep(2000);
  console.log('>>> &a manual green test'); bot.chat('&a manual green test');
  await sleep(2000);
  console.log('>>> /msg TestBot pm gradient check'); bot.chat('/msg TestBot pm gradient check');
  await sleep(2000);
  console.log('>>> /msg TestBot &a pm manual'); bot.chat('/msg TestBot &a pm manual');
  await sleep(2000);
  console.log('>>> /scc'); bot.chat('/scc');
  await sleep(1500);
  console.log('>>> plain after off'); bot.chat('plain after off');
  await sleep(2500);
  bot.quit('test done');
  finish(0);
});

setTimeout(() => { console.error('TIMEOUT 120s'); finish(2); }, 120000);